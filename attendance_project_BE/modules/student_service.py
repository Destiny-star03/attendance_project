from __future__ import annotations

import sqlite3
from datetime import datetime
from typing import Any

from modules.database import get_connection, init_db


def _row_to_dict(row: sqlite3.Row | None) -> dict[str, Any] | None:
    return None if row is None else dict(row)


def add_student(
    student_no: str,
    name: str,
    department: str | None,
    face_encoding: bytes,
) -> dict[str, Any]:
    try:
        init_db(verbose=False)

        student_no = student_no.strip()
        name = name.strip()
        department = department.strip() if department else None

        if not student_no:
            return {"success": False, "message": "학번을 입력해 주세요.", "student_id": None}

        if not name:
            return {"success": False, "message": "이름을 입력해 주세요.", "student_id": None}

        if not face_encoding:
            return {"success": False, "message": "얼굴 임베딩이 없습니다.", "student_id": None}

        if is_student_no_exists(student_no):
            return {"success": False, "message": "이미 등록된 학번입니다.", "student_id": None}

        created_at = datetime.now().isoformat(timespec="seconds")

        with get_connection() as connection:
            cursor = connection.execute(
                """
                INSERT INTO students (
                    student_no,
                    name,
                    department,
                    face_encoding,
                    is_active,
                    created_at
                )
                VALUES (?, ?, ?, ?, 1, ?)
                """,
                (student_no, name, department, sqlite3.Binary(face_encoding), created_at),
            )
            connection.commit()

        return {
            "success": True,
            "message": "학생 등록이 완료되었습니다.",
            "student_id": cursor.lastrowid,
        }

    except sqlite3.IntegrityError:
        return {"success": False, "message": "이미 등록된 학번입니다.", "student_id": None}
    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"데이터베이스 오류가 발생했습니다: {error}",
            "student_id": None,
        }
    except Exception as error:
        return {
            "success": False,
            "message": f"학생 등록 중 오류가 발생했습니다: {error}",
            "student_id": None,
        }


def update_student_face_encoding(
    student_no: str,
    name: str,
    department: str | None,
    face_encoding: bytes,
) -> dict[str, Any]:
    """Console utility helper. API registration still rejects duplicate student numbers."""
    try:
        init_db(verbose=False)

        student_no = student_no.strip()
        name = name.strip()
        department = department.strip() if department else None

        if not student_no:
            return {"success": False, "message": "학번을 입력해 주세요.", "student_id": None}

        if not name:
            return {"success": False, "message": "이름을 입력해 주세요.", "student_id": None}

        if not face_encoding:
            return {"success": False, "message": "얼굴 임베딩이 없습니다.", "student_id": None}

        with get_connection() as connection:
            row = connection.execute(
                "SELECT id FROM students WHERE student_no = ?",
                (student_no,),
            ).fetchone()

            if row is None:
                return {"success": False, "message": "등록된 학번을 찾을 수 없습니다.", "student_id": None}

            connection.execute(
                """
                UPDATE students
                SET name = ?, department = ?, face_encoding = ?
                WHERE student_no = ?
                """,
                (name, department, sqlite3.Binary(face_encoding), student_no),
            )
            connection.commit()

        return {
            "success": True,
            "message": "학생 얼굴 정보가 갱신되었습니다.",
            "student_id": row["id"],
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"데이터베이스 오류가 발생했습니다: {error}",
            "student_id": None,
        }
    except Exception as error:
        return {
            "success": False,
            "message": f"학생 얼굴 정보 갱신 중 오류가 발생했습니다: {error}",
            "student_id": None,
        }


def get_student_by_id(student_id: int) -> dict[str, Any] | None:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = connection.execute(
                """
                SELECT id, student_no, name, department, is_active, created_at
                FROM students
                WHERE id = ?
                  AND COALESCE(is_active, 1) = 1
                """,
                (student_id,),
            ).fetchone()

        return _row_to_dict(row)

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return None


def upsert_student_face(
    student_no: str,
    name: str,
    department: str | None,
    face_encoding: bytes,
) -> dict[str, Any]:
    existing_student = get_student_by_student_no(student_no)

    if existing_student is None:
        add_result = add_student(
            student_no=student_no,
            name=name,
            department=department,
            face_encoding=face_encoding,
        )
        if not add_result.get("success"):
            return {
                "success": False,
                "created": False,
                "updated": False,
                "message": add_result.get("message"),
                "student": None,
            }

        student = get_student_by_id(int(add_result["student_id"]))
        return {
            "success": True,
            "created": True,
            "updated": False,
            "message": add_result.get("message", "학생 등록이 완료되었습니다."),
            "student": student,
            "student_id": add_result["student_id"],
        }

    update_result = update_student_face_encoding(
        student_no=student_no,
        name=name,
        department=department,
        face_encoding=face_encoding,
    )
    if not update_result.get("success"):
        return {
            "success": False,
            "created": False,
            "updated": False,
            "message": update_result.get("message"),
            "student": None,
        }

    student = get_student_by_id(int(update_result["student_id"]))
    return {
        "success": True,
        "created": False,
        "updated": True,
        "message": update_result.get("message", "학생 얼굴 정보가 갱신되었습니다."),
        "student": student,
        "student_id": update_result["student_id"],
    }


def get_student_by_student_no(student_no: str) -> dict[str, Any] | None:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = connection.execute(
                """
                SELECT id, student_no, name, department, is_active, created_at
                FROM students
                WHERE student_no = ?
                  AND COALESCE(is_active, 1) = 1
                """,
                (student_no.strip(),),
            ).fetchone()

        return _row_to_dict(row)

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return None


def get_all_students() -> list[dict[str, Any]]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            rows = connection.execute(
                """
                SELECT id, student_no, name, department, is_active, created_at
                FROM students
                WHERE COALESCE(is_active, 1) = 1
                ORDER BY id ASC
                """
            ).fetchall()

        return [dict(row) for row in rows]

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return []


def is_student_no_exists(student_no: str) -> bool:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = connection.execute(
                "SELECT 1 FROM students WHERE student_no = ? LIMIT 1",
                (student_no.strip(),),
            ).fetchone()

        return row is not None

    except sqlite3.Error as error:
        print(f"데이터베이스 오류가 발생했습니다: {error}")
        return False


def delete_student(student_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            student = connection.execute(
                """
                SELECT id
                FROM students
                WHERE id = ?
                  AND COALESCE(is_active, 1) = 1
                LIMIT 1
                """,
                (student_id,),
            ).fetchone()
            if student is None:
                return {"success": False, "message": "학생을 찾을 수 없습니다.", "deleted": False}

            attendance_count = connection.execute(
                "SELECT COUNT(*) AS count FROM attendance WHERE student_id = ?",
                (student_id,),
            ).fetchone()["count"]

            if int(attendance_count or 0) > 0:
                connection.execute("UPDATE students SET is_active = 0 WHERE id = ?", (student_id,))
                connection.execute("DELETE FROM subject_students WHERE student_id = ?", (student_id,))
                connection.commit()
                return {"success": True, "message": "학생이 비활성화되었습니다.", "deleted": False}

            connection.execute("DELETE FROM subject_students WHERE student_id = ?", (student_id,))
            connection.execute("DELETE FROM students WHERE id = ?", (student_id,))
            connection.commit()

        return {"success": True, "message": "학생이 삭제되었습니다.", "deleted": True}

    except sqlite3.Error as error:
        return {"success": False, "message": f"학생 삭제 중 데이터베이스 오류가 발생했습니다: {error}", "deleted": False}
