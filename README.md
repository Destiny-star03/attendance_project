# Attendance Project

YOLO26 얼굴 검출, FaceNet 얼굴 식별, FastAPI 백엔드, Android Kotlin/XML View 앱으로 구성된 강의실 기반 얼굴 인식 출석 시스템입니다.

## 현재 구조

```text
C:\Project\attendance_project
├─ attendance_project_BE/          # FastAPI, SQLite, YOLO/FaceNet 서버
│  ├─ api/server.py                # HTTP API, 출석 인식, 백그라운드 결석 처리
│  ├─ modules/                     # DB, 과목, 강의실, 학생, 출석, 얼굴 처리 서비스
│  ├─ data/students.db             # SQLite 운영 DB
│  ├─ data/logs/                   # 출석 인식 디버그 로그
│  ├─ datasets/face_dataset/       # YOLO 얼굴 검출 재학습 데이터
│  ├─ models/yolo26_face.pt        # 얼굴 검출 모델
│  ├─ docs/                        # 백엔드/전체 프로젝트 명세 문서
│  └─ run.bat                      # 서버 실행 배치 파일
└─ attendance_yolo26_Front/        # Android Studio Kotlin + XML View 앱
   ├─ app/src/main/java/.../model
   ├─ app/src/main/java/.../network
   ├─ app/src/main/java/.../repository
   ├─ app/src/main/java/.../ui
   ├─ app/src/main/java/.../util
   └─ app/src/main/res/layout
```

상세 아키텍처와 파일별 역할은 [project_architecture_spec.md](attendance_project_BE/docs/project_architecture_spec.md)를 기준으로 관리합니다.

## 핵심 동작

- 앱 시작 시 `ClassroomSelectActivity`에서 강의실을 선택하고 `SharedPreferences`에 저장합니다.
- 학생 화면은 선택 강의실 기준 `GET /sessions/current?classroom_id=...`를 주기적으로 조회합니다.
- 현재 수업은 서버 로컬 날짜와 시간표 기준으로 판별합니다. 현재 기준은 `수업 시작 20분 전 <= now <= 종료 시간`입니다.
- 출석하기 화면은 현재 `session_id`가 있을 때만 `/attendance/recognize`에 이미지를 전송합니다.
- Android 앱은 YOLO26/FaceNet을 직접 실행하지 않습니다. 카메라 프레임을 서버로 보내고 결과만 표시합니다.
- 서버는 YOLO26으로 얼굴 위치를 검출하고, FaceNet으로 등록 학생과 비교합니다.
- 얼굴 인식 출석은 같은 학생이 같은 세션에서 3초 동안 안정적으로 인식되면 출석 처리합니다.
- 서버 백그라운드 작업은 종료된 세션의 미출석 수강생을 `absent`로 자동 확정합니다.
- 관리자 페이지는 강의실 관리, 과목 관리, 학생 관리, 서버 설정 기능을 제공합니다.

## 실행

백엔드:

```powershell
cd C:\Project\attendance_project\attendance_project_BE
venv\Scripts\python.exe -m uvicorn api.server:app --host 0.0.0.0 --port 8000 --reload
```

또는:

```powershell
cd C:\Project\attendance_project\attendance_project_BE
.\run.bat
```

Swagger:

```text
http://127.0.0.1:8000/docs
```

Android 빌드:

```powershell
cd C:\Project\attendance_project\attendance_yolo26_Front
.\gradlew.bat build
```

## 주요 API

- `GET /classrooms`
- `POST /classrooms`
- `DELETE /classrooms/{classroom_id}`
- `GET /sessions/current?classroom_id=1`
- `POST /subjects`
- `GET /subjects/{subject_id}/sessions`
- `POST /subjects/{subject_id}/sessions`
- `DELETE /sessions/{session_id}`
- `GET /sessions/{session_id}/attendance-students`
- `PUT /sessions/{session_id}/attendance-students/{student_id}`
- `GET /students`
- `DELETE /students/{student_id}`
- `POST /students/enroll/start`
- `POST /students/enroll/frame`
- `POST /students/enroll/complete`
- `POST /attendance/recognize`

## 얼굴 데이터 위치

YOLO 얼굴 검출 재학습 데이터:

```text
attendance_project_BE/datasets/face_dataset/images/train
attendance_project_BE/datasets/face_dataset/images/val
attendance_project_BE/datasets/face_dataset/labels/train
attendance_project_BE/datasets/face_dataset/labels/val
```

라벨은 YOLO 형식입니다.

```text
0 x_center y_center width height
```

학생 식별용 얼굴 데이터는 파일을 직접 넣지 않고 Android 앱의 `학생 관리 > 얼굴 재등록` 흐름으로 등록합니다.

## 검증 명령

백엔드:

```powershell
cd C:\Project\attendance_project\attendance_project_BE
python -m compileall .
```

Android:

```powershell
cd C:\Project\attendance_project\attendance_yolo26_Front
.\gradlew.bat build
```
