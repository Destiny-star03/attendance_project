from pathlib import Path

DATASET_DIR = Path("datasets/face_dataset")

def create_txt_for_split(split_name):
    image_dir = DATASET_DIR / "images" / split_name
    label_dir = DATASET_DIR / "labels" / split_name

    label_dir.mkdir(parents=True, exist_ok=True)

    if not image_dir.exists():
        print(f"이미지 폴더가 없습니다: {image_dir}")
        return

    image_files = []
    image_files.extend(image_dir.glob("*.jpg"))
    image_files.extend(image_dir.glob("*.jpeg"))
    image_files.extend(image_dir.glob("*.png"))

    if not image_files:
        print(f"{split_name} 폴더에 이미지가 없습니다.")
        return

    for image_path in image_files:
        txt_path = label_dir / f"{image_path.stem}.txt"

        if not txt_path.exists():
            txt_path.write_text("", encoding="utf-8")
            print(f"[{split_name}] 생성 완료: {txt_path}")
        else:
            print(f"[{split_name}] 이미 존재함: {txt_path}")

def main():
    create_txt_for_split("train")
    create_txt_for_split("val")
    print("모든 TXT 파일 생성 완료")

if __name__ == "__main__":
    main()