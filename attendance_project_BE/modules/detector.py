from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from ultralytics import YOLO

from config import FACE_DETECTION_CONFIDENCE, YOLO_MODEL_PATH


class FaceDetector:
    """YOLO-based face detector. It detects face boxes only."""

    def __init__(
        self,
        model_path: Path = YOLO_MODEL_PATH,
        confidence_threshold: float = FACE_DETECTION_CONFIDENCE,
    ) -> None:
        self.model_path = Path(model_path)
        self.confidence_threshold = confidence_threshold

        if not self.model_path.exists():
            message = f"YOLO 얼굴 검출 모델 파일을 찾을 수 없습니다: {self.model_path}"
            print(message)
            raise FileNotFoundError(message)

        self.model = YOLO(str(self.model_path))

    def detect_faces(self, frame: np.ndarray) -> list[dict[str, Any]]:
        """
        Detect face boxes from an OpenCV BGR frame.

        Returns:
            [
                {"box": [x1, y1, x2, y2], "confidence": 0.91, "class_id": 0}
            ]
        """
        if frame is None or frame.size == 0:
            return []

        height, width = frame.shape[:2]
        results = self.model.predict(
            source=frame,
            conf=self.confidence_threshold,
            verbose=False,
        )

        if not results or results[0].boxes is None:
            return []

        faces: list[dict[str, Any]] = []
        for detected_box in results[0].boxes:
            xyxy = detected_box.xyxy[0].detach().cpu().numpy()
            confidence = float(detected_box.conf[0].detach().cpu().item())
            class_id = int(detected_box.cls[0].detach().cpu().item()) if detected_box.cls is not None else 0

            x1, y1, x2, y2 = xyxy.astype(int).tolist()
            x1 = max(0, min(x1, width - 1))
            y1 = max(0, min(y1, height - 1))
            x2 = max(0, min(x2, width - 1))
            y2 = max(0, min(y2, height - 1))

            faces.append(
                {
                    "box": [x1, y1, x2, y2],
                    "confidence": confidence,
                    "class_id": class_id,
                }
            )

        return faces


def detect_faces(frame: np.ndarray) -> list[dict[str, Any]]:
    detector = FaceDetector()
    return detector.detect_faces(frame)


def _draw_faces(frame: np.ndarray, faces: list[dict[str, Any]]) -> np.ndarray:
    output = frame.copy()

    for face in faces:
        x1, y1, x2, y2 = face["box"]
        confidence = face["confidence"]
        cv2.rectangle(output, (x1, y1), (x2, y2), (0, 255, 0), 2)
        cv2.putText(
            output,
            f"face {confidence:.2f}",
            (x1, max(0, y1 - 10)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (0, 255, 0),
            2,
            cv2.LINE_AA,
        )

    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Test YOLO face detection on one image.")
    parser.add_argument("image_path", help="Path to the test image.")
    parser.add_argument(
        "--output",
        default="detector_test_output.jpg",
        help="Path to save the image with detected face boxes.",
    )
    args = parser.parse_args()

    image_path = Path(args.image_path)
    if not image_path.exists():
        raise FileNotFoundError(f"테스트 이미지 파일을 찾을 수 없습니다: {image_path}")

    image = cv2.imread(str(image_path))
    if image is None:
        raise ValueError(f"테스트 이미지를 읽을 수 없습니다: {image_path}")

    face_detector = FaceDetector()
    detected_faces = face_detector.detect_faces(image)

    print(f"검출된 얼굴 수: {len(detected_faces)}")
    for index, face in enumerate(detected_faces, start=1):
        print(f"{index}. box={face['box']} confidence={face['confidence']:.4f}")

    output_image = _draw_faces(image, detected_faces)
    cv2.imwrite(args.output, output_image)
    print(f"결과 이미지 저장: {args.output}")
