from __future__ import annotations

import argparse
from pathlib import Path

import cv2


PROJECT_DIR = Path(__file__).resolve().parent
DATASET_DIR = PROJECT_DIR / "datasets" / "face_dataset"
IMAGE_DIR = DATASET_DIR / "images"
LABEL_DIR = DATASET_DIR / "labels"
OUTPUT_DIR = DATASET_DIR / "visualized_labels"
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png"}


def _find_image(label_path: Path, split: str) -> Path | None:
    image_dir = IMAGE_DIR / split
    for extension in IMAGE_EXTENSIONS:
        image_path = image_dir / f"{label_path.stem}{extension}"
        if image_path.exists():
            return image_path
    return None


def _draw_yolo_label(image, label_line: str) -> None:
    height, width = image.shape[:2]
    parts = label_line.split()
    if len(parts) != 5:
        return

    class_id = parts[0]
    center_x = float(parts[1]) * width
    center_y = float(parts[2]) * height
    box_width = float(parts[3]) * width
    box_height = float(parts[4]) * height

    x1 = int(center_x - box_width / 2)
    y1 = int(center_y - box_height / 2)
    x2 = int(center_x + box_width / 2)
    y2 = int(center_y + box_height / 2)

    x1 = max(0, min(x1, width - 1))
    y1 = max(0, min(y1, height - 1))
    x2 = max(0, min(x2, width - 1))
    y2 = max(0, min(y2, height - 1))

    cv2.rectangle(image, (x1, y1), (x2, y2), (0, 255, 0), 2)
    cv2.putText(
        image,
        f"class {class_id}",
        (x1, max(20, y1 - 8)),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.7,
        (0, 255, 0),
        2,
        cv2.LINE_AA,
    )


def visualize_labels(split: str, limit: int | None = None) -> None:
    label_dir = LABEL_DIR / split
    output_dir = OUTPUT_DIR / split
    output_dir.mkdir(parents=True, exist_ok=True)

    label_paths = sorted(label_dir.glob("*.txt"))
    if limit is not None:
        label_paths = label_paths[:limit]

    saved_count = 0
    missing_image_count = 0
    empty_label_count = 0

    for label_path in label_paths:
        image_path = _find_image(label_path, split)
        if image_path is None:
            missing_image_count += 1
            print(f"이미지를 찾을 수 없습니다: {label_path.name}")
            continue

        image = cv2.imread(str(image_path))
        if image is None:
            missing_image_count += 1
            print(f"이미지를 읽을 수 없습니다: {image_path}")
            continue

        lines = [
            line.strip()
            for line in label_path.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
        if not lines:
            empty_label_count += 1
            cv2.putText(
                image,
                "EMPTY LABEL",
                (20, 40),
                cv2.FONT_HERSHEY_SIMPLEX,
                1.0,
                (0, 0, 255),
                3,
                cv2.LINE_AA,
            )

        for line in lines:
            _draw_yolo_label(image, line)

        output_path = output_dir / image_path.name
        cv2.imwrite(str(output_path), image)
        saved_count += 1

    print("라벨 시각화 완료")
    print(f"split: {split}")
    print(f"저장 위치: {output_dir}")
    print(f"저장 이미지 수: {saved_count}")
    print(f"빈 라벨 수: {empty_label_count}")
    print(f"이미지 누락/읽기 실패 수: {missing_image_count}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Visualize YOLO txt labels on images.")
    parser.add_argument(
        "--split",
        choices=["train", "val"],
        default="val",
        help="시각화할 데이터 split입니다.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="앞에서부터 지정한 개수만 시각화합니다.",
    )
    args = parser.parse_args()

    visualize_labels(split=args.split, limit=args.limit)
