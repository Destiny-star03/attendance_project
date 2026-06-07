from __future__ import annotations

import argparse
import io
import sqlite3
from pathlib import Path
from typing import Any

import cv2
import numpy as np
import torch
from facenet_pytorch import InceptionResnetV1
from facenet_pytorch.models.mtcnn import fixed_image_standardization

from config import FACE_RECOGNITION_MARGIN, FACE_RECOGNITION_MODEL, FACE_RECOGNITION_THRESHOLD
from modules.database import get_connection, init_db


FACENET_IMAGE_SIZE = (160, 160)
FACENET_PRETRAINED_DATASET = "vggface2"

_facenet_model: InceptionResnetV1 | None = None
_facenet_device = torch.device("cuda:0" if torch.cuda.is_available() else "cpu")


def _get_facenet_model() -> InceptionResnetV1:
    global _facenet_model

    if _facenet_model is None:
        try:
            _facenet_model = InceptionResnetV1(pretrained=FACENET_PRETRAINED_DATASET).eval()
            _facenet_model.to(_facenet_device)
        except Exception as error:
            raise RuntimeError(f"FaceNet 모델을 불러오지 못했습니다: {error}") from error

    return _facenet_model


def preprocess_face(face_image: np.ndarray) -> torch.Tensor:
    """Convert an OpenCV BGR face crop into a FaceNet input tensor."""
    if face_image is None or face_image.size == 0:
        raise ValueError("얼굴 이미지가 비어 있습니다.")

    if len(face_image.shape) == 2:
        rgb_image = cv2.cvtColor(face_image, cv2.COLOR_GRAY2RGB)
    else:
        rgb_image = cv2.cvtColor(face_image, cv2.COLOR_BGR2RGB)

    resized = cv2.resize(rgb_image, FACENET_IMAGE_SIZE, interpolation=cv2.INTER_AREA)
    tensor = torch.from_numpy(resized).permute(2, 0, 1).float()
    tensor = fixed_image_standardization(tensor)
    return tensor.unsqueeze(0).to(_facenet_device)


def extract_face_encoding(face_image: np.ndarray) -> np.ndarray:
    """Extract a L2-normalized 512-dimensional FaceNet embedding."""
    model = _get_facenet_model()
    input_tensor = preprocess_face(face_image)

    with torch.no_grad():
        embedding = model(input_tensor).detach().cpu().numpy()[0].astype(np.float32)

    norm = np.linalg.norm(embedding)
    if norm == 0:
        raise ValueError("FaceNet 임베딩이 올바르지 않습니다.")

    return embedding / norm


def encoding_to_bytes(encoding: np.ndarray) -> bytes:
    buffer = io.BytesIO()
    np.save(buffer, encoding.astype(np.float32), allow_pickle=False)
    return buffer.getvalue()


def face_image_to_encoding_bytes(face_image: np.ndarray) -> bytes:
    return encoding_to_bytes(extract_face_encoding(face_image))


def encoding_from_bytes(data: bytes) -> np.ndarray:
    if not data:
        raise ValueError("저장된 얼굴 임베딩이 비어 있습니다.")

    try:
        buffer = io.BytesIO(data)
        encoding = np.load(buffer, allow_pickle=False)
    except Exception:
        encoding = np.frombuffer(data, dtype=np.float32)

    encoding = encoding.astype(np.float32).reshape(-1)
    norm = np.linalg.norm(encoding)
    if norm == 0:
        raise ValueError("저장된 얼굴 임베딩이 올바르지 않습니다.")

    return encoding / norm


def calculate_distance(source_encoding: np.ndarray, stored_encoding: np.ndarray) -> float:
    """Return Euclidean distance between L2-normalized embeddings."""
    source = source_encoding.reshape(-1)
    stored = stored_encoding.reshape(-1)

    if source.shape != stored.shape:
        raise ValueError(
            f"임베딩 크기가 다릅니다: source={source.shape[0]}, stored={stored.shape[0]}"
        )

    return float(np.linalg.norm(source - stored))


def calculate_similarity(source_encoding: np.ndarray, stored_encoding: np.ndarray) -> float:
    """Return cosine similarity for debugging."""
    source = source_encoding.reshape(-1)
    stored = stored_encoding.reshape(-1)
    similarity = float(np.dot(source, stored))
    return max(-1.0, min(1.0, similarity))


