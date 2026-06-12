from __future__ import annotations

import sqlite3
from datetime import datetime
from typing import Any

from modules.database import get_connection, init_db


def _now_text() -> str:
    return datetime.now().isoformat(timespec="seconds")


def _subject_payload(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if row is None:
        return None

    classroom_name = row["classroom"] if "classroom" in row.keys() else None
    payload = {
        "subject_id": row["id"],
        "subject_name": row["subject_name"],
        "professor_name": row["professor_name"],
        "classroom": classroom_name,
        "classroom_name": classroom_name,
        "classroom_id": row["classroom_id"] if "classroom_id" in row.keys() else None,
        "day_of_week": row["day_of_week"] if "day_of_week" in row.keys() else None,
        "start_time": row["start_time"] if "start_time" in row.keys() else None,
        "end_time": row["end_time"] if "end_time" in row.keys() else None,
        "is_active": bool(row["is_active"]) if "is_active" in row.keys() else True,
        "created_at": row["created_at"],
    }
    if "student_count" in row.keys():
        payload["student_count"] = int(row["student_count"] or 0)
    return payload


def _student_payload(row: sqlite3.Row) -> dict[str, Any]:
    return {
        "student_id": row["id"],
        "student_no": row["student_no"],
        "name": row["name"],
        "department": row["department"],
        "is_active": bool(row["is_active"]) if "is_active" in row.keys() else True,
        "created_at": row["created_at"],
    }


def _classroom_name_by_id(connection: sqlite3.Connection, classroom_id: int | None) -> str | None:
    if classroom_id is None:
        return None

    row = connection.execute(
        "SELECT classroom_name FROM classrooms WHERE id = ? LIMIT 1",
        (classroom_id,),
    ).fetchone()
    return row["classroom_name"] if row is not None else None


def _fetch_subject(connection: sqlite3.Connection, subject_id: int) -> sqlite3.Row | None:
    return connection.execute(
        """
        SELECT
            subjects.id,
            subjects.subject_name,
            subjects.professor_name,
            COALESCE(classrooms.classroom_name, subjects.classroom) AS classroom,
            subjects.classroom_id,
            subjects.day_of_week,
            subjects.start_time,
            subjects.end_time,
            subjects.is_active,
            subjects.created_at,
            COUNT(subject_students.id) AS student_count
        FROM subjects
        LEFT JOIN subject_students ON subject_students.subject_id = subjects.id
        LEFT JOIN classrooms ON classrooms.id = subjects.classroom_id
        WHERE subjects.id = ?
          AND COALESCE(subjects.is_active, 1) = 1
        GROUP BY subjects.id
        """,
        (subject_id,),
    ).fetchone()


def _student_exists(connection: sqlite3.Connection, student_id: int) -> bool:
    row = connection.execute(
        "SELECT 1 FROM students WHERE id = ? AND COALESCE(is_active, 1) = 1 LIMIT 1",
        (student_id,),
    ).fetchone()
    return row is not None


def create_subject(
    subject_name: str,
    professor_name: str | None = None,
    classroom: str | None = None,
    classroom_id: int | None = None,
    day_of_week: str | None = None,
    start_time: str | None = None,
    end_time: str | None = None,
) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        normalized_name = subject_name.strip()
        normalized_professor = professor_name.strip() if professor_name else None
        normalized_classroom = classroom.strip() if classroom else None
        normalized_day_of_week = day_of_week.strip() if day_of_week else None
        normalized_start_time = start_time.strip() if start_time else None
        normalized_end_time = end_time.strip() if end_time else None

        if not normalized_name:
            return {"success": False, "message": "과목명을 입력해 주세요.", "subject": None}

        with get_connection() as connection:
            classroom_name = _classroom_name_by_id(connection, classroom_id)
            if classroom_id is not None and classroom_name is None:
                return {"success": False, "message": "강의실을 찾을 수 없습니다.", "subject": None}
            if normalized_classroom is None:
                normalized_classroom = classroom_name

            cursor = connection.execute(
                """
                INSERT INTO subjects (
                    subject_name,
                    professor_name,
                    classroom,
                    classroom_id,
                    day_of_week,
                    start_time,
                    end_time,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    normalized_name,
                    normalized_professor,
                    normalized_classroom,
                    classroom_id,
                    normalized_day_of_week,
                    normalized_start_time,
                    normalized_end_time,
                    _now_text(),
                ),
            )
            subject_id = int(cursor.lastrowid)
            subject = _fetch_subject(connection, subject_id)
            connection.commit()

        return {
            "success": True,
            "message": "과목이 생성되었습니다.",
            "subject": _subject_payload(subject),
        }

    except sqlite3.Error as error:
        return {"success": False, "message": f"과목 생성 중 데이터베이스 오류가 발생했습니다: {error}", "subject": None}


def get_subjects() -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            rows = connection.execute(
                """
                SELECT
                    subjects.id,
                    subjects.subject_name,
                    subjects.professor_name,
                    COALESCE(classrooms.classroom_name, subjects.classroom) AS classroom,
                    subjects.classroom_id,
                    subjects.day_of_week,
                    subjects.start_time,
                    subjects.end_time,
                    subjects.is_active,
                    subjects.created_at,
                    COUNT(subject_students.id) AS student_count
                FROM subjects
                LEFT JOIN subject_students ON subject_students.subject_id = subjects.id
                LEFT JOIN classrooms ON classrooms.id = subjects.classroom_id
                WHERE COALESCE(subjects.is_active, 1) = 1
                GROUP BY subjects.id
                ORDER BY subjects.id DESC
                """
            ).fetchall()

        return {
            "success": True,
            "message": "과목 목록 조회 성공",
            "items": [_subject_payload(row) for row in rows],
        }

    except sqlite3.Error as error:
        return {"success": False, "message": f"과목 목록 조회 중 데이터베이스 오류가 발생했습니다: {error}", "items": []}


def get_subject_by_id(subject_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            subject = _fetch_subject(connection, subject_id)

        if subject is None:
            return {"success": False, "message": "과목을 찾을 수 없습니다.", "subject": None}

        return {
            "success": True,
            "message": "과목 상세 조회 성공",
            "subject": _subject_payload(subject),
        }

    except sqlite3.Error as error:
        return {"success": False, "message": f"과목 상세 조회 중 데이터베이스 오류가 발생했습니다: {error}", "subject": None}


def update_subject(
    subject_id: int,
    subject_name: str | None = None,
    professor_name: str | None = None,
    classroom: str | None = None,
    classroom_id: int | None = None,
    day_of_week: str | None = None,
    start_time: str | None = None,
    end_time: str | None = None,
) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            current = _fetch_subject(connection, subject_id)
            if current is None:
                return {"success": False, "message": "과목을 찾을 수 없습니다.", "subject": None}

            next_name = current["subject_name"] if subject_name is None else subject_name.strip()
            next_professor = current["professor_name"] if professor_name is None else (professor_name.strip() or None)
            next_classroom = current["classroom"] if classroom is None else (classroom.strip() or None)
            next_classroom_id = current["classroom_id"] if classroom_id is None else classroom_id
            next_day_of_week = current["day_of_week"] if day_of_week is None else (day_of_week.strip() or None)
            next_start_time = current["start_time"] if start_time is None else (start_time.strip() or None)
            next_end_time = current["end_time"] if end_time is None else (end_time.strip() or None)

            if not next_name:
                return {"success": False, "message": "과목명을 입력해 주세요.", "subject": None}

            classroom_name = _classroom_name_by_id(connection, next_classroom_id)
            if next_classroom_id is not None and classroom_name is None:
                return {"success": False, "message": "강의실을 찾을 수 없습니다.", "subject": None}
            if classroom is None and classroom_name is not None:
                next_classroom = classroom_name

            connection.execute(
                """
                UPDATE subjects
                SET
                    subject_name = ?,
                    professor_name = ?,
                    classroom = ?,
                    classroom_id = ?,
                    day_of_week = ?,
                    start_time = ?,
                    end_time = ?
                WHERE id = ?
                """,
                (
                    next_name,
                    next_professor,
                    next_classroom,
                    next_classroom_id,
                    next_day_of_week,
                    next_start_time,
                    next_end_time,
                    subject_id,
                ),
            )
            subject = _fetch_subject(connection, subject_id)
            connection.commit()

        return {
            "success": True,
            "message": "과목 정보가 수정되었습니다.",
            "subject": _subject_payload(subject),
        }

    except sqlite3.Error as error:
        return {"success": False, "message": f"과목 수정 중 데이터베이스 오류가 발생했습니다: {error}", "subject": None}


def delete_subject(subject_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            subject = _fetch_subject(connection, subject_id)
            if subject is None:
                return {"success": False, "message": "과목을 찾을 수 없습니다.", "deleted": False}

            attendance_count = connection.execute(
                """
                SELECT COUNT(*) AS count
                FROM attendance
                JOIN attendance_sessions ON attendance_sessions.id = attendance.session_id
                WHERE attendance_sessions.subject_id = ?
                """,
                (subject_id,),
            ).fetchone()["count"]

            if int(attendance_count or 0) > 0:
                connection.execute("UPDATE subjects SET is_active = 0 WHERE id = ?", (subject_id,))
                connection.execute(
                    "UPDATE attendance_sessions SET is_active = 0 WHERE subject_id = ?",
                    (subject_id,),
                )
                connection.commit()
                return {"success": True, "message": "과목이 비활성화되었습니다.", "deleted": False}

            connection.execute("DELETE FROM subject_students WHERE subject_id = ?", (subject_id,))
            connection.execute("UPDATE attendance_sessions SET subject_id = NULL WHERE subject_id = ?", (subject_id,))
            connection.execute("DELETE FROM subjects WHERE id = ?", (subject_id,))
            connection.commit()

        return {"success": True, "message": "과목이 삭제되었습니다.", "deleted": True}

    except sqlite3.Error as error:
        return {"success": False, "message": f"과목 삭제 중 데이터베이스 오류가 발생했습니다: {error}", "deleted": False}


def add_student_to_subject(subject_id: int, student_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            subject = _fetch_subject(connection, subject_id)
            if subject is None:
                return {"success": False, "message": "과목을 찾을 수 없습니다.", "enrollment": None}
            if not _student_exists(connection, student_id):
                return {"success": False, "message": "학생을 찾을 수 없습니다.", "enrollment": None}

            existing = connection.execute(
                """
                SELECT id FROM subject_students
                WHERE subject_id = ? AND student_id = ?
                LIMIT 1
                """,
                (subject_id, student_id),
            ).fetchone()
            if existing is not None:
                return {
                    "success": False,
                    "message": "이미 과목에 등록된 학생입니다.",
                    "enrollment": {
                        "subject_id": subject_id,
                        "student_id": student_id,
                    },
                }

            cursor = connection.execute(
                """
                INSERT INTO subject_students (subject_id, student_id, created_at)
                VALUES (?, ?, ?)
                """,
                (subject_id, student_id, _now_text()),
            )
            connection.commit()

        return {
            "success": True,
            "message": "수강생이 추가되었습니다.",
            "enrollment": {
                "id": int(cursor.lastrowid),
                "subject_id": subject_id,
                "student_id": student_id,
            },
        }

    except sqlite3.IntegrityError:
        return {"success": False, "message": "이미 과목에 등록된 학생입니다.", "enrollment": None}
    except sqlite3.Error as error:
        return {"success": False, "message": f"수강생 추가 중 데이터베이스 오류가 발생했습니다: {error}", "enrollment": None}


def remove_student_from_subject(subject_id: int, student_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            result = connection.execute(
                "DELETE FROM subject_students WHERE subject_id = ? AND student_id = ?",
                (subject_id, student_id),
            )
            connection.commit()

        if result.rowcount <= 0:
            return {"success": False, "message": "등록된 수강생 정보를 찾을 수 없습니다.", "deleted": False}

        return {"success": True, "message": "수강생이 제거되었습니다.", "deleted": True}

    except sqlite3.Error as error:
        return {"success": False, "message": f"수강생 제거 중 데이터베이스 오류가 발생했습니다: {error}", "deleted": False}


def get_subject_students(subject_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            subject = _fetch_subject(connection, subject_id)
            if subject is None:
                return {"success": False, "message": "과목을 찾을 수 없습니다.", "subject": None, "items": []}

            rows = connection.execute(
                """
                SELECT students.id, students.student_no, students.name, students.department, students.is_active, students.created_at
                FROM subject_students
                JOIN students ON students.id = subject_students.student_id
                WHERE subject_students.subject_id = ?
                  AND COALESCE(students.is_active, 1) = 1
                ORDER BY students.student_no ASC, students.id ASC
                """,
                (subject_id,),
            ).fetchall()

        return {
            "success": True,
            "message": "수강생 목록 조회 성공",
            "subject": _subject_payload(subject),
            "items": [_student_payload(row) for row in rows],
        }

    except sqlite3.Error as error:
        return {"success": False, "message": f"수강생 목록 조회 중 데이터베이스 오류가 발생했습니다: {error}", "subject": None, "items": []}


def get_student_subjects(student_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            if not _student_exists(connection, student_id):
                return {"success": False, "message": "학생을 찾을 수 없습니다.", "student_id": student_id, "items": []}

            rows = connection.execute(
                """
                SELECT
                    subjects.id,
                    subjects.subject_name,
                    subjects.professor_name,
                    COALESCE(classrooms.classroom_name, subjects.classroom) AS classroom,
                    subjects.classroom_id,
                    subjects.day_of_week,
                    subjects.start_time,
                    subjects.end_time,
                    subjects.is_active,
                    subjects.created_at
                FROM subject_students
                JOIN subjects ON subjects.id = subject_students.subject_id
                LEFT JOIN classrooms ON classrooms.id = subjects.classroom_id
                WHERE subject_students.student_id = ?
                  AND COALESCE(subjects.is_active, 1) = 1
                ORDER BY subjects.id DESC
                """,
                (student_id,),
            ).fetchall()

        return {
            "success": True,
            "message": "학생 수강 과목 조회 성공",
            "student_id": student_id,
            "items": [_subject_payload(row) for row in rows],
        }

    except sqlite3.Error as error:
        return {"success": False, "message": f"학생 수강 과목 조회 중 데이터베이스 오류가 발생했습니다: {error}", "student_id": student_id, "items": []}
