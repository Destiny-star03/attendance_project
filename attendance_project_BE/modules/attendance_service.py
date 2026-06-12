from __future__ import annotations

import argparse
import sqlite3
from datetime import datetime
from typing import Any

from modules.database import get_connection, init_db


ATTENDANCE_STATUS_PRESENT = "present"
ATTENDANCE_STATUS_LATE = "late"
ATTENDANCE_STATUS_ABSENT = "absent"
ATTENDANCE_STATUS_PENDING = "pending"
MANUAL_ATTENDANCE_STATUSES = {
    ATTENDANCE_STATUS_PRESENT,
    ATTENDANCE_STATUS_LATE,
    ATTENDANCE_STATUS_ABSENT,
    ATTENDANCE_STATUS_PENDING,
}
DEFAULT_SUBJECT_NAME = "기본 수업"


def _today() -> str:
    return datetime.now().date().isoformat()


def _today_day_of_week() -> str:
    return ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"][datetime.now().weekday()]


def _current_time() -> str:
    return datetime.now().time().isoformat(timespec="seconds")


def _current_time_hhmm() -> str:
    return datetime.now().time().isoformat(timespec="minutes")


def _now_text() -> str:
    return datetime.now().isoformat(timespec="seconds")


def _row_is_current_by_time(row: sqlite3.Row) -> bool:
    class_date = row["class_date"] if "class_date" in row.keys() else None
    start_time = row["start_time"] if "start_time" in row.keys() else None
    end_time = row["end_time"] if "end_time" in row.keys() else None
    if not class_date or not start_time or not end_time:
        return False

    current_time = _current_time_hhmm()
    return (
        str(class_date) == _today()
        and str(start_time)[:5] <= current_time
        and str(end_time)[:5] >= current_time
    )


