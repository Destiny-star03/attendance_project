# YOLO26 + FaceNet 출석 관리 MVP

Python, OpenCV, Ultralytics YOLO, FaceNet, FastAPI, SQLite를 사용하는 얼굴 인식 출석 관리 프로젝트입니다.

## 역할 분리

- YOLO26: 얼굴 위치 검출만 담당합니다.
- FaceNet: YOLO가 crop한 얼굴 이미지에서 512차원 임베딩을 만들고 학생을 식별합니다.
- FastAPI: Android 앱과 통신하는 서버입니다.
- Android 앱: 사진 촬영/선택, 서버 전송, 결과 표시만 담당합니다.
- `main.py`: PC 웹캠 데모용입니다. Android 서버로 사용하지 않습니다.

## 설치

```powershell
python -m venv venv
venv\Scripts\activate
python -m pip install -r requirements.txt
python -m pip install facenet-pytorch --no-deps
```

Python 3.13에서는 `facenet-pytorch`의 의존성 버전 제한이 최신 `torch`, `numpy`, `pillow`와 충돌할 수 있어 `--no-deps` 설치를 사용합니다.

## 모델 파일

YOLO 얼굴 검출 모델은 아래 경로에 있어야 합니다.

```text
models/yolo26_face.pt
```

모델 경로는 `config.py`의 `YOLO_MODEL_PATH`에서만 관리합니다.
현재 학습 산출물을 사용할 경우 `runs/detect/runs/train/face_yolo_30ep/weights/best.pt`를 위 경로로 복사합니다.
루트의 `yolo11n.pt`는 학습 시작점/base weight이며 런타임 얼굴 검출 모델로 사용하지 않습니다.

## DB 초기화

```powershell
python -m modules.database
```

FaceNet 전환 후 기존 OpenCV 임베딩은 호환되지 않습니다. 기존 DB는 백업 후 새로 시작하고 학생을 다시 등록해야 합니다.

## PC 웹캠 데모 실행

```powershell
python main.py
```

카메라 번호를 바꾸려면:

```powershell
python main.py --camera 1
```

## 콘솔 학생 등록

```powershell
python -m admin.register_student
```

얼굴 사진 경로에는 이미지 1개 또는 여러 이미지가 들어 있는 폴더를 입력할 수 있습니다.

## FastAPI 서버 실행

Android 연동용 서버는 `api/server.py`입니다.

```powershell
uvicorn api.server:app --host 0.0.0.0 --port 8000 --reload
```

확인:

```text
http://127.0.0.1:8000/health
http://127.0.0.1:8000/docs
```

## API

- `GET /health`
- `POST /students`
  - multipart/form-data
  - `student_no`, `name`, optional `department`, `image`
- `POST /attendance/recognize`
  - multipart/form-data
  - `image`
- `GET /attendance/today`
- `GET /attendance?date=YYYY-MM-DD`
- `GET /attendance/export?date=YYYY-MM-DD`

## Android 접속 주소

Android 에뮬레이터에서 PC 서버 접근:

```text
http://10.0.2.2:8000
```

실제 스마트폰 테스트:

```text
http://PC_IP:8000
```

예:

```text
http://192.168.0.xx:8000
```

Android 앱에는 `INTERNET` 권한이 필요합니다. HTTP 통신을 사용하면 cleartext traffic 허용 또는 `networkSecurityConfig` 설정이 필요합니다.

## CSV 내보내기

```powershell
python -m admin.export_attendance
```

CSV는 `data/exports/attendance_YYYY-MM-DD.csv`에 `utf-8-sig` 인코딩으로 저장됩니다.
