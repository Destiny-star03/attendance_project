from __future__ import annotations

import sqlite3
from datetime import datetime
from typing import Any

from modules.database import get_connection, init_db


def _now_text() -> str:
    return datetime.now().isoformat(timespec="seconds")


def _row_to_dict(row: sqlite3.Row | None) -> dict[str, Any] | None:
    return None if row is None else dict(row)


def _get_student_by_student_no(
    connection: sqlite3.Connection,
    student_no: str,
    *,
    active_only: bool,
) -> sqlite3.Row | None:
    if active_only:
        return connection.execute(
            """
            SELECT id, student_no, name, department, is_active, created_at
            FROM students
            WHERE student_no = ?
              AND COALESCE(is_active, 1) = 1
            LIMIT 1
            """,
            (student_no.strip(),),
        ).fetchone()

    return connection.execute(
        """
        SELECT id, student_no, name, department, is_active, created_at
        FROM students
        WHERE student_no = ?
        LIMIT 1
        """,
        (student_no.strip(),),
    ).fetchone()


def _reactivate_student(
    connection: sqlite3.Connection,
    student_id: int,
    *,
    name: str,
    department: str | None,
    face_encoding: bytes,
) -> dict[str, Any]:
    connection.execute(
        """
        UPDATE students
        SET name = ?,
            department = ?,
            face_encoding = ?,
            is_active = 1,
            created_at = ?
        WHERE id = ?
        """,
        (name, department, sqlite3.Binary(face_encoding), _now_text(), student_id),
    )
    return {
        "success": True,
        "message": "학생이 다시 등록되었습니다.",
        "student_id": student_id,
        "reactivated": True,
    }


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

        with get_connection() as connection:
            existing = _get_student_by_student_no(connection, student_no, active_only=False)
            if existing is not None:
                if int(existing["is_active"] or 0) == 0:
                    result = _reactivate_student(
                        connection,
                        int(existing["id"]),
                        name=name,
                        department=department,
                        face_encoding=face_encoding,
                    )
                    connection.commit()
                    return result
                return {"success": False, "message": "이미 등록된 학번입니다.", "student_id": None}

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
                (student_no, name, department, sqlite3.Binary(face_encoding), _now_text()),
            )
            connection.commit()

        return {
            "success": True,
            "message": "학생 등록이 완료되었습니다.",
            "student_id": cursor.lastrowid,
            "reactivated": False,
        }

    except sqlite3.IntegrityError:
        return {"success": False, "message": "이미 등록된 학번입니다.", "student_id": None}
    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"학생 등록 중 데이터베이스 오류가 발생했습니다: {error}",
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
            row = _get_student_by_student_no(connection, student_no, active_only=False)
            if row is None:
                return {"success": False, "message": "등록된 학번을 찾을 수 없습니다.", "student_id": None}

            connection.execute(
                """
                UPDATE students
                SET name = ?,
                    department = ?,
                    face_encoding = ?,
                    is_active = 1
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
            "message": f"학생 얼굴 정보 갱신 중 데이터베이스 오류가 발생했습니다: {error}",
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
        print(f"학생 조회 중 데이터베이스 오류가 발생했습니다: {error}")
        return None


def upsert_student_face(
    student_no: str,
    name: str,
    department: str | None,
    face_encoding: bytes,
) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            existing = _get_student_by_student_no(connection, student_no, active_only=False)

        if existing is None:
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
                "created": not bool(add_result.get("reactivated")),
                "updated": False,
                "reactivated": bool(add_result.get("reactivated")),
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
            "updated": int(existing["is_active"] or 0) == 1,
            "reactivated": int(existing["is_active"] or 0) == 0,
            "message": "학생이 다시 등록되었습니다."
            if int(existing["is_active"] or 0) == 0
            else update_result.get("message", "학생 얼굴 정보가 갱신되었습니다."),
            "student": student,
            "student_id": update_result["student_id"],
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "created": False,
            "updated": False,
            "message": f"학생 저장 중 데이터베이스 오류가 발생했습니다: {error}",
            "student": None,
        }


def get_student_by_student_no(student_no: str) -> dict[str, Any] | None:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = _get_student_by_student_no(connection, student_no, active_only=True)

        return _row_to_dict(row)

    except sqlite3.Error as error:
        print(f"학생 조회 중 데이터베이스 오류가 발생했습니다: {error}")
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
        print(f"학생 목록 조회 중 데이터베이스 오류가 발생했습니다: {error}")
        return []


def is_student_no_exists(student_no: str, *, active_only: bool = True) -> bool:
    try:
        init_db(verbose=False)

        with get_connection() as connection:
            row = _get_student_by_student_no(connection, student_no, active_only=active_only)

        return row is not None

    except sqlite3.Error as error:
        print(f"학생 학번 중복 확인 중 데이터베이스 오류가 발생했습니다: {error}")
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

            connection.execute("DELETE FROM subject_students WHERE student_id = ?", (student_id,))

            if int(attendance_count or 0) > 0:
                connection.execute("UPDATE students SET is_active = 0 WHERE id = ?", (student_id,))
                connection.commit()
                return {"success": True, "message": "학생이 비활성화되었습니다.", "deleted": False}

            connection.execute("DELETE FROM students WHERE id = ?", (student_id,))
            connection.commit()

        return {"success": True, "message": "학생이 삭제되었습니다.", "deleted": True}

    except sqlite3.Error as error:
        return {
            "success": False,
            "message": f"학생 삭제 중 데이터베이스 오류가 발생했습니다: {error}",
            "deleted": False,
        }
