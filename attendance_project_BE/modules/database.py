from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Any, Iterable

from config import DB_PATH, ensure_directories


def get_connection(db_path: Path = DB_PATH) -> sqlite3.Connection:
    ensure_directories()
    connection = sqlite3.connect(db_path)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    return connection


def execute_query(
    query: str,
    params: Iterable[Any] | None = None,
    *,
    fetch_one: bool = False,
    fetch_all: bool = False,
    commit: bool = False,
) -> sqlite3.Row | list[sqlite3.Row] | int | None:
    params = params or ()

    with get_connection() as connection:
        cursor = connection.execute(query, tuple(params))

        if commit:
            connection.commit()
            return cursor.lastrowid

        if fetch_one:
            return cursor.fetchone()

        if fetch_all:
            return cursor.fetchall()

    return None


def _table_exists(connection: sqlite3.Connection, table_name: str) -> bool:
    row = connection.execute(
        """
        SELECT name
        FROM sqlite_master
        WHERE type = 'table' AND name = ?
        """,
        (table_name,),
    ).fetchone()
    return row is not None


def _get_columns(connection: sqlite3.Connection, table_name: str) -> set[str]:
    rows = connection.execute(f"PRAGMA table_info({table_name})").fetchall()
    return {row["name"] for row in rows}


