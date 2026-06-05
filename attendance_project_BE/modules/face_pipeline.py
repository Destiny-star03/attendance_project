from __future__ import annotations

from dataclasses import dataclass
from typing import Any

import cv2
import numpy as np

from modules.detector import FaceDetector
from modules.recognizer import encoding_to_bytes, extract_face_encoding


FACE_NOT_FOUND_MESSAGE = "얼굴을 찾지 못했습니다."
MULTIPLE_FACES_MESSAGE = "한 명의 얼굴만 촬영해 주세요."
IMAGE_DECODE_ERROR_MESSAGE = "이미지를 읽을 수 없습니다."


@dataclass
class FacePipelineResult:
    success: bool
    message: str
    image: np.ndarray | None = None
    face_crop: np.ndarray | None = None
    face_encoding: bytes | None = None
    face: dict[str, Any] | None = None
    faces: list[dict[str, Any]] | None = None


def decode_image_bytes(image_bytes: bytes) -> np.ndarray | None:
    if not image_bytes:
        return None

    array = np.frombuffer(image_bytes, dtype=np.uint8)
    return cv2.imdecode(array, cv2.IMREAD_COLOR)


def crop_face(image: np.ndarray, box: list[int]) -> np.ndarray:
    x1, y1, x2, y2 = box
    if x2 <= x1 or y2 <= y1:
        raise ValueError("얼굴 영역 좌표가 올바르지 않습니다.")

    face_crop = image[y1:y2, x1:x2]
    if face_crop.size == 0:
        raise ValueError("얼굴 crop 이미지가 비어 있습니다.")

    return face_crop


def extract_single_face(
    image: np.ndarray | None,
    detector: FaceDetector,
    *,
    build_encoding: bool = False,
) -> FacePipelineResult:
    if image is None or image.size == 0:
        return FacePipelineResult(success=False, message=IMAGE_DECODE_ERROR_MESSAGE)

    faces = detector.detect_faces(image)

    if len(faces) == 0:
        return FacePipelineResult(success=False, message=FACE_NOT_FOUND_MESSAGE, image=image, faces=faces)

    if len(faces) >= 2:
        return FacePipelineResult(success=False, message=MULTIPLE_FACES_MESSAGE, image=image, faces=faces)

    face = faces[0]
    face_crop = crop_face(image, face["box"])
    face_encoding = encoding_to_bytes(extract_face_encoding(face_crop)) if build_encoding else None

    return FacePipelineResult(
        success=True,
        message="얼굴 검출 성공",
        image=image,
        face_crop=face_crop,
        face_encoding=face_encoding,
        face=face,
        faces=faces,
    )


def extract_single_face_from_bytes(
    image_bytes: bytes,
    detector: FaceDetector,
    *,
    build_encoding: bool = False,
) -> FacePipelineResult:
    image = decode_image_bytes(image_bytes)
    return extract_single_face(image, detector, build_encoding=build_encoding)