class FaceRecognizer:
    """Compare a cropped face image with stored FaceNet embeddings."""

    def __init__(
        self,
        threshold: float = FACE_RECOGNITION_THRESHOLD,
        margin: float = FACE_RECOGNITION_MARGIN,
    ) -> None:
        self.threshold = threshold
        self.margin = margin
        self.model_name = FACE_RECOGNITION_MODEL

    def recognize(self, face_image: np.ndarray) -> dict[str, Any]:
        init_db(verbose=False)
        target_encoding = extract_face_encoding(face_image)

        candidates: list[dict[str, Any]] = []

        with get_connection() as connection:
            rows = connection.execute(
                """
                SELECT id, face_encoding
                FROM students
                ORDER BY id ASC
                """
            ).fetchall()

        for row in rows:
            try:
                stored_encoding = encoding_from_bytes(row["face_encoding"])
                distance = calculate_distance(target_encoding, stored_encoding)
                similarity = calculate_similarity(target_encoding, stored_encoding)
            except ValueError as error:
                print(f"잘못된 얼굴 임베딩을 건너뜁니다. student_id={row['id']}: {error}")
                continue

            candidates.append(
                {
                    "student_id": int(row["id"]),
                    "distance": distance,
                    "similarity": similarity,
                }
            )

        candidates.sort(key=lambda candidate: candidate["distance"])
        top1 = candidates[0] if candidates else None
        top2 = candidates[1] if len(candidates) >= 2 else None

        best_student_id = int(top1["student_id"]) if top1 is not None else None
        best_distance = float(top1["distance"]) if top1 is not None else None
        best_similarity = float(top1["similarity"]) if top1 is not None else None
        second_distance = float(top2["distance"]) if top2 is not None else None
        second_student_id = int(top2["student_id"]) if top2 is not None else None
        distance_margin = (
            second_distance - best_distance
            if second_distance is not None and best_distance is not None
            else None
        )

        threshold_pass = best_distance is not None and best_distance < self.threshold
        margin_pass = distance_margin is None or distance_margin >= self.margin
        ambiguous = bool(threshold_pass and not margin_pass)
        matched = bool(threshold_pass and margin_pass)

        return {
            "student_id": best_student_id if matched else None,
            "distance": best_distance,
            "similarity": best_similarity,
            "second_student_id": second_student_id,
            "second_distance": second_distance,
            "distance_margin": distance_margin,
            "threshold": self.threshold,
            "margin_threshold": self.margin,
            "threshold_pass": threshold_pass,
            "margin_pass": margin_pass,
            "ambiguous": ambiguous,
            "matched": matched,
        }


def recognize(face_image: np.ndarray) -> dict[str, Any]:
    recognizer = FaceRecognizer()
    return recognizer.recognize(face_image)


def _register_test_student(
    image_path: Path,
    student_no: str,
    name: str,
    department: str | None,
) -> dict[str, Any]:
    from modules.student_service import add_student

    image = cv2.imread(str(image_path))
    if image is None:
        raise ValueError(f"이미지를 읽을 수 없습니다: {image_path}")

    face_encoding = face_image_to_encoding_bytes(image)
    return add_student(student_no, name, department, face_encoding)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Test FaceNet recognition on one cropped face image.")
    parser.add_argument("image_path", help="Path to a cropped face image.")
    parser.add_argument("--register", action="store_true", help="Register this image before recognition.")
    parser.add_argument("--student-no", default="TEST001", help="Student number for --register.")
    parser.add_argument("--name", default="Test Student", help="Student name for --register.")
    parser.add_argument("--department", default=None, help="Department for --register.")
    args = parser.parse_args()

    test_image_path = Path(args.image_path)
    if not test_image_path.exists():
        raise FileNotFoundError(f"테스트 이미지 파일을 찾을 수 없습니다: {test_image_path}")

    if args.register:
        print(_register_test_student(test_image_path, args.student_no, args.name, args.department))

    test_image = cv2.imread(str(test_image_path))
    if test_image is None:
        raise ValueError(f"이미지를 읽을 수 없습니다: {test_image_path}")

    print(recognize(test_image))
