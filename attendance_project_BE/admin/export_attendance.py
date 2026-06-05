# admin/export_attendance.py
# Console script for exporting attendance records to CSV.

from __future__ import annotations

from pathlib import Path
import sys


PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from modules.export_service import export_attendance_to_csv


def export_attendance_from_console() -> bool:
    print("출석 기록 CSV 내보내기를 시작합니다.")
    date = input("내보낼 날짜를 입력하세요. 비워두면 오늘 날짜를 사용합니다. (YYYY-MM-DD): ").strip()

    try:
        result = export_attendance_to_csv(date or None)
    except ValueError as error:
        print(f"내보내기 실패: {error}")
        return False
    except Exception as error:
        print(f"내보내기 실패: CSV 저장 중 오류가 발생했습니다. ({error})")
        return False

    print("내보내기 완료")
    print(f"날짜: {result['date']}")
    print(f"파일: {result['file_path']}")
    print(f"출석 기록 수: {result['record_count']}")
    return True


if __name__ == "__main__":
    export_attendance_from_console()
