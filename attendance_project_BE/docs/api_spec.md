# API 명세서

작성일: 2026-06-02

대상 서버: `api.server:app`

기본 URL:

```text
http://127.0.0.1:8000
```

서버 실행 예:

```powershell
venv\Scripts\python.exe -m uvicorn api.server:app --host 0.0.0.0 --port 8000 --reload
```

공통 특성:

- 응답은 대부분 HTTP 200으로 반환되며, 실제 성공 여부는 JSON의 `success` 필드로 판단한다.
- CORS는 전체 origin/method/header 허용 상태다.
- 이미지 업로드 API는 `multipart/form-data`를 사용한다.
- 날짜 문자열은 `YYYY-MM-DD`, 시간 문자열은 `HH:MM` 또는 `HH:MM:SS` 형식을 사용한다.

## 1. 헬스 체크

### `GET /health`

서버 구동 상태를 확인한다.

응답:

```json
{
  "status": "ok"
}
```

## 2. 출석 세션

출석 세션은 과목/수업 일자/수업 시간 단위의 출석 기준이다. 새 세션을 만들거나 특정 세션을 활성화하면 기존 활성 세션은 비활성화된다.

### `POST /sessions`

출석 세션을 생성하고 활성 세션으로 지정한다.

Content-Type: `application/json`

요청:

```json
{
  "subject_name": "데이터베이스",
  "class_date": "2026-05-18",
  "start_time": "09:00",
  "end_time": "10:50"
}
```

요청 필드:

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `subject_name` | string | Y | 과목명 |
| `class_date` | string | Y | 수업 일자, `YYYY-MM-DD` |
| `start_time` | string/null | N | 시작 시간 |
| `end_time` | string/null | N | 종료 시간 |

성공 응답:

```json
{
  "success": true,
  "message": "출석 세션이 생성되었습니다.",
  "session": {
    "session_id": 1,
    "subject_name": "데이터베이스",
    "class_date": "2026-05-18",
    "start_time": "09:00",
    "end_time": "10:50",
    "is_active": true
  }
}
```

### `GET /sessions`

전체 출석 세션 목록을 조회한다.

응답:

```json
{
  "success": true,
  "message": "출석 세션 목록 조회 성공",
  "items": [
    {
      "session_id": 1,
      "subject_name": "데이터베이스",
      "class_date": "2026-05-18",
      "start_time": "09:00",
      "end_time": "10:50",
      "is_active": true
    }
  ]
}
```

### `GET /sessions/active`

현재 활성 출석 세션을 조회한다.

성공 응답:

```json
{
  "success": true,
  "message": "활성 출석 세션 조회 성공",
  "session": {
    "session_id": 1,
    "subject_name": "데이터베이스",
    "class_date": "2026-05-18",
    "start_time": "09:00",
    "end_time": "10:50",
    "is_active": true
  }
}
```

활성 세션 없음:

```json
{
  "success": false,
  "message": "활성 출석 세션이 없습니다.",
  "session": null
}
```

### `POST /sessions/{session_id}/activate`

특정 세션을 활성화한다.

Path:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `session_id` | integer | 활성화할 출석 세션 ID |

응답:

```json
{
  "success": true,
  "message": "출석 세션이 활성화되었습니다.",
  "session": {
    "session_id": 1,
    "subject_name": "데이터베이스",
    "class_date": "2026-05-18",
    "start_time": "09:00",
    "end_time": "10:50",
    "is_active": true
  }
}
```

### `POST /sessions/{session_id}/close`

특정 세션을 비활성화한다.

Path:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `session_id` | integer | 종료할 출석 세션 ID |

응답:

```json
{
  "success": true,
  "message": "출석 세션이 종료되었습니다.",
  "session": {
    "session_id": 1,
    "subject_name": "데이터베이스",
    "class_date": "2026-05-18",
    "start_time": "09:00",
    "end_time": "10:50",
    "is_active": false
  }
}
```

## 3. 학생 등록

### `POST /students`

학생 정보와 얼굴 이미지를 등록한다. 서버는 이미지에서 얼굴 1개를 검출하고 FaceNet 임베딩을 생성해 DB에 저장한다.

Content-Type: `multipart/form-data`

요청 필드:

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `student_no` | string | Y | 학번. 중복 불가 |
| `name` | string | Y | 학생 이름 |
| `department` | string | N | 학과 |
| `image` | file | Y | 얼굴 이미지. 얼굴은 정확히 1명이어야 함 |

성공 응답:

```json
{
  "success": true,
  "message": "학생 등록이 완료되었습니다.",
  "student": {
    "student_id": 1,
    "student_no": "22260065",
    "name": "홍길동",
    "department": "스마트소프트웨어학과"
  }
}
```

