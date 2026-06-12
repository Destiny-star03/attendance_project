from __future__ import annotations

import sqlite3
from datetime import datetime
from typing import Any

from modules.database import get_connection, init_db


def _now_text() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _normalize_text(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None


def _classroom_payload(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if row is None:
        return None

    return {
        "classroom_id": row["id"],
        "classroom_name": row["classroom_name"],
        "building_name": row["building_name"],
        "floor": row["floor"],
        "description": row["description"],
        "is_active": bool(row["is_active"]),
        "created_at": row["created_at"],
    }


def _fetch_classroom(connection: sqlite3.Connection, classroom_id: int) -> sqlite3.Row | None:
    return connection.execute(
        """
        SELECT id, classroom_name, building_name, floor, description, is_active, created_at
        FROM classrooms
        WHERE id = ?
        """,
        (classroom_id,),
    ).fetchone()


def _fetch_classroom_by_name(connection: sqlite3.Connection, classroom_name: str) -> sqlite3.Row | None:
    return connection.execute(
        """
        SELECT id, classroom_name, building_name, floor, description, is_active, created_at
        FROM classrooms
        WHERE classroom_name = ?
        LIMIT 1
        """,
        (classroom_name,),
    ).fetchone()


def _error(message: str, status: str = "bad_request", *, classroom: Any = None) -> dict[str, Any]:
    return {
        "success": False,
        "status": status,
        "message": message,
        "classroom": classroom,
    }


def create_classroom(
    classroom_name: str,
    building_name: str | None = None,
    floor: str | None = None,
    description: str | None = None,
) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        normalized_name = _normalize_text(classroom_name)
        if normalized_name is None:
            return _error("강의실명을 입력해 주세요.")

        with get_connection() as connection:
            existing = _fetch_classroom_by_name(connection, normalized_name)
            if existing is not None:
                if bool(existing["is_active"]):
                    return _error("이미 등록된 강의실명입니다.", classroom=_classroom_payload(existing))

                connection.execute(
                    """
                    UPDATE classrooms
                    SET
                        building_name = ?,
                        floor = ?,
                        description = ?,
                        is_active = 1,
                        created_at = ?
                    WHERE id = ?
                    """,
                    (
                        _normalize_text(building_name),
                        _normalize_text(floor),
                        _normalize_text(description),
                        _now_text(),
                        existing["id"],
                    ),
                )
                row = _fetch_classroom(connection, int(existing["id"]))
                connection.commit()
                return {
                    "success": True,
                    "message": "강의실을 다시 활성화했습니다.",
                    "classroom": _classroom_payload(row),
                }

            cursor = connection.execute(
                """
                INSERT INTO classrooms (
                    classroom_name,
                    building_name,
                    floor,
                    description,
                    is_active,
                    created_at
                )
                VALUES (?, ?, ?, ?, 1, ?)
                """,
                (
                    normalized_name,
                    _normalize_text(building_name),
                    _normalize_text(floor),
                    _normalize_text(description),
                    _now_text(),
                ),
            )
            classroom_id = int(cursor.lastrowid)
            row = _fetch_classroom(connection, classroom_id)
            connection.commit()

        return {
            "success": True,
            "message": "강의실 저장 성공",
            "classroom": _classroom_payload(row),
        }

    except sqlite3.IntegrityError:
        return _error("이미 등록된 강의실명입니다.")
    except sqlite3.Error as error:
        return _error(f"강의실 저장 중 데이터베이스 오류가 발생했습니다: {error}", status="server_error")


def get_classrooms(active_only: bool = True) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            if active_only:
                rows = connection.execute(
                    """
                    SELECT id, classroom_name, building_name, floor, description, is_active, created_at
                    FROM classrooms
                    WHERE is_active = 1
                    ORDER BY building_name IS NULL, building_name, floor IS NULL, floor, classroom_name, id
                    """
                ).fetchall()
            else:
                rows = connection.execute(
                    """
                    SELECT id, classroom_name, building_name, floor, description, is_active, created_at
                    FROM classrooms
                    ORDER BY is_active DESC, building_name IS NULL, building_name, floor IS NULL, floor, classroom_name, id
                    """
                ).fetchall()

        return {
            "success": True,
            "message": "강의실 목록 조회 성공",
            "items": [_classroom_payload(row) for row in rows],
        }

    except sqlite3.Error as error:
        return {
            "success": False,
            "status": "server_error",
            "message": f"강의실 목록 조회 중 데이터베이스 오류가 발생했습니다: {error}",
            "items": [],
        }


def get_classroom_by_id(classroom_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            row = _fetch_classroom(connection, classroom_id)

        if row is None:
            return _error("강의실을 찾을 수 없습니다.", status="not_found")

        return {
            "success": True,
            "message": "강의실 상세 조회 성공",
            "classroom": _classroom_payload(row),
        }

    except sqlite3.Error as error:
        return _error(f"강의실 상세 조회 중 데이터베이스 오류가 발생했습니다: {error}", status="server_error")


def update_classroom(
    classroom_id: int,
    classroom_name: str | None = None,
    building_name: str | None = None,
    floor: str | None = None,
    description: str | None = None,
    is_active: bool | None = None,
) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            current = _fetch_classroom(connection, classroom_id)
            if current is None:
                return _error("강의실을 찾을 수 없습니다.", status="not_found")

            next_name = current["classroom_name"] if classroom_name is None else _normalize_text(classroom_name)
            if next_name is None:
                return _error("강의실명을 입력해 주세요.")

            next_building = current["building_name"] if building_name is None else _normalize_text(building_name)
            next_floor = current["floor"] if floor is None else _normalize_text(floor)
            next_description = current["description"] if description is None else _normalize_text(description)
            next_active = bool(current["is_active"]) if is_active is None else bool(is_active)

            connection.execute(
                """
                UPDATE classrooms
                SET
                    classroom_name = ?,
                    building_name = ?,
                    floor = ?,
                    description = ?,
                    is_active = ?
                WHERE id = ?
                """,
                (
                    next_name,
                    next_building,
                    next_floor,
                    next_description,
                    1 if next_active else 0,
                    classroom_id,
                ),
            )
            row = _fetch_classroom(connection, classroom_id)
            connection.commit()

        return {
            "success": True,
            "message": "강의실 저장 성공",
            "classroom": _classroom_payload(row),
        }

    except sqlite3.IntegrityError:
        return _error("이미 등록된 강의실명입니다.")
    except sqlite3.Error as error:
        return _error(f"강의실 수정 중 데이터베이스 오류가 발생했습니다: {error}", status="server_error")


def delete_classroom(classroom_id: int) -> dict[str, Any]:
    try:
        init_db(verbose=False)
        with get_connection() as connection:
            current = _fetch_classroom(connection, classroom_id)
            if current is None:
                return _error("강의실을 찾을 수 없습니다.", status="not_found")

            connection.execute(
                """
                UPDATE classrooms
                SET is_active = 0
                WHERE id = ?
                """,
                (classroom_id,),
            )
            row = _fetch_classroom(connection, classroom_id)
            connection.commit()

        return {
            "success": True,
            "message": "강의실을 비활성화했습니다.",
            "classroom": _classroom_payload(row),
        }

    except sqlite3.Error as error:
        return _error(f"강의실 삭제 중 데이터베이스 오류가 발생했습니다: {error}", status="server_error")
