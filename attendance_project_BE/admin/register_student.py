from __future__ import annotations

from pathlib import Path
import sys


PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import cv2
import numpy as np

from modules.detector import FaceDetector
from modules.face_pipeline import extract_single_face
from modules.recognizer import encoding_to_bytes, extract_face_encoding
from modules.student_service import add_student


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png"}


def _prompt_required(label: str) -> str:
    while True:
        value = input(f"{label}: ").strip().strip('"')
        if value:
            return value
        print(f"{label}을(를) 입력해 주세요.")


def _collect_image_paths(path: Path) -> list[Path]:
    if path.is_file():
        return [path] if path.suffix.lower() in IMAGE_EXTENSIONS else []

    if path.is_dir():
        return sorted(
            image_path
            for image_path in path.rglob("*")
            if image_path.is_file() and image_path.suffix.lower() in IMAGE_EXTENSIONS
        )

    return []


def _build_face_encoding_bytes(path: Path, detector: FaceDetector) -> bytes | None:
    image_paths = _collect_image_paths(path)
    if not image_paths:
        print("등록 실패: JPG/PNG 얼굴 사진을 찾지 못했습니다.")
        return None

    encodings: list[np.ndarray] = []
    for image_path in image_paths:
        image = cv2.imread(str(image_path))
        result = extract_single_face(image, detector)

        if not result.success or result.face_crop is None:
            print(f"건너뜀: {result.message} ({image_path.name})")
            continue

        try:
            encodings.append(extract_face_encoding(result.face_crop))
        except Exception as error:
            print(f"건너뜀: 얼굴 임베딩 추출 오류 ({image_path.name}, {error})")

    if not encodings:
        print("등록 실패: 사용할 수 있는 얼굴 사진이 없습니다.")
        return None

    mean_encoding = np.mean(np.stack(encodings), axis=0)
    norm = np.linalg.norm(mean_encoding)
    if norm == 0:
        print("등록 실패: 얼굴 임베딩이 올바르지 않습니다.")
        return None

    print(f"사용한 얼굴 사진 수: {len(encodings)} / {len(image_paths)}")
    return encoding_to_bytes(mean_encoding / norm)


def register_student_from_console() -> bool:
    print("학생 등록을 시작합니다.")
    print("얼굴 사진 경로에는 이미지 파일 1개 또는 여러 이미지가 들어 있는 폴더를 입력할 수 있습니다.")
    print("이미 등록된 학번이면 등록에 실패합니다. FaceNet 전환 후에는 새 DB로 다시 등록하세요.")

    name = _prompt_required("이름")
    student_no = _prompt_required("학번")
    department = input("학과: ").strip() or None
    photo_path = Path(_prompt_required("얼굴 사진 파일 또는 폴더 경로"))

    if not photo_path.exists():
        print(f"등록 실패: 경로가 존재하지 않습니다. ({photo_path})")
        return False

    try:
        detector = FaceDetector()
    except FileNotFoundError:
        print("등록 실패: YOLO26 얼굴 검출 모델 파일을 찾을 수 없습니다.")
        print("학습 결과 best.pt를 models/yolo26_face.pt로 배치한 뒤 다시 실행해 주세요.")
        return False
    except Exception as error:
        print(f"등록 실패: 얼굴 검출 모델을 불러오는 중 오류가 발생했습니다. ({error})")
        return False

    face_encoding = _build_face_encoding_bytes(photo_path, detector)
    if face_encoding is None:
        return False

    result = add_student(
        student_no=student_no,
        name=name,
        department=department,
        face_encoding=face_encoding,
    )

    if result.get("success"):
        print("등록 성공: 학생 정보가 저장되었습니다.")
        print(f"학생 ID: {result.get('student_id')}")
        print(f"학번: {student_no}")
        print(f"이름: {name}")
        if department:
            print(f"학과: {department}")
        return True

    print(f"등록 실패: {result.get('message', '학생 정보를 저장하지 못했습니다.')}")
    return False


if __name__ == "__main__":
    register_student_from_console()