def _create_students_table(cursor: sqlite3.Cursor) -> None:
    # 학생 테이블은 기존 구조를 유지한다. face_encoding BLOB도 변경하지 않는다.
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS students (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            student_no TEXT UNIQUE NOT NULL,
            name TEXT NOT NULL,
            department TEXT,
            face_encoding BLOB NOT NULL,
            created_at TEXT NOT NULL
        )
        """
    )


def _create_attendance_sessions_table(
    cursor: sqlite3.Cursor,
    table_name: str = "attendance_sessions",
) -> None:
    cursor.execute(
        f"""
        CREATE TABLE IF NOT EXISTS {table_name} (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            subject_name TEXT NOT NULL,
            class_date TEXT NOT NULL,
            start_time TEXT,
            end_time TEXT,
            is_active INTEGER DEFAULT 1,
            created_at TEXT NOT NULL
        )
        """
    )


def _create_attendance_table(cursor: sqlite3.Cursor, table_name: str = "attendance") -> None:
    cursor.execute(
        f"""
        CREATE TABLE IF NOT EXISTS {table_name} (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id INTEGER NOT NULL,
            student_id INTEGER NOT NULL,
            attendance_date TEXT NOT NULL,
            attendance_time TEXT NOT NULL,
            status TEXT NOT NULL,
            confidence REAL,
            distance REAL,
            FOREIGN KEY(session_id) REFERENCES attendance_sessions(id),
            FOREIGN KEY(student_id) REFERENCES students(id),
            UNIQUE(session_id, student_id)
        )
        """
    )


def _attendance_sessions_schema_is_current(connection: sqlite3.Connection) -> bool:
    if not _table_exists(connection, "attendance_sessions"):
        return False

    columns = _get_columns(connection, "attendance_sessions")
    required_columns = {
        "id",
        "subject_name",
        "class_date",
        "start_time",
        "end_time",
        "is_active",
        "created_at",
    }
    return required_columns.issubset(columns)


def _attendance_unique_is_current(connection: sqlite3.Connection) -> bool:
    if not _table_exists(connection, "attendance"):
        return False

    indexes = connection.execute("PRAGMA index_list(attendance)").fetchall()
    for index in indexes:
        if not index["unique"]:
            continue

        index_name = index["name"]
        index_columns = [
            row["name"]
            for row in connection.execute(f"PRAGMA index_info({index_name})").fetchall()
        ]
        if index_columns == ["session_id", "student_id"]:
            return True

    return False


def _attendance_foreign_keys_are_current(connection: sqlite3.Connection) -> bool:
    if not _table_exists(connection, "attendance"):
        return False

    foreign_keys = connection.execute("PRAGMA foreign_key_list(attendance)").fetchall()
    session_fk_ok = False
    student_fk_ok = False

    for foreign_key in foreign_keys:
        table_name = foreign_key["table"]
        from_column = foreign_key["from"]
        to_column = foreign_key["to"]

        if table_name == "attendance_sessions" and from_column == "session_id" and to_column == "id":
            session_fk_ok = True
        if table_name == "students" and from_column == "student_id" and to_column == "id":
            student_fk_ok = True

    return session_fk_ok and student_fk_ok


def _attendance_schema_is_current(connection: sqlite3.Connection) -> bool:
    if not _table_exists(connection, "attendance"):
        return False

    columns = _get_columns(connection, "attendance")
    required_columns = {
        "id",
        "session_id",
        "student_id",
        "attendance_date",
        "attendance_time",
        "status",
        "confidence",
        "distance",
    }
    return (
        required_columns.issubset(columns)
        and _attendance_unique_is_current(connection)
        and _attendance_foreign_keys_are_current(connection)
    )


def _migrate_attendance_sessions_table(connection: sqlite3.Connection) -> None:
    cursor = connection.cursor()

    if not _table_exists(connection, "attendance_sessions"):
        _create_attendance_sessions_table(cursor)
        return

    if _attendance_sessions_schema_is_current(connection):
        return

    old_columns = _get_columns(connection, "attendance_sessions")
    cursor.execute("ALTER TABLE attendance_sessions RENAME TO attendance_sessions_legacy")
    _create_attendance_sessions_table(cursor)

    if {"id", "class_date", "created_at"}.issubset(old_columns):
        subject_source = "subject_name" if "subject_name" in old_columns else None
        if subject_source is None and "session_name" in old_columns:
            subject_source = "session_name"

        subject_expr = subject_source if subject_source is not None else "'기존 출석 세션'"
        start_expr = "start_time" if "start_time" in old_columns else "NULL"
        end_expr = "end_time" if "end_time" in old_columns else "NULL"
        active_expr = "is_active" if "is_active" in old_columns else "1"

        cursor.execute(
            f"""
            INSERT INTO attendance_sessions (
                id,
                subject_name,
                class_date,
                start_time,
                end_time,
                is_active,
                created_at
            )
            SELECT
                id,
                COALESCE({subject_expr}, '기존 출석 세션'),
                class_date,
                {start_expr},
                {end_expr},
                COALESCE({active_expr}, 1),
                created_at
            FROM attendance_sessions_legacy
            """
        )

    cursor.execute("DROP TABLE attendance_sessions_legacy")


def _migrate_attendance_table(connection: sqlite3.Connection) -> None:
    cursor = connection.cursor()

    if not _table_exists(connection, "attendance"):
        _create_attendance_table(cursor)
        return

    if _attendance_schema_is_current(connection):
        return

    old_columns = _get_columns(connection, "attendance")
    cursor.execute("ALTER TABLE attendance RENAME TO attendance_legacy")
    _create_attendance_table(cursor)

    if {"session_id", "student_id", "attendance_date", "attendance_time", "status"}.issubset(old_columns):
        confidence_expr = "confidence" if "confidence" in old_columns else "NULL"
        distance_expr = "distance" if "distance" in old_columns else "NULL"
        cursor.execute(
            f"""
            INSERT OR IGNORE INTO attendance (
                id,
                session_id,
                student_id,
                attendance_date,
                attendance_time,
                status,
                confidence,
                distance
            )
            SELECT
                id,
                session_id,
                student_id,
                attendance_date,
                attendance_time,
                status,
                {confidence_expr},
                {distance_expr}
            FROM attendance_legacy
            """
        )

    elif {"student_id", "attendance_date", "attendance_time", "status"}.issubset(old_columns):
        # 예전 날짜 기준 출석 테이블은 날짜별 임시 세션을 만들어 최대한 보존한다.
        legacy_rows = cursor.execute(
            """
            SELECT student_id, attendance_date, attendance_time, status
            FROM attendance_legacy
            ORDER BY attendance_date ASC, attendance_time ASC, id ASC
            """
        ).fetchall()

        session_ids_by_date: dict[str, int] = {}
        for row in legacy_rows:
            attendance_date = row["attendance_date"]
            if attendance_date not in session_ids_by_date:
                session_cursor = cursor.execute(
                    """
                    INSERT INTO attendance_sessions (
                        subject_name,
                        class_date,
                        start_time,
                        end_time,
                        is_active,
                        created_at
                    )
                    VALUES (?, ?, NULL, NULL, 0, datetime('now', 'localtime'))
                    """,
                    (f"기존 출석 세션 {attendance_date}", attendance_date),
                )
                session_ids_by_date[attendance_date] = int(session_cursor.lastrowid)

            cursor.execute(
                """
                INSERT OR IGNORE INTO attendance (
                    session_id,
                    student_id,
                    attendance_date,
                    attendance_time,
                    status,
                    confidence,
                    distance
                )
                VALUES (?, ?, ?, ?, ?, NULL, NULL)
                """,
                (
                    session_ids_by_date[attendance_date],
                    row["student_id"],
                    attendance_date,
                    row["attendance_time"],
                    row["status"],
                ),
            )

    cursor.execute("DROP TABLE attendance_legacy")


def reset_attendance_tables() -> None:
    """개발용으로 출석 세션과 출석 기록만 초기화한다. students 테이블은 보존한다."""
    ensure_directories()

    with get_connection() as connection:
        connection.execute("PRAGMA foreign_keys = OFF")
        cursor = connection.cursor()
        cursor.execute("DROP TABLE IF EXISTS attendance")
        cursor.execute("DROP TABLE IF EXISTS attendance_sessions")
        _create_attendance_sessions_table(cursor)
        _create_attendance_table(cursor)
        connection.commit()
        connection.execute("PRAGMA foreign_keys = ON")


def init_db(verbose: bool = True) -> None:
    ensure_directories()

    with get_connection() as connection:
        # 기존 테이블을 재구성할 수 있으므로 마이그레이션 중에는 외래키 검사를 잠시 끈다.
        connection.execute("PRAGMA foreign_keys = OFF")
        cursor = connection.cursor()
        _create_students_table(cursor)
        _migrate_attendance_sessions_table(connection)
        _migrate_attendance_table(connection)
        connection.commit()
        connection.execute("PRAGMA foreign_keys = ON")

    if verbose:
        print("데이터베이스 초기화 완료")


if __name__ == "__main__":
    init_db()
