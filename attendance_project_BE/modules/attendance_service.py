from __future__ import annotations

import argparse
import sqlite3
from datetime import datetime
from typing import Any

from modules.database import get_connection, init_db


ATTENDANCE_STATUS_PRESENT = "present"
DEFAULT_SUBJECT_NAME = "湲곕낯 ?섏뾽"


def _today() -> str:
    return datetime.now().date().isoformat()


def _current_time() -> str:
    return datetime.now().time().isoformat(timespec="seconds")


def _current_time_hhmm() -> str:
    return datetime.now().time().isoformat(timespec="minutes")


def _now_text() -> str:
    return datetime.now().isoformat(timespec="seconds")


def _session_payload(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if row is None:
        return None

    subject_name = row["subject_name"]
    return {
        "session_id": row["id"],
        "subject_name": subject_name,
        "session_name": subject_name,  # 湲곗〈 API ?명솚??蹂꾩묶
        "subject_id": row["subject_id"] if "subject_id" in row.keys() else None,
        "classroom_id": row["classroom_id"] if "classroom_id" in row.keys() else None,
        "classroom_name": row["classroom_name"] if "classroom_name" in row.keys() else None,
        "day_of_week": row["day_of_week"] if "day_of_week" in row.keys() else None,
        "class_date": row["class_date"],
        "start_time": row["start_time"],
        "end_time": row["end_time"],
        "is_active": bool(row["is_active"]),
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

        with get_connection() as connection:
            if activate:
                connection.execute(
                    """
                    UPDATE attendance_sessions
                    SET is_active = 0
                    WHERE is_active = 1
                    """
                )
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
                    classroom_id,
                    normalized_day_of_week,
                    normalized_subject,
                    normalized_date,
                    start_time,
                    end_time,
                    1 if activate else 0,
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
            "message": f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}",
            "session": None,
        }


def get_session_by_id(session_id: int) -> dict[str, Any] | None:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = _fetch_session(connection, session_id)

        return _session_payload(row)

    except sqlite3.Error as error:
        print(f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}")
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
        print(f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}")
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

        session = _session_payload(row)
        if session is None:
            return {
                "success": False,
                "status": "no_current_session",
                "message": "현재 선택한 강의실에서 진행 중인 수업이 없습니다.",
                "session": None,
            }

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
        raise RuntimeError(result.get("message", "?쒖꽦 異쒖꽍 ?몄뀡???앹꽦?섏? 紐삵뻽?듬땲??"))

    return result["session"]


def activate_session(session_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = _fetch_session(connection, session_id)
            if row is None:
                return {"success": False, "message": "異쒖꽍 ?몄뀡??李얠쓣 ???놁뒿?덈떎.", "session": None}

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
            "message": "異쒖꽍 ?몄뀡???쒖꽦?붾릺?덉뒿?덈떎.",
            "session": _session_payload(active_row),
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}",
            "session": None,
        }


def close_session(session_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = _fetch_session(connection, session_id)
            if row is None:
                return {"success": False, "message": "異쒖꽍 ?몄뀡??李얠쓣 ???놁뒿?덈떎.", "session": None}

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
            "message": "異쒖꽍 ?몄뀡??醫낅즺?섏뿀?듬땲??",
            "session": _session_payload(closed_row),
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}",
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
        print(f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}")
        return []


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
        print(f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}")
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
        print(f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}")
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
                    "message": "異쒖꽍 ?몄뀡??李얠쓣 ???놁뒿?덈떎.",
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
            "message": "異쒖꽍 ?꾨즺",
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
            "message": f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}",
            "attendance_id": None,
            "session_id": session_id,
        }
    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}",
            "attendance_id": None,
            "session_id": session_id,
        }
    except Exception as error:
        return {
            "success": False,
            "message": f"異쒖꽍 泥섎━ 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}",
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
        print(f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}")
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
        print(f"?곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}")
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
                "message": "異쒖꽍 ?몄뀡??李얠쓣 ???놁뒿?덈떎.",
                "session": None,
                "items": [],
            }

        return {
            "success": True,
            "message": "?몄뀡 異쒖꽍 紐⑸줉 議고쉶 ?깃났",
            "session": session,
            "items": get_attendance_by_session(session_id),
        }

    if date is not None and date.strip():
        return {
            "success": True,
            "message": "?좎쭨蹂?異쒖꽍 紐⑸줉 議고쉶 ?깃났",
            "session": None,
            "date": date.strip(),
            "items": get_attendance_by_date(date.strip()),
        }

    active_session = get_active_session()
    if active_session is None:
        return {
            "success": False,
            "message": "?쒖꽦 異쒖꽍 ?몄뀡???놁뒿?덈떎.",
            "session": None,
            "items": [],
        }

    return {
        "success": True,
        "message": "?쒖꽦 ?몄뀡 異쒖꽍 紐⑸줉 議고쉶 ?깃났",
        "session": active_session,
        "items": get_attendance_by_session(int(active_session["session_id"])),
    }


# 湲곗〈 肄붾뱶 ?명솚??蹂꾩묶
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
    """?숈깮 ?곸꽭 ?붾㈃?먯꽌 ?ъ슜??異쒖꽍/吏媛?寃곗꽍 ?듦퀎瑜?議고쉶?쒕떎."""
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
                    "message": "?숈깮??李얠쓣 ???놁뒿?덈떎.",
                    "attendance_count": 0,
                    "late_count": 0,
                    "absence_count": 0,
                }

            row = connection.execute(
                """
                SELECT
                    SUM(CASE WHEN status IN ('present', 'attended', '異쒖꽍') THEN 1 ELSE 0 END) AS attendance_count,
                    SUM(CASE WHEN status IN ('late', '吏媛?) THEN 1 ELSE 0 END) AS late_count,
                    SUM(CASE WHEN status IN ('absent', '寃곗꽍') THEN 1 ELSE 0 END) AS absence_count
                FROM attendance
                WHERE student_id = ?
                """,
                (student_id,),
            ).fetchone()

        return {
            "success": True,
            "message": "?숈깮 ?듦퀎 議고쉶 ?깃났",
            "attendance_count": int(row["attendance_count"] or 0),
            "late_count": int(row["late_count"] or 0),
            "absence_count": int(row["absence_count"] or 0),
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"?숈깮 ?듦퀎 議고쉶 以??곗씠?곕쿋?댁뒪 ?ㅻ쪟媛 諛쒖깮?덉뒿?덈떎: {error}",
            "attendance_count": 0,
            "late_count": 0,
            "absence_count": 0,
        }