주요 실패 응답:

```json
{
  "success": false,
  "matched": false,
  "status": "no_face",
  "message": "얼굴을 찾지 못했습니다."
}
```

```json
{
  "success": false,
  "matched": false,
  "status": "multiple_faces",
  "message": "한 명의 얼굴만 촬영해 주세요."
}
```

```json
{
  "success": false,
  "message": "이미 등록된 학번입니다."
}
```

## 4. 얼굴 인식 출석

### `POST /attendance/recognize`

업로드된 이미지에서 얼굴을 검출하고 등록 학생과 매칭한 뒤 출석을 처리한다. 같은 `session_id + student_id` 조합은 1회만 저장된다.

Content-Type: `multipart/form-data`

요청 필드:

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `image` | file | Y | 얼굴 이미지 |
| `session_id` | integer | N | 출석 처리할 세션 ID. 없으면 활성 세션 사용 |

처리 규칙:

- 얼굴이 없거나 여러 명이면 추적 상태를 초기화하고 실패 응답을 반환한다.
- 등록되지 않은 얼굴이면 `unknown` 상태를 반환한다.
- 등록된 얼굴이지만 활성 세션이 없으면 `no_active_session` 상태를 반환한다.
- 같은 학생 얼굴이 같은 세션에서 `ATTENDANCE_HOLD_SECONDS` 동안 유지되어야 출석 처리한다. 현재 기본값은 3초다.
- `TRACKER_TIMEOUT_SECONDS` 동안 같은 얼굴 요청이 이어지지 않으면 추적 상태가 만료된다. 현재 기본값은 5초다.

인식 진행 중 응답:

```json
{
  "success": true,
  "matched": true,
  "status": "recognizing",
  "message": "인식 중",
  "hold_seconds": 3.0,
  "elapsed_seconds": 1.4,
  "remaining_seconds": 1.6,
  "session": {
    "session_id": 1,
    "subject_name": "데이터베이스",
    "class_date": "2026-05-18",
    "start_time": "09:00",
    "end_time": "10:50",
    "is_active": true
  },
  "student": {
    "student_id": 1,
    "student_no": "22260065",
    "name": "홍길동",
    "department": "스마트소프트웨어학과"
  },
  "recognition": {
    "distance": 0.064,
    "similarity": 0.998
  },
  "attendance": {
    "marked": false,
    "message": "미출석"
  }
}
```

출석 완료 응답:

```json
{
  "success": true,
  "matched": true,
  "status": "attended",
  "message": "출석 완료",
  "hold_seconds": 3.0,
  "elapsed_seconds": 3.1,
  "remaining_seconds": 0.0,
  "session": {
    "session_id": 1,
    "subject_name": "데이터베이스",
    "class_date": "2026-05-18"
  },
  "student": {
    "student_id": 1,
    "student_no": "22260065",
    "name": "홍길동",
    "department": "스마트소프트웨어학과"
  },
  "recognition": {
    "distance": 0.064,
    "similarity": 0.998
  },
  "attendance": {
    "marked": true,
    "message": "출석 완료"
  }
}
```

이미 출석한 경우:

```json
{
  "success": true,
  "matched": true,
  "status": "already_attended",
  "message": "이미 출석",
  "session": {
    "session_id": 1,
    "subject_name": "데이터베이스",
    "class_date": "2026-05-18"
  },
  "student": {
    "student_id": 1,
    "student_no": "22260065",
    "name": "홍길동",
    "department": "스마트소프트웨어학과"
  },
  "recognition": {
    "distance": 0.064,
    "similarity": 0.998
  },
  "attendance": {
    "marked": false,
    "message": "이미 출석"
  }
}
```

등록되지 않은 얼굴:

```json
{
  "success": true,
  "matched": false,
  "status": "unknown",
  "message": "미등록 사용자",
  "recognition": {
    "distance": 1.24,
    "similarity": 0.12
  }
}
```

활성 세션 없음:

```json
{
  "success": false,
  "matched": true,
  "status": "no_active_session",
  "message": "활성 출석 세션이 없습니다.",
  "student": {
    "student_id": 1,
    "student_no": "22260065",
    "name": "홍길동",
    "department": "스마트소프트웨어학과"
  },
  "recognition": {
    "distance": 0.064,
    "similarity": 0.998
  },
  "attendance": {
    "marked": false,
    "message": "출석 세션 없음"
  }
}
```

상태값:

