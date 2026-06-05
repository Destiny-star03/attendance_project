# YOLO26 모델 Export 및 Android 추론 계획

## 1. 목표

현재 Python 프로젝트는 `models/yolo26_face.pt` 파일을 Ultralytics YOLO 모델로 로드하여 얼굴 검출을 수행한다. 추후 Android Studio 앱과 연동할 때는 두 가지 방향을 선택할 수 있다.

1. 서버 추론 방식
   - Android 앱은 이미지를 Python 서버로 전송한다.
   - Python 서버가 YOLO26 얼굴 검출, 얼굴 인식, 출석 처리를 수행한다.

2. 온디바이스 추론 방식
   - YOLO26 얼굴 검출 모델을 TFLite 또는 ONNX 형식으로 변환한다.
   - Android 앱 내부에서 직접 얼굴 검출을 수행한다.
   - 얼굴 인식과 출석 처리는 서버에서 수행하거나, 추후 앱 내부로 확장한다.

본 문서는 YOLO26 모델을 Android 환경에서 사용할 수 있도록 export하는 계획과 두 방식의 장단점을 비교한다.

## 2. 현재 모델 구조

현재 프로젝트 설정은 다음 경로를 사용한다.

```text
models/yolo26_face.pt
```

Python 코드에서는 `config.py`의 `YOLO_MODEL_PATH`를 통해 모델 경로를 읽고, `modules/detector.py`의 `FaceDetector`가 Ultralytics `YOLO` 클래스로 모델을 로드한다.

```text
FaceDetector
  -> YOLO_MODEL_PATH
  -> YOLO 모델 로드
  -> detect_faces(frame)
  -> 얼굴 box와 confidence 반환
```

Android에서 이 모델을 사용하려면 `.pt` 형식을 그대로 사용할 수 없으므로 TFLite 또는 ONNX로 변환해야 한다.

## 3. TFLite Export 계획

TFLite는 Android 온디바이스 추론에 가장 일반적으로 사용되는 모델 형식이다. TensorFlow Lite Interpreter를 사용하면 Android 앱 내부에서 모델을 실행할 수 있다.

예상 export 명령:

```bash
yolo export model=models/yolo26_face.pt format=tflite
```

또는 Python 코드로 export할 수 있다.

```python
from ultralytics import YOLO

model = YOLO("models/yolo26_face.pt")
model.export(format="tflite")
```

export 결과는 일반적으로 다음과 같은 파일로 생성된다.

```text
models/yolo26_face_saved_model/
models/yolo26_face_float32.tflite
```

실제 파일명은 Ultralytics 버전과 export 옵션에 따라 달라질 수 있으므로 export 후 생성 파일을 확인해야 한다.

## 4. ONNX Export 계획

ONNX는 다양한 추론 런타임에서 사용할 수 있는 범용 모델 형식이다. Android에서는 ONNX Runtime Mobile을 사용해 실행할 수 있다.

예상 export 명령:

```bash
yolo export model=models/yolo26_face.pt format=onnx
```

또는 Python 코드:

```python
from ultralytics import YOLO

model = YOLO("models/yolo26_face.pt")
model.export(format="onnx")
```

예상 결과:

```text
models/yolo26_face.onnx
```

ONNX는 서버, 데스크톱, 모바일 등 여러 환경에서 사용할 수 있어 테스트와 배포 방식이 비교적 유연하다.

## 5. Export 전 확인 사항

모델 변환 전 다음 항목을 확인해야 한다.

- `models/yolo26_face.pt` 파일이 실제로 존재하는지 확인
- 학습된 클래스가 얼굴 검출용인지 확인
- 입력 이미지 크기 확인
- confidence threshold 기준 확인
- export 후 Python 또는 Android에서 동일 이미지에 대해 검출 결과 비교
- 변환된 모델의 box 좌표 후처리 방식 확인

특히 YOLO 계열 모델은 출력 tensor 후처리가 중요하다. Android에서 직접 추론할 경우 모델 출력값을 box 좌표, confidence, class로 변환하는 후처리 코드를 구현해야 한다.

## 6. Android TFLite 적용 구조

TFLite 방식의 처리 구조는 다음과 같다.

```text
Android 앱
  - 카메라 프레임 획득
  - Bitmap 전처리
  - TFLite Interpreter 실행
  - YOLO 출력 후처리
  - 얼굴 box 추출
  - 얼굴 crop 생성
  - crop 이미지를 Python 서버로 전송
        |
        v
Python 서버
  - 얼굴 인식
  - 학생 조회
  - 출석 처리
  - JSON 응답
```

이 방식에서는 얼굴 검출을 Android에서 처리하고, 얼굴 인식과 출석 DB 처리는 서버에서 처리할 수 있다. 서버 부하를 줄일 수 있지만 Android 앱 구현 난이도는 높아진다.

## 7. Android ONNX 적용 구조

ONNX 방식은 Android 앱에 ONNX Runtime Mobile을 포함해 모델을 실행한다.

```text
Android 앱
  - 이미지 전처리
  - ONNX Runtime Mobile 실행
  - YOLO 출력 후처리
  - 얼굴 box 추출
  - crop 이미지를 서버로 전송하거나 앱 내부 처리
```

ONNX는 Python에서도 같은 모델을 테스트하기 쉬워 서버와 모바일 결과 비교에 유리하다. 다만 Android 앱에 ONNX Runtime 의존성을 추가해야 하며, 앱 용량이 증가할 수 있다.

## 8. 서버 추론 방식

