# 프로젝트 아키텍처 및 구현 명세

작성 기준: 2026-06-14 현재 코드 구조

## 1. 시스템 개요

이 프로젝트는 강의실과 수업 세션을 기준으로 얼굴 인식 출석을 처리하는 Android + FastAPI 시스템이다.

- Android 앱은 Kotlin + XML View 기반이다.
- Android 앱은 YOLO26/FaceNet을 직접 실행하지 않는다.
- Android 앱은 CameraX로 이미지를 캡처하고 FastAPI 서버에 전송한다.
- FastAPI 서버는 YOLO26으로 얼굴 위치를 검출하고 FaceNet으로 학생을 식별한다.
- 출석 기준은 선택 강의실, 현재 날짜, 현재 시간, 수업 세션 ID다.
- 현재 수업은 DB의 수동 `is_active`가 아니라 시간표 기반으로 자동 판별한다.

## 2. 전체 디렉터리 구조

```text
attendance_project/
├─ README.md
├─ attendance_project_BE/
│  ├─ api/
│  │  └─ server.py
│  ├─ modules/
│  │  ├─ attendance_service.py
│  │  ├─ attendance_state.py
│  │  ├─ classroom_service.py
│  │  ├─ database.py
│  │  ├─ detector.py
│  │  ├─ enrollment_service.py
│  │  ├─ export_service.py
│  │  ├─ face_pipeline.py
│  │  ├─ recognizer.py
│  │  ├─ student_registration_state.py
│  │  ├─ student_service.py
│  │  └─ subject_service.py
│  ├─ data/
│  │  ├─ students.db
│  │  ├─ exports/
│  │  ├─ known_faces/
│  │  └─ logs/
│  ├─ datasets/face_dataset/
│  ├─ models/yolo26_face.pt
│  ├─ docs/
│  ├─ config.py
│  ├─ requirements.txt
│  └─ run.bat
└─ attendance_yolo26_Front/
   ├─ app/src/main/java/kr/ac/yonam/attendance/
   │  ├─ model/
   │  ├─ network/
   │  ├─ repository/
   │  ├─ ui/
   │  └─ util/
   └─ app/src/main/res/
      ├─ layout/
      └─ values/
```

## 3. 백엔드 아키텍처

### API 계층

`api/server.py`가 FastAPI 앱의 엔트리포인트다.

주요 책임:

- HTTP API 정의
- multipart 이미지 업로드 처리
- YOLO/FaceNet 객체 lazy loading
- 얼굴 등록/출석 인식 요청 처리
- `/sessions/current` 현재 수업 조회 제공
- 서버 startup/shutdown 시 자동 결석 처리 백그라운드 task 관리

### 서비스 계층

| 파일 | 책임 |
| --- | --- |
| `modules/database.py` | SQLite 연결, 테이블 생성, 기존 스키마 마이그레이션 |
| `modules/classroom_service.py` | 강의실 생성, 조회, 수정, 비활성화, 같은 이름 재활성화 |
| `modules/subject_service.py` | 과목 생성/수정/삭제, 수강생 연결, 과목별 세션 조회 |
| `modules/attendance_service.py` | 세션 생성/삭제, 현재 세션 조회, 출석 기록, 세션별 출석 상태 관리, 자동 결석 처리 |
| `modules/student_service.py` | 학생 등록, 삭제/비활성화, 같은 학번 재등록 시 재활성화 |
| `modules/detector.py` | YOLO26 얼굴 검출, YOLO 실패 시 Haar fallback |
| `modules/face_pipeline.py` | 이미지 디코딩, 단일 얼굴 검증, 얼굴 crop/padding |
| `modules/recognizer.py` | FaceNet 임베딩 생성과 등록 학생 비교 |
| `modules/attendance_state.py` | 출석 3초 유지 상태 추적 |
| `modules/enrollment_service.py` | 정면/좌/우/위/아래 얼굴 등록 세션 관리 |

### DB 테이블 개념

