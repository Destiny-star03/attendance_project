from __future__ import annotations

import csv
from datetime import datetime
from pathlib import Path
from typing import Any

from config import EXPORT_DIR, ensure_directories
from modules.attendance_service import get_attendance_by_date


CSV_COLUMNS = ["날짜", "시간", "이름", "학번", "학과", "출석상태"]


def _today() -> str:
    return datetime.now().date().isoformat()


def normalize_date(date: str | None) -> str:
    if date is None or not date.strip():
        return _today()

    normalized = date.strip()
    try:
        datetime.strptime(normalized, "%Y-%m-%d")
    except ValueError as error:
        raise ValueError("날짜 형식은 YYYY-MM-DD여야 합니다.") from error

    return normalized


def _record_to_csv_row(record: dict[str, Any]) -> dict[str, str]:
    return {
        "날짜": str(record.get("attendance_date", "")),
        "시간": str(record.get("attendance_time", "")),
        "이름": str(record.get("name", "")),
        "학번": str(record.get("student_no", "")),
        "학과": str(record.get("department") or ""),
        "출석상태": str(record.get("status", "")),
    }


def export_attendance_to_csv(date: str | None = None) -> dict[str, Any]:
    export_date = normalize_date(date)
    ensure_directories()

    export_path = Path(EXPORT_DIR) / f"attendance_{export_date}.csv"
    records = get_attendance_by_date(export_date)

    with export_path.open("w", newline="", encoding="utf-8-sig") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        for record in records:
            writer.writerow(_record_to_csv_row(record))

    return {
        "success": True,
        "date": export_date,
        "file_path": str(export_path),
        "record_count": len(records),
    }


if __name__ == "__main__":
    result = export_attendance_to_csv()
    print(f"CSV 저장 완료: {result['file_path']}")
    print(f"내보낸 출석 기록 수: {result['record_count']}")
