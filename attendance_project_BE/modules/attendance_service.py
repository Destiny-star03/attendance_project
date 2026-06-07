from __future__ import annotations

import argparse
import sqlite3
from datetime import datetime
from typing import Any

from modules.database import get_connection, init_db


ATTENDANCE_STATUS_PRESENT = "present"
DEFAULT_SUBJECT_NAME = "기본 수업"


def _today() -> str:
    return datetime.now().date().isoformat()


def _current_time() -> str:
    return datetime.now().time().isoformat(timespec="seconds")


def _now_text() -> str:
    return datetime.now().isoformat(timespec="seconds")


def _session_payload(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if row is None:
        return None

    subject_name = row["subject_name"]
    return {
        "session_id": row["id"],
        "subject_name": subject_name,
        "session_name": subject_name,  # 기존 API 호환용 별칭
        "subject_id": row["subject_id"] if "subject_id" in row.keys() else None,
        "class_date": row["class_date"],
        "start_time": row["start_time"],
        "end_time": row["end_time"],
        "is_active": bool(row["is_active"]),
        "created_at": row["created_at"],
    }


def _fetch_session(connection: sqlite3.Connection, session_id: int) -> sqlite3.Row | None:
    return connection.execute(
        """
        SELECT id, subject_id, subject_name, class_date, start_time, end_time, is_active, created_at
        FROM attendance_sessions
        WHERE id = ?
        """,
        (session_id,),
    ).fetchone()


def create_attendance_session(
    subject_name: str,
    class_date: str,
    start_time: str | None = None,
    end_time: str | None = None,
    subject_id: int | None = None,
) -> dict[str, Any]:
    try:
        init_db(verbose=False)

        normalized_subject = subject_name.strip()
        normalized_date = class_date.strip()
        if not normalized_subject:
            return {"success": False, "message": "과목명을 입력해 주세요.", "session": None}
        if not normalized_date:
            return {"success": False, "message": "수업 날짜를 입력해 주세요.", "session": None}

        with get_connection() as connection:
            # MVP 정책: 새 세션을 만들면 기존 활성 세션을 모두 비활성화하고 새 세션을 활성화한다.
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
                    subject_name,
                    class_date,
                    start_time,
                    end_time,
                    is_active,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, 1, ?)
                """,
                (subject_id, normalized_subject, normalized_date, start_time, end_time, _now_text()),
            )
            session_id = int(cursor.lastrowid)
            row = _fetch_session(connection, session_id)
            connection.commit()

        return {
            "success": True,
            "message": "출석 세션이 생성되었습니다.",
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
                SELECT id, subject_id, subject_name, class_date, start_time, end_time, is_active, created_at
                FROM attendance_sessions
                WHERE is_active = 1
                ORDER BY id DESC
                LIMIT 1
                """
            ).fetchone()

        return _session_payload(row)

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return None


def get_or_create_active_session() -> dict[str, Any]:
    active_session = get_active_session()
    if active_session is not None:
        return active_session

    result = create_attendance_session(DEFAULT_SUBJECT_NAME, _today())
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
                SELECT id, subject_id, subject_name, class_date, start_time, end_time, is_active, created_at
                FROM attendance_sessions
                ORDER BY class_date DESC, id DESC
                """
            ).fetchall()

        return [_session_payload(row) for row in rows if row is not None]

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
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
                    "message": "이미 출석함",
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
                "message": "이미 출석함",
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

    create_session_parser = subparsers.add_parser("create-session", help="Create a new active session.")
    create_session_parser.add_argument("subject_name", help="Subject name.")
    create_session_parser.add_argument("--class-date", default=_today(), help="Class date in YYYY-MM-DD format.")
    create_session_parser.add_argument("--start-time", default=None, help="Start time in HH:MM format.")
    create_session_parser.add_argument("--end-time", default=None, help="End time in HH:MM format.")

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
