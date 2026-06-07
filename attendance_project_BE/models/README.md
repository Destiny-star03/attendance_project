# models

이 폴더에는 얼굴 검출용 YOLO26 모델 파일을 저장합니다.

런타임 필수 파일:

```text
yolo26_face.pt
```

서버와 PC 데모는 `config.py`의 `YOLO_MODEL_PATH`를 통해 이 파일만 로드합니다.
학습 결과를 사용할 때는 `runs/detect/runs/train/face_yolo_30ep/weights/best.pt`를
`models/yolo26_face.pt`로 복사한 뒤 실행하세요.

루트의 `yolo11n.pt`는 학습 시작점/base weight이며, 런타임 얼굴 검출 모델이 아닙니다.

추후 Android 연동 시 이 모델은 ONNX 또는 TFLite 형식으로 export하는 구조를 별도 문서에 정리합니다.