주요 테이블:

- `students`: 학생, 학번, 이름, 학과, 얼굴 임베딩, 활성 상태
- `classrooms`: 강의실명, 건물명, 층, 설명, 활성 상태
- `subjects`: 과목명, 담당 교수, 강의실, 요일, 시작/종료 시간
- `subject_students`: 과목-학생 수강 연결
- `attendance_sessions`: 과목별 수업 날짜와 시간
- `attendance`: 세션별 학생 출석 상태

삭제 정책:

- 출석 기록이 있는 학생은 실제 삭제보다 `is_active=0` 비활성화한다.
- 같은 학번을 다시 등록하면 기존 비활성 학생 row를 재활성화한다.
- 강의실 삭제도 비활성화 방식이며 같은 이름 재등록 시 기존 row를 재활성화한다.
- 출석 기록이 있는 세션 삭제는 데이터 보존을 위해 차단한다.

## 4. Android 아키텍처

### 계층 구조

| 패키지 | 책임 |
| --- | --- |
| `model` | Retrofit/Gson 응답 및 요청 모델 |
| `network` | `AttendanceApi`, `ApiClient` |
| `repository` | 서버 응답/error body 처리, UI에 안정적인 response 전달 |
| `ui` | Activity, Dialog, RecyclerView Adapter, CameraX 화면 |
| `util` | 서버 주소, 강의실 선택값, 이미지 변환 유틸 |

### 주요 화면

| 파일 | 역할 |
| --- | --- |
| `ClassroomSelectActivity.kt` | 앱 시작 화면, 강의실 목록 조회/선택, 선택값 저장 |
| `RoleSelectActivity.kt` | 학생 접속 화면 진입 |
| `StudentMainActivity.kt` | 선택 강의실 기준 현재 수업 표시, 출석하기 진입 |
| `AttendanceCameraActivity.kt` | CameraX 프리뷰, 주기적 이미지 전송, 출석 상태 표시 |
| `AdminMainActivity.kt` | 관리자 기능 진입: 강의실/과목/학생/서버 설정 |
| `ClassroomManageActivity.kt` | 강의실 생성/수정/비활성화 |
| `SubjectListActivity.kt` | 과목 목록/추가/삭제 진입 |
| `SubjectDetailActivity.kt` | 세션 탭, 수강 학생 탭, 세션 삭제, 출석 관리 모달 |
| `RegisterStudentActivity.kt` | 학생 목록, 신규 등록, 얼굴 재등록, 학생 삭제 |
| `SessionAttendanceDialog.kt` | 세션별 수강생 출석 상태 변경 |

### Android 저장값

`ClassroomConfig.kt`는 `SharedPreferences`에 선택 강의실을 저장한다.

- `selected_classroom_id`
- `selected_classroom_name`

강의실 선택 화면은 서버에서 받은 활성 강의실 목록 기준으로 저장된 ID를 검증한다. 목록에 없으면 선택값을 제거하고 “선택된 강의실이 없습니다” 상태로 표시한다.

## 5. 핵심 사용자 흐름

### 5.1 강의실 선택

1. 앱 시작
2. `ClassroomSelectActivity`
3. `GET /classrooms`
4. 사용자가 강의실 선택
5. `ClassroomConfig`에 ID/이름 저장
6. 학생 접속 화면으로 이동

강의실 선택 화면으로 돌아오면 어댑터의 selected id를 다시 계산해 이전 강의실의 “선택됨” 표시가 남지 않게 한다.

### 5.2 현재 수업 조회

Android:

1. `StudentMainActivity`가 선택된 `classroom_id`를 읽는다.
2. `GET /sessions/current?classroom_id=...` 호출
3. 현재 수업이 있으면 과목명, 날짜, 시작/종료 시간 표시
4. `session_id`를 출석하기 화면에 전달

서버 현재 수업 조건:

```text
class_date == 서버 로컬 오늘 날짜
start_time - 20분 <= 현재 시간 <= end_time
classroom_id 또는 classroom_name 일치
```

