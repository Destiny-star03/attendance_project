# admin/list_students.py
# 콘솔에서 등록된 학생 목록을 조회합니다.

from modules.database import init_db
from modules.student_service import get_all_students


def print_students() -> None:
    """등록된 학생 목록을 콘솔에 출력합니다."""
    init_db()
    students = get_all_students()

    if not students:
        print("등록된 학생이 없습니다.")
        return

    print("등록된 학생 목록")
    print("-" * 80)
    print(f"{'ID':<5} {'학번':<15} {'이름':<15} {'학과/반':<20} {'등록일시'}")
    print("-" * 80)

    for student in students:
        department = student.get("department") or "-"
        print(
            f"{student['id']:<5} "
            f"{student['student_no']:<15} "
            f"{student['name']:<15} "
            f"{department:<20} "
            f"{student['created_at']}"
        )


if __name__ == "__main__":
    print_students()