| status | 의미 |
| --- | --- |
| `recognizing` | 얼굴은 등록 학생과 매칭됐지만 유지 시간이 부족해 아직 DB 저장 전 |
| `attended` | 출석 저장 완료 |
| `already_attended` | 같은 세션에서 이미 출석 처리됨 |
| `unknown` | 등록 학생과 매칭되지 않음 |
| `no_active_session` | 사용할 출석 세션이 없음 |
| `no_face` | 이미지에서 얼굴을 찾지 못함 |
| `multiple_faces` | 이미지에 얼굴이 2명 이상 검출됨 |
| `image_error` | 업로드 이미지를 읽거나 디코딩하지 못함 |
| `model_error` | YOLO 모델 파일 오류 |
| `runtime_error` | 모델 실행 오류 |
| `bad_request` | 요청값 오류 |
| `server_error` | 기타 서버 오류 |

## 5. 출석 조회

### `GET /attendance/today`

오늘 날짜 기준 출석 목록을 조회한다. 날짜는 서버 로컬 시간 기준이다.

응답:

```json
{
  "success": true,
  "message": "날짜별 출석 목록 조회 성공",
  "session": null,
  "date": "2026-06-02",
  "items": [
    {
      "attendance_id": 1,
      "session_id": 1,
      "subject_name": "데이터베이스",
      "class_date": "2026-05-18",
      "start_time": "09:00",
      "end_time": "10:50",
      "student_id": 1,
      "student_no": "22260065",
      "name": "홍길동",
      "department": "스마트소프트웨어학과",
      "attendance_date": "2026-06-02",
      "attendance_time": "09:12:30",
      "status": "present",
      "confidence": 0.91,
      "distance": 0.064
    }
  ]
}
```

### `GET /attendance`

출석 목록을 조회한다.

Query:

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `session_id` | integer | N | 특정 세션의 출석 목록 조회 |
| `date` | string | N | 특정 날짜의 전체 출석 목록 조회 |

조회 기준:

- `session_id`가 있으면 세션 기준 조회
- `session_id`가 없고 `date`가 있으면 날짜 기준 조회
- 둘 다 없으면 활성 세션 기준 조회

날짜 형식 오류:

```json
{
  "success": false,
  "message": "날짜 형식은 YYYY-MM-DD여야 합니다.",
  "session": null,
  "items": []
}
```

## 6. CSV 다운로드

### `GET /attendance/export`

출석 목록을 CSV 파일로 내려받는다.

Query:

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `session_id` | integer | N | 특정 세션 CSV 다운로드 |
| `date` | string | N | 특정 날짜 CSV 다운로드 |

파일명:

| 조건 | 파일명 |
| --- | --- |
| `session_id` 있음 | `attendance_session_{session_id}.csv` |
| `date` 있음 | `attendance_{date}.csv` |
| 활성 세션 기준 | `attendance_session_{active_session_id}.csv` |
| 기준 없음 | `attendance.csv` |

CSV 저장 위치: `data/exports`

CSV 인코딩: `utf-8-sig`

CSV 컬럼:

| 컬럼 | 설명 |
| --- | --- |
| 세션ID | 출석 세션 ID |
| 과목명 | 출석 세션 과목명 |
| 날짜 | 출석 일자 |
| 시간 | 출석 시간 |
| 학번 | 학생 학번 |
| 이름 | 학생 이름 |
| 학과 | 학생 학과 |
| 출석상태 | 현재는 `present` |
| 거리값 | FaceNet 임베딩 거리 |

## 7. 공통 응답 객체

### Session

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `session_id` | integer | 출석 세션 ID |
| `subject_name` | string | 과목명 |
| `class_date` | string | 수업 일자 |
| `start_time` | string/null | 시작 시간 |
| `end_time` | string/null | 종료 시간 |
| `is_active` | boolean/integer | 활성 여부 |

### Student

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `student_id` | integer | 학생 ID |
| `student_no` | string | 학번 |
| `name` | string | 이름 |
| `department` | string/null | 학과 |

### Recognition

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `distance` | number/null | 입력 얼굴과 가장 가까운 등록 얼굴의 유클리드 거리 |
| `similarity` | number/null | 코사인 유사도 |

### AttendanceRecord

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `attendance_id` | integer | 출석 기록 ID |
| `session_id` | integer | 출석 세션 ID |
| `subject_name` | string | 과목명 |
| `class_date` | string | 수업 일자 |
| `start_time` | string/null | 시작 시간 |
| `end_time` | string/null | 종료 시간 |
| `student_id` | integer | 학생 ID |
| `student_no` | string | 학번 |
| `name` | string | 이름 |
| `department` | string/null | 학과 |
| `attendance_date` | string | 출석 처리 일자 |
| `attendance_time` | string | 출석 처리 시간 |
| `status` | string | 출석 상태. 현재 기본값은 `present` |
| `confidence` | number/null | YOLO 얼굴 검출 confidence |
| `distance` | number/null | FaceNet 임베딩 거리 |