`is_active=true` 응답은 “현재 시간 기준 출석 가능” 표시다. DB 수동 활성 체크박스 의미로 사용하지 않는다.

### 5.3 출석 인식

1. `AttendanceCameraActivity`가 CameraX 프레임을 JPEG로 변환
2. `/attendance/recognize`에 `image`와 `session_id` 전송
3. 서버가 YOLO26으로 얼굴 검출
4. YOLO 결과가 없으면 Haar fallback 시도
5. FaceNet으로 등록 학생과 거리 비교
6. 같은 학생이 같은 세션에서 3초 동안 유지되면 출석 처리
7. `attendance` 테이블에 `present` 저장

주요 상태:

- `no_face`: 얼굴 검출 실패
- `multiple_faces`: 여러 얼굴 검출
- `unknown`: FaceNet 식별 실패
- `ambiguous_face`: 1위/2위 후보 간 거리 차가 작음
- `recognizing`: 3초 유지 중
- `attended`: 출석 완료
- `already_attended`: 이미 출석됨

디버그 로그:

```text
attendance_project_BE/data/logs/attendance_recognition_debug.jsonl
```

로그에는 `final_status`, `detection_status`, `detector_source`, `distance`, `threshold` 등이 기록된다.

### 5.4 학생 얼굴 등록/재등록

1. 관리자 페이지 > 학생 관리
2. 기존 학생 목록 표시
3. 신규 등록 또는 기존 학생 선택 후 얼굴 재등록
4. 등록 포즈: `front`, `left`, `right`, `up`, `down`
5. 서버가 포즈별 프레임을 받고 임베딩 생성
6. 완료 시 학생 row 생성 또는 기존 row 업데이트

학생 식별용 얼굴 데이터는 앱 등록 흐름으로 DB에 저장한다. 파일 시스템에 수동으로 넣지 않는다.

### 5.5 과목/세션/수강생 관리

과목 생성 시 저장되는 주요 정보:

- 과목명
- 담당 교수
- 강의실
- 요일
- 시작 시간
- 종료 시간

과목 상세 화면:

- `수업 세션` 탭: 날짜별 세션 목록, 세션 삭제, 세션 클릭 시 출석 관리 모달
- `수강 학생` 탭: 수강생 목록, 다중 체크박스 학생 추가, 학생 제거

세션 출석 관리:

- `GET /sessions/{session_id}/attendance-students`
- 수강생 전체를 반환한다.
- attendance row가 없으면 `pending`으로 표시한다.
- `PUT /sessions/{session_id}/attendance-students/{student_id}`로 `present`, `late`, `absent`, `pending` 변경
- `pending`은 attendance row 삭제로 표현한다.

### 5.6 자동 결석 처리

FastAPI startup 시 백그라운드 task가 시작된다.

- 주기: `ABSENCE_FINALIZER_INTERVAL_SECONDS = 60`
- 대상: 종료 시간이 지난 `subject_id`가 있는 세션
- 처리: 수강생 중 attendance row가 없는 학생만 `absent` insert
- 이미 출석/지각/결석/미출석 변경 row가 있는 학생은 건드리지 않는다.
- 중복 실행 방지를 위해 `INSERT OR IGNORE`를 사용한다.

## 6. 주요 API 명세 요약

### 강의실

- `GET /classrooms`
- `POST /classrooms`
- `GET /classrooms/{classroom_id}`
- `PUT /classrooms/{classroom_id}`
- `DELETE /classrooms/{classroom_id}`

### 현재 수업/세션

- `GET /sessions/current?classroom_id=1`
- `GET /sessions`
- `DELETE /sessions/{session_id}`
- `GET /sessions/{session_id}/attendance-students`
- `PUT /sessions/{session_id}/attendance-students/{student_id}`

### 과목