def _session_payload(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if row is None:
        return None

    subject_name = row["subject_name"]
    return {
        "session_id": row["id"],
        "subject_name": subject_name,
        "session_name": subject_name,  # 기존 API 호환용 별칭
        "subject_id": row["subject_id"] if "subject_id" in row.keys() else None,
        "classroom_id": row["classroom_id"] if "classroom_id" in row.keys() else None,
        "classroom_name": row["classroom_name"] if "classroom_name" in row.keys() else None,
        "day_of_week": row["day_of_week"] if "day_of_week" in row.keys() else None,
        "class_date": row["class_date"],
        "start_time": row["start_time"],
        "end_time": row["end_time"],
        "is_active": bool(row["is_active"]) or _row_is_current_by_time(row),
        "created_at": row["created_at"],
    }


def _fetch_session(connection: sqlite3.Connection, session_id: int) -> sqlite3.Row | None:
    return connection.execute(
        """
        SELECT
            attendance_sessions.id,
            attendance_sessions.subject_id,
            COALESCE(attendance_sessions.classroom_id, subjects.classroom_id) AS classroom_id,
            COALESCE(classrooms.classroom_name, subjects.classroom) AS classroom_name,
            attendance_sessions.day_of_week,
            attendance_sessions.subject_name,
            attendance_sessions.class_date,
            attendance_sessions.start_time,
            attendance_sessions.end_time,
            attendance_sessions.is_active,
            attendance_sessions.created_at
        FROM attendance_sessions
        LEFT JOIN subjects ON subjects.id = attendance_sessions.subject_id
        LEFT JOIN classrooms ON classrooms.id = COALESCE(attendance_sessions.classroom_id, subjects.classroom_id)
        WHERE attendance_sessions.id = ?
        """,
        (session_id,),
    ).fetchone()


def _fetch_subject_schedule_defaults(
    connection: sqlite3.Connection,
    subject_id: int | None,
) -> sqlite3.Row | None:
    if subject_id is None:
        return None

    return connection.execute(
        """
        SELECT
            classroom_id,
            day_of_week,
            start_time,
            end_time
        FROM subjects
        WHERE id = ?
          AND COALESCE(is_active, 1) = 1
        """,
        (subject_id,),
    ).fetchone()


def _fill_session_schedule_fields(
    connection: sqlite3.Connection,
    row: sqlite3.Row | None,
) -> sqlite3.Row | None:
    if row is None:
        return None

    connection.execute(
        """
        UPDATE attendance_sessions
        SET
            classroom_id = COALESCE(classroom_id, ?),
            day_of_week = COALESCE(day_of_week, ?),
            start_time = COALESCE(start_time, ?),
            end_time = COALESCE(end_time, ?)
        WHERE id = ?
        """,
        (
            row["classroom_id"],
            row["day_of_week"],
            row["start_time"],
            row["end_time"],
            row["id"],
        ),
    )
    return _fetch_session(connection, int(row["id"]))


def create_attendance_session(
    subject_name: str,
    class_date: str,
    start_time: str | None = None,
    end_time: str | None = None,
    subject_id: int | None = None,
    classroom_id: int | None = None,
    day_of_week: str | None = None,
    activate: bool = False,
) -> dict[str, Any]:
    try:
        init_db(verbose=False)

        normalized_subject = subject_name.strip()
        normalized_date = class_date.strip()
        if not normalized_subject:
            return {"success": False, "message": "과목명을 입력해 주세요.", "session": None}
        if not normalized_date:
            return {"success": False, "message": "수업 날짜를 입력해 주세요.", "session": None}
        normalized_day_of_week = day_of_week.strip() if day_of_week else None
        normalized_start_time = start_time.strip() if start_time else None
        normalized_end_time = end_time.strip() if end_time else None

        with get_connection() as connection:
            subject_defaults = _fetch_subject_schedule_defaults(connection, subject_id)
            resolved_classroom_id = classroom_id
            resolved_day_of_week = normalized_day_of_week
            resolved_start_time = normalized_start_time
            resolved_end_time = normalized_end_time

            if subject_defaults is not None:
                if resolved_classroom_id is None:
                    resolved_classroom_id = subject_defaults["classroom_id"]
                if resolved_day_of_week is None:
                    resolved_day_of_week = subject_defaults["day_of_week"]
                if resolved_start_time is None:
                    resolved_start_time = subject_defaults["start_time"]
                if resolved_end_time is None:
                    resolved_end_time = subject_defaults["end_time"]

            cursor = connection.execute(
                """
                INSERT INTO attendance_sessions (
                    subject_id,
                    classroom_id,
                    day_of_week,
                    subject_name,
                    class_date,
                    start_time,
                    end_time,
                    is_active,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    subject_id,
                    resolved_classroom_id,
                    resolved_day_of_week,
                    normalized_subject,
                    normalized_date,
                    resolved_start_time,
                    resolved_end_time,
                    0,
                    _now_text(),
                ),
            )
            session_id = int(cursor.lastrowid)
            row = _fetch_session(connection, session_id)
            connection.commit()

        return {
            "success": True,
            "message": "수업 세션이 생성되었습니다.",
            "session": _session_payload(row),
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"데이터베이스 오류가 발생했습니다: {error}",
            "session": None,
        }


def get_session_by_id(session_id: int) -> dict[str, Any] | None:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = _fetch_session(connection, session_id)

        return _session_payload(row)

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return None


def get_active_session() -> dict[str, Any] | None:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = connection.execute(
                """
                SELECT
                    attendance_sessions.id,
                    attendance_sessions.subject_id,
                    COALESCE(attendance_sessions.classroom_id, subjects.classroom_id) AS classroom_id,
                    COALESCE(classrooms.classroom_name, subjects.classroom) AS classroom_name,
                    COALESCE(attendance_sessions.day_of_week, subjects.day_of_week) AS day_of_week,
                    attendance_sessions.subject_name,
                    attendance_sessions.class_date,
                    COALESCE(attendance_sessions.start_time, subjects.start_time) AS start_time,
                    COALESCE(attendance_sessions.end_time, subjects.end_time) AS end_time,
                    attendance_sessions.is_active,
                    attendance_sessions.created_at
                FROM attendance_sessions
                LEFT JOIN subjects ON subjects.id = attendance_sessions.subject_id
                LEFT JOIN classrooms ON classrooms.id = COALESCE(attendance_sessions.classroom_id, subjects.classroom_id)
                WHERE attendance_sessions.is_active = 1
                ORDER BY attendance_sessions.id DESC
                LIMIT 1
                """
            ).fetchone()

        return _session_payload(row)

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return None


def get_current_session_by_classroom(
    classroom_id: int | None = None,
    classroom_name: str | None = None,
) -> dict[str, Any]:
    normalized_classroom_name = classroom_name.strip() if classroom_name else None
    if classroom_id is None and not normalized_classroom_name:
        return {
            "success": False,
            "status": "bad_request",
            "message": "classroom_id 또는 classroom_name 중 하나는 필수입니다.",
            "session": None,
        }

    try:
        init_db(verbose=False)
        today = _today()
        current_time = _current_time_hhmm()

        with get_connection() as connection:
            row = connection.execute(
                """
                SELECT
                    attendance_sessions.id,
                    attendance_sessions.subject_id,
                    COALESCE(attendance_sessions.classroom_id, subjects.classroom_id) AS classroom_id,
                    COALESCE(classrooms.classroom_name, subjects.classroom) AS classroom_name,
                    COALESCE(attendance_sessions.day_of_week, subjects.day_of_week) AS day_of_week,
                    attendance_sessions.subject_name,
                    attendance_sessions.class_date,
                    COALESCE(attendance_sessions.start_time, subjects.start_time) AS start_time,
                    COALESCE(attendance_sessions.end_time, subjects.end_time) AS end_time,
                    attendance_sessions.is_active,
                    attendance_sessions.created_at
                FROM attendance_sessions
                LEFT JOIN subjects ON subjects.id = attendance_sessions.subject_id
                LEFT JOIN classrooms ON classrooms.id = COALESCE(attendance_sessions.classroom_id, subjects.classroom_id)
                WHERE attendance_sessions.class_date = ?
                  AND (
                    attendance_sessions.subject_id IS NULL
                    OR COALESCE(subjects.is_active, 1) = 1
                  )
                  AND COALESCE(attendance_sessions.start_time, subjects.start_time) IS NOT NULL
                  AND COALESCE(attendance_sessions.end_time, subjects.end_time) IS NOT NULL
                  AND substr(COALESCE(attendance_sessions.start_time, subjects.start_time), 1, 5) <= ?
                  AND substr(COALESCE(attendance_sessions.end_time, subjects.end_time), 1, 5) >= ?
                  AND (
                    (? IS NOT NULL AND (
                        attendance_sessions.classroom_id = ?
                        OR subjects.classroom_id = ?
                        OR classrooms.id = ?
                    ))
                    OR (? IS NOT NULL AND (
                        subjects.classroom = ?
                        OR classrooms.classroom_name = ?
                    ))
                  )
                ORDER BY
                    substr(COALESCE(attendance_sessions.start_time, subjects.start_time), 1, 5) ASC,
                    attendance_sessions.created_at DESC,
                    attendance_sessions.id DESC
                LIMIT 1
                """,
                (
                    today,
                    current_time,
                    current_time,
                    classroom_id,
                    classroom_id,
                    classroom_id,
                    classroom_id,
                    normalized_classroom_name,
                    normalized_classroom_name,
                    normalized_classroom_name,
                ),
            ).fetchone()

            if row is None:
                subject_row = connection.execute(
                    """
                    SELECT
                        subjects.id,
                        subjects.subject_name,
                        subjects.classroom,
                        subjects.classroom_id,
                        classrooms.classroom_name,
                        subjects.day_of_week,
                        subjects.start_time,
                        subjects.end_time
                    FROM subjects
                    LEFT JOIN classrooms ON classrooms.id = subjects.classroom_id
                    WHERE subjects.start_time IS NOT NULL
                      AND COALESCE(subjects.is_active, 1) = 1
                      AND subjects.end_time IS NOT NULL
                      AND substr(subjects.start_time, 1, 5) <= ?
                      AND substr(subjects.end_time, 1, 5) >= ?
                      AND (
                        subjects.day_of_week IS NULL
                        OR subjects.day_of_week = ?
                      )
                      AND (
                        (? IS NOT NULL AND (
                            subjects.classroom_id = ?
                            OR classrooms.id = ?
                        ))
                        OR (? IS NOT NULL AND (
                            subjects.classroom = ?
                            OR classrooms.classroom_name = ?
                        ))
                      )
                    ORDER BY substr(subjects.start_time, 1, 5) ASC, subjects.id DESC
                    LIMIT 1
                    """,
                    (
                        current_time,
                        current_time,
                        _today_day_of_week(),
                        classroom_id,
                        classroom_id,
                        classroom_id,
                        normalized_classroom_name,
                        normalized_classroom_name,
                        normalized_classroom_name,
                    ),
                ).fetchone()

                if subject_row is not None:
                    existing_row = connection.execute(
                        """
                        SELECT
                            attendance_sessions.id,
                            attendance_sessions.subject_id,
                            COALESCE(attendance_sessions.classroom_id, subjects.classroom_id) AS classroom_id,
                            COALESCE(classrooms.classroom_name, subjects.classroom) AS classroom_name,
                            COALESCE(attendance_sessions.day_of_week, subjects.day_of_week) AS day_of_week,
                            attendance_sessions.subject_name,
                            attendance_sessions.class_date,
                            COALESCE(attendance_sessions.start_time, subjects.start_time) AS start_time,
                            COALESCE(attendance_sessions.end_time, subjects.end_time) AS end_time,
                            attendance_sessions.is_active,
                            attendance_sessions.created_at
                        FROM attendance_sessions
                        LEFT JOIN subjects ON subjects.id = attendance_sessions.subject_id
                        LEFT JOIN classrooms ON classrooms.id = COALESCE(attendance_sessions.classroom_id, subjects.classroom_id)
                        WHERE attendance_sessions.subject_id = ?
                          AND attendance_sessions.class_date = ?
                        ORDER BY attendance_sessions.id DESC
                        LIMIT 1
                        """,
                        (subject_row["id"], today),
                    ).fetchone()

                    if existing_row is not None:
                        row = _fill_session_schedule_fields(connection, existing_row)
                        connection.commit()
                    else:
                        cursor = connection.execute(
                            """
                            INSERT INTO attendance_sessions (
                                subject_id,
                                classroom_id,
                                day_of_week,
                                subject_name,
                                class_date,
                                start_time,
                                end_time,
                                is_active,
                                created_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
                            """,
                            (
                                subject_row["id"],
                                subject_row["classroom_id"],
                                subject_row["day_of_week"],
                                subject_row["subject_name"],
                                today,
                                subject_row["start_time"],
                                subject_row["end_time"],
                                _now_text(),
                            ),
                        )
                        row = _fetch_session(connection, int(cursor.lastrowid))
                        connection.commit()

            if row is not None:
                row = _fill_session_schedule_fields(connection, row)
                connection.commit()

        session = _session_payload(row)
        if session is None:
            return {
                "success": False,
                "status": "no_current_session",
                "message": "현재 선택한 강의실에서 진행 중인 수업이 없습니다.",
                "session": None,
            }
        session["is_active"] = True

        return {
            "success": True,
            "message": "현재 진행 중인 수업입니다.",
            "session": session,
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "status": "server_error",
            "message": f"현재 수업 조회 중 데이터베이스 오류가 발생했습니다: {error}",
            "session": None,
        }


def get_or_create_active_session() -> dict[str, Any]:
    active_session = get_active_session()
    if active_session is not None:
        return active_session

    result = create_attendance_session(DEFAULT_SUBJECT_NAME, _today(), activate=True)
    if not result.get("success") or result.get("session") is None:
        raise RuntimeError(result.get("message", "활성 출석 세션을 생성하지 못했습니다."))

    return result["session"]


def activate_session(session_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = _fetch_session(connection, session_id)
            if row is None:
                return {"success": False, "message": "출석 세션을 찾을 수 없습니다.", "session": None}

            connection.execute(
                """
                UPDATE attendance_sessions
                SET is_active = 0
                WHERE is_active = 1
                """
            )
            connection.execute(
                """
                UPDATE attendance_sessions
                SET is_active = 1
                WHERE id = ?
                """,
                (session_id,),
            )
            active_row = _fetch_session(connection, session_id)
            connection.commit()

        return {
            "success": True,
            "message": "출석 세션이 활성화되었습니다.",
            "session": _session_payload(active_row),
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"데이터베이스 오류가 발생했습니다: {error}",
            "session": None,
        }


def close_session(session_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = _fetch_session(connection, session_id)
            if row is None:
                return {"success": False, "message": "출석 세션을 찾을 수 없습니다.", "session": None}

            connection.execute(
                """
                UPDATE attendance_sessions
                SET is_active = 0
                WHERE id = ?
                """,
                (session_id,),
            )
            closed_row = _fetch_session(connection, session_id)
            connection.commit()

        return {
            "success": True,
            "message": "출석 세션이 종료되었습니다.",
            "session": _session_payload(closed_row),
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"데이터베이스 오류가 발생했습니다: {error}",
            "session": None,
        }


def list_sessions() -> list[dict[str, Any]]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            rows = connection.execute(
                """
                SELECT
                    attendance_sessions.id,
                    attendance_sessions.subject_id,
                    COALESCE(attendance_sessions.classroom_id, subjects.classroom_id) AS classroom_id,
                    COALESCE(classrooms.classroom_name, subjects.classroom) AS classroom_name,
                    attendance_sessions.day_of_week,
                    attendance_sessions.subject_name,
                    attendance_sessions.class_date,
                    attendance_sessions.start_time,
                    attendance_sessions.end_time,
                    attendance_sessions.is_active,
                    attendance_sessions.created_at
                FROM attendance_sessions
                LEFT JOIN subjects ON subjects.id = attendance_sessions.subject_id
                LEFT JOIN classrooms ON classrooms.id = COALESCE(attendance_sessions.classroom_id, subjects.classroom_id)
                ORDER BY attendance_sessions.class_date DESC, attendance_sessions.id DESC
                """
            ).fetchall()

        return [_session_payload(row) for row in rows if row is not None]

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return []


def list_sessions_by_subject(subject_id: int) -> list[dict[str, Any]]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            rows = connection.execute(
                """
                SELECT
                    attendance_sessions.id,
                    attendance_sessions.subject_id,
                    COALESCE(attendance_sessions.classroom_id, subjects.classroom_id) AS classroom_id,
                    COALESCE(classrooms.classroom_name, subjects.classroom) AS classroom_name,
                    COALESCE(attendance_sessions.day_of_week, subjects.day_of_week) AS day_of_week,
                    attendance_sessions.subject_name,
                    attendance_sessions.class_date,
                    COALESCE(attendance_sessions.start_time, subjects.start_time) AS start_time,
                    COALESCE(attendance_sessions.end_time, subjects.end_time) AS end_time,
                    attendance_sessions.is_active,
                    attendance_sessions.created_at
                FROM attendance_sessions
                LEFT JOIN subjects ON subjects.id = attendance_sessions.subject_id
                LEFT JOIN classrooms ON classrooms.id = COALESCE(attendance_sessions.classroom_id, subjects.classroom_id)
                WHERE attendance_sessions.subject_id = ?
                ORDER BY attendance_sessions.class_date DESC,
                         substr(COALESCE(attendance_sessions.start_time, subjects.start_time), 1, 5) ASC,
                         attendance_sessions.id DESC
                """,
                (subject_id,),
            ).fetchall()

        return [_session_payload(row) for row in rows if row is not None]

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return []


def delete_session(session_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = _fetch_session(connection, session_id)
            if row is None:
                return {
                    "success": False,
                    "status": "not_found",
                    "message": "출석 세션을 찾을 수 없습니다.",
                    "deleted": False,
                }

            attendance_count = connection.execute(
                "SELECT COUNT(*) AS count FROM attendance WHERE session_id = ?",
                (session_id,),
            ).fetchone()["count"]
            if int(attendance_count or 0) > 0:
                return {
                    "success": False,
                    "status": "has_attendance",
                    "message": "출석 기록이 있는 수업 세션은 삭제할 수 없습니다.",
                    "deleted": False,
                }

            connection.execute("DELETE FROM attendance_sessions WHERE id = ?", (session_id,))
            connection.commit()

        return {
            "success": True,
            "status": "deleted",
            "message": "수업 세션이 삭제되었습니다.",
            "deleted": True,
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "status": "server_error",
            "message": f"수업 세션 삭제 중 데이터베이스 오류가 발생했습니다: {error}",
            "deleted": False,
        }


def has_attended(student_id: int, session_id: int) -> bool:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = connection.execute(
                """
                SELECT id
                FROM attendance
                WHERE student_id = ? AND session_id = ?
                LIMIT 1
                """,
                (student_id, session_id),
            ).fetchone()

        return row is not None

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return False


def is_student_enrolled_in_subject(student_id: int, subject_id: int) -> bool:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = connection.execute(
                """
                SELECT id
                FROM subject_students
                WHERE student_id = ? AND subject_id = ?
                LIMIT 1
                """,
                (student_id, subject_id),
            ).fetchone()

        return row is not None

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return False


def mark_attendance(
    student_id: int,
    session_id: int | None = None,
    confidence: float | None = None,
    distance: float | None = None,
) -> dict[str, Any]:
    try:
        init_db(verbose=False)

        session = get_or_create_active_session() if session_id is None else None
        resolved_session_id = int(session["session_id"] if session is not None else session_id)
        attendance_date = _today()
        attendance_time = _current_time()

        with get_connection() as connection:
            session_row = _fetch_session(connection, resolved_session_id)
            if session_row is None:
                return {
                    "success": False,
                    "message": "출석 세션을 찾을 수 없습니다.",
                    "attendance_id": None,
                    "session_id": resolved_session_id,
                }

            existing = connection.execute(
                """
                SELECT id
                FROM attendance
                WHERE student_id = ? AND session_id = ?
                LIMIT 1
                """,
                (student_id, resolved_session_id),
            ).fetchone()

            if existing is not None:
                return {
                    "success": True,
                    "message": "이미 출석했습니다.",
                    "attendance_id": existing["id"],
                    "session": _session_payload(session_row),
                }

            cursor = connection.execute(
                """
                INSERT INTO attendance (
                    session_id,
                    student_id,
                    attendance_date,
                    attendance_time,
                    status,
                    confidence,
                    distance
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    resolved_session_id,
                    student_id,
                    attendance_date,
                    attendance_time,
                    ATTENDANCE_STATUS_PRESENT,
                    confidence,
                    distance,
                ),
            )
            connection.commit()

        return {
            "success": True,
            "message": "출석 완료",
            "attendance_id": cursor.lastrowid,
            "session": _session_payload(session_row),
        }

    except sqlite3.IntegrityError as error:
        if "UNIQUE" in str(error).upper():
            return {
                "success": True,
                "message": "이미 출석했습니다.",
                "attendance_id": None,
                "session_id": session_id,
            }

        return {
            "success": False,
            "message": f"데이터베이스 오류가 발생했습니다: {error}",
            "attendance_id": None,
            "session_id": session_id,
        }
    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"데이터베이스 오류가 발생했습니다: {error}",
            "attendance_id": None,
            "session_id": session_id,
        }
    except Exception as error:
        return {
            "success": False,
            "message": f"출석 처리 중 오류가 발생했습니다: {error}",
            "attendance_id": None,
            "session_id": session_id,
        }


def _attendance_rows_to_dicts(rows: list[sqlite3.Row]) -> list[dict[str, Any]]:
    return [dict(row) for row in rows]


def get_attendance_by_session(session_id: int) -> list[dict[str, Any]]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            rows = connection.execute(
                """
                SELECT
                    attendance.id,
                    attendance.session_id,
                    attendance.student_id,
                    students.student_no,
                    students.name,
                    students.department,
                    attendance.attendance_date,
                    attendance.attendance_time,
                    attendance.status,
                    attendance.confidence,
                    attendance.distance,
                    attendance_sessions.subject_name,
                    attendance_sessions.classroom_id,
                    attendance_sessions.day_of_week,
                    attendance_sessions.class_date,
                    attendance_sessions.start_time,
                    attendance_sessions.end_time
                FROM attendance
                JOIN students ON students.id = attendance.student_id
                JOIN attendance_sessions ON attendance_sessions.id = attendance.session_id
                WHERE attendance.session_id = ?
                ORDER BY attendance.attendance_time ASC, attendance.id ASC
                """,
                (session_id,),
            ).fetchall()

        return _attendance_rows_to_dicts(rows)

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return []


def get_today_attendance() -> list[dict[str, Any]]:
    return get_attendance_by_date(_today())


def get_attendance_by_date(date: str) -> list[dict[str, Any]]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            rows = connection.execute(
                """
                SELECT
                    attendance.id,
                    attendance.session_id,
                    attendance.student_id,
                    students.student_no,
                    students.name,
                    students.department,
                    attendance.attendance_date,
                    attendance.attendance_time,
                    attendance.status,
                    attendance.confidence,
                    attendance.distance,
                    attendance_sessions.subject_name,
                    attendance_sessions.classroom_id,
                    attendance_sessions.day_of_week,
                    attendance_sessions.class_date,
                    attendance_sessions.start_time,
                    attendance_sessions.end_time
                FROM attendance
                JOIN students ON students.id = attendance.student_id
                JOIN attendance_sessions ON attendance_sessions.id = attendance.session_id
                WHERE attendance.attendance_date = ?
                ORDER BY attendance.attendance_time ASC, attendance.id ASC
                """,
                (date.strip(),),
            ).fetchall()

        return _attendance_rows_to_dicts(rows)

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return []


def get_attendance_records(
    session_id: int | None = None,
    date: str | None = None,
) -> dict[str, Any]:
    if session_id is not None:
        session = get_session_by_id(session_id)
        if session is None:
            return {
                "success": False,
                "message": "출석 세션을 찾을 수 없습니다.",
                "session": None,
                "items": [],
            }

        return {
            "success": True,
            "message": "세션 출석 목록 조회 성공",
            "session": session,
            "items": get_attendance_by_session(session_id),
        }

    if date is not None and date.strip():
        return {
            "success": True,
            "message": "날짜별 출석 목록 조회 성공",
            "session": None,
            "date": date.strip(),
            "items": get_attendance_by_date(date.strip()),
        }

    active_session = get_active_session()
    if active_session is None:
        return {
            "success": False,
            "message": "활성 출석 세션이 없습니다.",
            "session": None,
            "items": [],
        }

    return {
        "success": True,
        "message": "활성 세션 출석 목록 조회 성공",
        "session": active_session,
        "items": get_attendance_by_session(int(active_session["session_id"])),
    }


def list_session_attendance_students(session_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            session_row = _fetch_session(connection, session_id)
            if session_row is None:
                return {
                    "success": False,
                    "status": "not_found",
                    "message": "출석 세션을 찾을 수 없습니다.",
                    "session": None,
                    "items": [],
                }

            subject_id = session_row["subject_id"]
            if subject_id is not None:
                rows = connection.execute(
                    """
                    SELECT
                        attendance.id,
                        ? AS session_id,
                        ? AS subject_name,
                        ? AS classroom_id,
                        ? AS day_of_week,
                        ? AS class_date,
                        ? AS start_time,
                        ? AS end_time,
                        students.id AS student_id,
                        students.student_no,
                        students.name,
                        students.department,
                        attendance.attendance_date,
                        attendance.attendance_time,
                        COALESCE(attendance.status, ?) AS status,
                        attendance.confidence,
                        attendance.distance
                    FROM subject_students
                    JOIN students ON students.id = subject_students.student_id
                    LEFT JOIN attendance
                      ON attendance.student_id = students.id
                     AND attendance.session_id = ?
                    WHERE subject_students.subject_id = ?
                      AND COALESCE(students.is_active, 1) = 1
                    ORDER BY students.student_no ASC, students.name ASC, students.id ASC
                    """,
                    (
                        session_id,
                        session_row["subject_name"],
                        session_row["classroom_id"],
                        session_row["day_of_week"],
                        session_row["class_date"],
                        session_row["start_time"],
                        session_row["end_time"],
                        ATTENDANCE_STATUS_PENDING,
                        session_id,
                        subject_id,
                    ),
                ).fetchall()
            else:
                rows = connection.execute(
                    """
                    SELECT
                        attendance.id,
                        ? AS session_id,
                        ? AS subject_name,
                        ? AS classroom_id,
                        ? AS day_of_week,
                        ? AS class_date,
                        ? AS start_time,
                        ? AS end_time,
                        students.id AS student_id,
                        students.student_no,
                        students.name,
                        students.department,
                        attendance.attendance_date,
                        attendance.attendance_time,
                        COALESCE(attendance.status, ?) AS status,
                        attendance.confidence,
                        attendance.distance
                    FROM students
                    LEFT JOIN attendance
                      ON attendance.student_id = students.id
                     AND attendance.session_id = ?
                    WHERE COALESCE(students.is_active, 1) = 1
                    ORDER BY students.student_no ASC, students.name ASC, students.id ASC
                    """,
                    (
                        session_id,
                        session_row["subject_name"],
                        session_row["classroom_id"],
                        session_row["day_of_week"],
                        session_row["class_date"],
                        session_row["start_time"],
                        session_row["end_time"],
                        ATTENDANCE_STATUS_PENDING,
                        session_id,
                    ),
                ).fetchall()

        return {
            "success": True,
            "message": "세션 출석 학생 목록 조회 성공",
            "session": _session_payload(session_row),
            "items": _attendance_rows_to_dicts(rows),
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "status": "server_error",
            "message": f"세션 출석 학생 목록 조회 중 데이터베이스 오류가 발생했습니다: {error}",
            "session": None,
            "items": [],
        }


def update_session_attendance_status(
    session_id: int,
    student_id: int,
    status: str,
) -> dict[str, Any]:
    normalized_status = status.strip()
    if normalized_status not in MANUAL_ATTENDANCE_STATUSES:
        return {
            "success": False,
            "status": "bad_request",
            "message": "출석 상태는 present, late, absent, pending 중 하나여야 합니다.",
        }

    try:
        init_db(verbose=False)

        with get_connection() as connection:
            session_row = _fetch_session(connection, session_id)
            if session_row is None:
                return {
                    "success": False,
                    "status": "not_found",
                    "message": "출석 세션을 찾을 수 없습니다.",
                }

            student_row = connection.execute(
                """
                SELECT id
                FROM students
                WHERE id = ?
                  AND COALESCE(is_active, 1) = 1
                """,
                (student_id,),
            ).fetchone()
            if student_row is None:
                return {
                    "success": False,
                    "status": "student_not_found",
                    "message": "학생을 찾을 수 없습니다.",
                }

            subject_id = session_row["subject_id"]
            if subject_id is not None:
                enrolled_row = connection.execute(
                    """
                    SELECT id
                    FROM subject_students
                    WHERE subject_id = ?
                      AND student_id = ?
                    LIMIT 1
                    """,
                    (subject_id, student_id),
                ).fetchone()
                if enrolled_row is None:
                    return {
                        "success": False,
                        "status": "not_enrolled",
                        "message": "해당 수업의 수강생이 아닙니다.",
                    }

            if normalized_status == ATTENDANCE_STATUS_PENDING:
                connection.execute(
                    "DELETE FROM attendance WHERE session_id = ? AND student_id = ?",
                    (session_id, student_id),
                )
                connection.commit()
                return {
                    "success": True,
                    "status": ATTENDANCE_STATUS_PENDING,
                    "message": "출석 상태가 미출석으로 변경되었습니다.",
                    "attendance": None,
                }

            existing = connection.execute(
                """
                SELECT id
                FROM attendance
                WHERE session_id = ?
                  AND student_id = ?
                LIMIT 1
                """,
                (session_id, student_id),
            ).fetchone()
            attendance_date = session_row["class_date"] or _today()
            attendance_time = _current_time()
            if existing is None:
                cursor = connection.execute(
                    """
                    INSERT INTO attendance (
                        session_id,
                        student_id,
                        attendance_date,
                        attendance_time,
                        status
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    (session_id, student_id, attendance_date, attendance_time, normalized_status),
                )
                attendance_id = int(cursor.lastrowid)
            else:
                attendance_id = int(existing["id"])
                connection.execute(
                    """
                    UPDATE attendance
                    SET
                        attendance_date = ?,
                        attendance_time = ?,
                        status = ?
                    WHERE id = ?
                    """,
                    (attendance_date, attendance_time, normalized_status, attendance_id),
                )
            connection.commit()

        return {
            "success": True,
            "status": normalized_status,
            "message": "출석 상태가 변경되었습니다.",
            "attendance_id": attendance_id,
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "status": "server_error",
            "message": f"출석 상태 변경 중 데이터베이스 오류가 발생했습니다: {error}",
        }


# 기존 코드 호환용 별칭
activate_attendance_session = activate_session
close_attendance_session = close_session
list_attendance_sessions = list_sessions


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Attendance service test commands.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    mark_parser = subparsers.add_parser("mark", help="Mark attendance for a student.")
    mark_parser.add_argument("student_id", type=int, help="Student ID.")
    mark_parser.add_argument("--session-id", type=int, default=None, help="Attendance session ID.")

    create_session_parser = subparsers.add_parser("create-session", help="Create a new attendance session.")
    create_session_parser.add_argument("subject_name", help="Subject name.")
    create_session_parser.add_argument("--class-date", default=_today(), help="Class date in YYYY-MM-DD format.")
    create_session_parser.add_argument("--start-time", default=None, help="Start time in HH:MM format.")
    create_session_parser.add_argument("--end-time", default=None, help="End time in HH:MM format.")
    create_session_parser.add_argument("--activate", action="store_true", help="Activate the session after creation.")

    activate_parser = subparsers.add_parser("activate-session", help="Activate a session.")
    activate_parser.add_argument("session_id", type=int, help="Session ID.")

    close_parser = subparsers.add_parser("close-session", help="Close a session.")
    close_parser.add_argument("session_id", type=int, help="Session ID.")

    subparsers.add_parser("active-session", help="Show the active attendance session.")
    subparsers.add_parser("sessions", help="Show all attendance sessions.")
    subparsers.add_parser("today", help="Show today's attendance records.")

    date_parser = subparsers.add_parser("date", help="Show attendance records by date.")
    date_parser.add_argument("date", help="Date in YYYY-MM-DD format.")

    session_parser = subparsers.add_parser("session", help="Show attendance records by session.")
    session_parser.add_argument("session_id", type=int, help="Session ID.")

    args = parser.parse_args()

    if args.command == "mark":
        print(mark_attendance(args.student_id, session_id=args.session_id))
    elif args.command == "create-session":
        print(
            create_attendance_session(
                args.subject_name,
                args.class_date,
                start_time=args.start_time,
                end_time=args.end_time,
                activate=args.activate,
            )
        )
    elif args.command == "activate-session":
        print(activate_session(args.session_id))
    elif args.command == "close-session":
        print(close_session(args.session_id))
    elif args.command == "active-session":
        print(get_active_session())
    elif args.command == "sessions":
        for session in list_sessions():
            print(session)
    elif args.command == "today":
        for record in get_today_attendance():
            print(record)
    elif args.command == "date":
        for record in get_attendance_by_date(args.date):
            print(record)
    elif args.command == "session":
        for record in get_attendance_by_session(args.session_id):
            print(record)

def get_student_attendance_stats(student_id: int) -> dict[str, Any]:
    """학생 상세 화면에서 사용할 출석/지각/결석 통계를 조회한다."""
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            student = connection.execute(
                "SELECT id FROM students WHERE id = ?",
                (student_id,),
            ).fetchone()
            if student is None:
                return {
                    "success": False,
                    "message": "학생을 찾을 수 없습니다.",
                    "attendance_count": 0,
                    "late_count": 0,
                    "absence_count": 0,
                }

            row = connection.execute(
                """
                SELECT
                    SUM(CASE WHEN status IN ('present', 'attended', '출석') THEN 1 ELSE 0 END) AS attendance_count,
                    SUM(CASE WHEN status IN ('late', '지각') THEN 1 ELSE 0 END) AS late_count,
                    SUM(CASE WHEN status IN ('absent', '결석') THEN 1 ELSE 0 END) AS absence_count
                FROM attendance
                WHERE student_id = ?
                """,
                (student_id,),
            ).fetchone()

        return {
            "success": True,
            "message": "학생 통계 조회 성공",
            "attendance_count": int(row["attendance_count"] or 0),
            "late_count": int(row["late_count"] or 0),
            "absence_count": int(row["absence_count"] or 0),
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"학생 통계 조회 중 데이터베이스 오류가 발생했습니다: {error}",
            "attendance_count": 0,
            "late_count": 0,
            "absence_count": 0,
        }