서버 추론 방식은 Android 앱이 원본 이미지 또는 리사이즈된 이미지를 Python 서버로 보내고, 서버가 모든 추론과 DB 처리를 수행한다.

```text
Android 앱
  - 이미지 촬영 또는 선택
  - 서버로 이미지 전송

Python 서버
  - YOLO26 얼굴 검출
  - 얼굴 crop
  - 얼굴 인식
  - 출석 처리
  - JSON 응답
```

이 방식은 현재 Python 코드 구조를 가장 적게 변경하면서 Android 연동을 구현할 수 있다.

## 9. 온디바이스 추론 방식과 서버 추론 방식 비교

| 구분 | 온디바이스 추론 | 서버 추론 |
| --- | --- | --- |
| 실행 위치 | Android 기기 내부 | Python 서버 |
| 네트워크 의존성 | 낮음 | 높음 |
| 앱 구현 난이도 | 높음 | 낮음 |
| 서버 부하 | 낮음 | 높음 |
| 모델 업데이트 | 앱 업데이트 필요 가능성 높음 | 서버 모델만 교체 가능 |
| 개인정보 측면 | 원본 이미지 외부 전송 감소 가능 | 이미지가 서버로 전송됨 |
| 성능 | 기기 성능에 따라 달라짐 | 서버 성능에 따라 안정적 |
| 후처리 구현 | Android에서 필요 | Python 기존 코드 재사용 |
| 학교 프로젝트 MVP 적합성 | 중간 | 높음 |

## 10. 장단점 정리

### 10.1 온디바이스 추론 장점

- 네트워크가 불안정해도 얼굴 검출 가능
- 서버로 전송하는 이미지 범위를 줄일 수 있음
- 서버 부하를 줄일 수 있음
- 실시간 카메라 프레임 처리에 유리할 수 있음

### 10.2 온디바이스 추론 단점

- TFLite 또는 ONNX 모델 변환이 필요함
- Android에서 YOLO 후처리 코드를 구현해야 함
- 기기별 성능 차이가 큼
- 모델 교체 시 앱 업데이트가 필요할 수 있음
- 얼굴 인식까지 온디바이스로 처리하려면 추가 모델과 보안 설계가 필요함

### 10.3 서버 추론 장점

- 현재 Python 코드 재사용 가능
- 모델 교체와 threshold 조정이 쉬움
- Android 앱 구현이 단순함
- 서버에서 DB와 출석 기록을 일관되게 관리할 수 있음
- 초기 MVP 개발 속도가 빠름

### 10.4 서버 추론 단점

- 네트워크 연결이 필요함
- 서버 부하가 커질 수 있음
- 이미지가 서버로 전송되므로 개인정보 보호 정책이 필요함
- 동시에 많은 사용자가 접속하면 서버 성능 문제가 생길 수 있음

## 11. 권장 구현 방향

학교 프로젝트 MVP 단계에서는 서버 추론 방식을 우선 적용하는 것을 권장한다.

이유는 다음과 같다.

- 현재 Python 모듈을 그대로 활용할 수 있다.
- Android 앱은 이미지 전송과 결과 표시 중심으로 단순하게 구현할 수 있다.
- 얼굴 검출, 얼굴 인식, 출석 처리 로직을 서버에서 통합 관리할 수 있다.
- 모델 성능 개선이나 threshold 조정을 앱 업데이트 없이 서버에서 처리할 수 있다.

추후 고도화 단계에서는 다음과 같이 확장할 수 있다.

1. 1차 MVP: Android 앱이 이미지를 서버로 전송하고 서버가 전체 처리
2. 2차 개선: Android에서 TFLite 또는 ONNX로 얼굴 검출만 수행
3. 3차 개선: 얼굴 crop만 서버로 전송해 네트워크 사용량 감소
4. 4차 고도화: 온디바이스 얼굴 인식 모델 적용 검토

## 12. Export 후 검증 계획

모델 export 후 다음 절차로 검증한다.

1. Python에서 `.pt` 모델로 동일 이미지 얼굴 검출 결과 저장
2. export된 TFLite 또는 ONNX 모델로 동일 이미지 추론
3. 얼굴 box 좌표와 confidence 비교
4. Android 앱에서 같은 이미지로 추론 결과 확인
5. 검출된 box 기준 crop 이미지가 Python 서버 인식 결과와 일치하는지 확인
6. 실제 웹캠 또는 Android 카메라 촬영 이미지로 반복 테스트

검증 기준은 다음과 같다.

- 얼굴 1명이 포함된 이미지에서 얼굴 box가 안정적으로 검출되는가
- 얼굴이 없는 이미지에서 오검출이 과도하지 않은가
- 여러 명이 있는 이미지에서 다중 얼굴을 올바르게 검출하는가
- Android에서 반환한 crop이 Python 서버 인식 모듈에서 정상 처리되는가

## 13. 결론

Android 연동 초기 단계에서는 서버 추론 방식을 적용하는 것이 가장 현실적이다. 현재 Python 프로젝트의 `FaceDetector`, `FaceRecognizer`, `attendance_service`를 FastAPI 서버로 감싸면 Android 앱은 이미지를 전송하고 JSON 결과를 표시하는 구조만 구현하면 된다.

이후 모델 최적화가 필요하거나 네트워크 의존도를 줄여야 하는 경우 YOLO26 모델을 TFLite 또는 ONNX로 export하여 Android 온디바이스 얼굴 검출을 적용한다. 이때 Android 앱에는 모델 입력 전처리, 추론 실행, YOLO 후처리, box 좌표 변환 로직이 추가로 필요하다.