- `GET /subjects`
- `POST /subjects`
- `GET /subjects/{subject_id}`
- `PUT /subjects/{subject_id}`
- `DELETE /subjects/{subject_id}`
- `GET /subjects/{subject_id}/students`
- `POST /subjects/{subject_id}/students/{student_id}`
- `DELETE /subjects/{subject_id}/students/{student_id}`
- `GET /subjects/{subject_id}/sessions`
- `POST /subjects/{subject_id}/sessions`

### 학생/등록

- `GET /students`
- `POST /students`
- `DELETE /students/{student_id}`
- `GET /students/{student_id}/stats`
- `POST /students/enroll/start`
- `POST /students/enroll/frame`
- `POST /students/enroll/complete`
- `GET /students/enroll/{enroll_id}/status`
- `DELETE /students/enroll/{enroll_id}`

### 출석

- `POST /attendance/recognize`
- `GET /attendance`
- `GET /attendance/export`

## 7. 모델과 데이터

### YOLO 얼굴 검출 모델

```text
attendance_project_BE/models/yolo26_face.pt
```

설정:

```text
YOLO_MODEL_PATH = models/yolo26_face.pt
FACE_DETECTION_CONFIDENCE = 0.5
ATTENDANCE_FACE_DETECTION_CONFIDENCE = 0.25
ENROLL_FACE_DETECTION_CONFIDENCE = 0.35
```

### YOLO 재학습 데이터

```text
attendance_project_BE/datasets/face_dataset/images/train
attendance_project_BE/datasets/face_dataset/images/val
attendance_project_BE/datasets/face_dataset/labels/train
attendance_project_BE/datasets/face_dataset/labels/val
attendance_project_BE/datasets/face_dataset/data.yaml
```

라벨:

```text
0 x_center y_center width height
```

필요한 데이터는 특정 학생 식별용이 아니라 `face` 단일 클래스 얼굴 bounding box 데이터다. 정면, 좌우, 상하, 거리 변화, 조명 변화, 실제 출석 카메라 환경을 포함해야 한다.

### FaceNet 학생 식별 데이터

학생 식별 임베딩은 DB에 저장된다. 신규 등록/재등록은 Android 앱의 학생 관리 화면을 사용한다.

## 8. 실행 및 검증

백엔드 실행:

```powershell
cd C:\Project\attendance_project\attendance_project_BE
venv\Scripts\python.exe -m uvicorn api.server:app --host 0.0.0.0 --port 8000 --reload
```

백엔드 검증:

```powershell
cd C:\Project\attendance_project\attendance_project_BE
python -m compileall .
```

Android 빌드:

```powershell
cd C:\Project\attendance_project\attendance_yolo26_Front
.\gradlew.bat build
```

수동 테스트 체크리스트:

- 강의실 선택 후 뒤로가기/재선택 시 선택 표시가 최신 상태인지 확인
- 현재 시간보다 20분 이내 시작하는 세션이 현재 수업으로 잡히는지 확인
- 세션 종료 후 미출석 수강생이 `absent`로 자동 생성되는지 확인
- 출석 화면에서 `session_id`가 포함되어 `/attendance/recognize`가 호출되는지 확인
- 얼굴 인식 실패 시 로그의 `final_status`와 `detector_source`를 확인
- 과목 상세에서 세션 탭/수강 학생 탭/다중 학생 추가/세션별 출석 상태 변경 확인

## 9. 운영상 주의사항

- FastAPI `--reload` 또는 다중 worker 환경에서는 백그라운드 결석 task가 중복 실행될 수 있다. 현재 DB unique와 `INSERT OR IGNORE`로 중복 row는 방어한다.
- SQLite 단일 파일 DB 구조라 동시 쓰기 부하가 커지면 별도 DBMS 전환을 검토해야 한다.
- 얼굴 인식 로그는 이미지를 저장하지 않고 상태/수치만 저장한다.
- Android 서버 주소는 `ServerSettingActivity`와 `ServerConfig`에서 관리한다.
- HTTP 통신을 사용하므로 Android cleartext 설정이 필요하다.
