# models

이 폴더에는 얼굴 검출용 YOLO26 모델 파일을 저장합니다.

필수 파일명:

```text
yolo26_face.pt
```

현재 저장소에는 모델 가중치 파일을 포함하지 않습니다. 직접 학습하거나 제공받은 얼굴 검출 모델을 `models/yolo26_face.pt` 경로에 배치하세요.

추후 Android 연동 시 이 모델은 ONNX 또는 TFLite 형식으로 export하는 구조를 별도 문서에 정리합니다.
