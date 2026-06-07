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

    payload = {
        "subject_id": row["id"],
        "subject_name": row["subject_name"],
        "professor_name": row["professor_name"],
        "classroom": row["classroom"],
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
        "created_at": row["created_at"],
    }


def _fetch_subject(connection: sqlite3.Connection, subject_id: int) -> sqlite3.Row | None:
    return connection.execute(
        """
        SELECT
            subjects.id,
            subjects.subject_name,
            subjects.professor_name,
            subjects.classroom,
            subjects.created_at,
            COUNT(subject_students.id) AS student_count
        FROM subjects
        LEFT JOIN subject_students ON subject_students.subject_id = subjects.id
        WHERE subjects.id = ?
        GROUP BY subjects.id
        """,
        (subject_id,),
    ).fetchone()


def _student_exists(connection: sqlite3.Connection, student_id: int) -> bool:
    row = connection.execute(
        "SELECT 1 FROM students WHERE id = ? LIMIT 1",
        (student_id,),
    ).fetchone()
    return row is not None


def create_subject(
    subject_name: str,
    professor_name: str | None = None,
    classroom: str | None = None,
) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        normalized_name = subject_name.strip()
        normalized_professor = professor_name.strip() if professor_name else None
        normalized_classroom = classroom.strip() if classroom else None

        if not normalized_name:
            return {"success": False, "message": "과목명을 입력해 주세요.", "subject": None}

        with get_connection() as connection:
            cursor = connection.execute(
                """
                INSERT INTO subjects (subject_name, professor_name, classroom, created_at)
                VALUES (?, ?, ?, ?)
                """,
                (normalized_name, normalized_professor, normalized_classroom, _now_text()),
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
                    subjects.classroom,
                    subjects.created_at,
                    COUNT(subject_students.id) AS student_count
                FROM subjects
                LEFT JOIN subject_students ON subject_students.subject_id = subjects.id
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

            if not next_name:
                return {"success": False, "message": "과목명을 입력해 주세요.", "subject": None}

            connection.execute(
                """
                UPDATE subjects
                SET subject_name = ?, professor_name = ?, classroom = ?
                WHERE id = ?
                """,
                (next_name, next_professor, next_classroom, subject_id),
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

            student_count = connection.execute(
                "SELECT COUNT(*) AS count FROM subject_students WHERE subject_id = ?",
                (subject_id,),
            ).fetchone()["count"]
            if int(student_count or 0) > 0:
                return {
                    "success": False,
                    "message": "수강생이 등록된 과목은 삭제할 수 없습니다.",
                    "deleted": False,
                }

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
                SELECT students.id, students.student_no, students.name, students.department, students.created_at
                FROM subject_students
                JOIN students ON students.id = subject_students.student_id
                WHERE subject_students.subject_id = ?
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
                SELECT subjects.id, subjects.subject_name, subjects.professor_name, subjects.classroom, subjects.created_at
                FROM subject_students
                JOIN subjects ON subjects.id = subject_students.subject_id
                WHERE subject_students.student_id = ?
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
