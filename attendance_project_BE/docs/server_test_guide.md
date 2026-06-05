# 서버 테스트 가이드

이 문서는 YOLO26 + FaceNet + FastAPI 기반 얼굴 인식 출석 서버가 정상 동작하는지 확인하기 위한 수동 테스트 절차다. Swagger UI만으로도 테스트할 수 있고, 필요하면 Python `requests`로도 간단히 확인할 수 있다.

## 1. 서버 실행

프로젝트 루트에서 실행한다.

```powershell
venv\Scripts\python.exe -m uvicorn api.server:app --host 0.0.0.0 --port 8000 --reload
```

서버가 정상 실행되면 브라우저에서 다음 주소에 접속한다.

```text
http://127.0.0.1:8000/docs
```

## 2. Swagger 접속

Swagger UI:

```text
http://127.0.0.1:8000/docs
```

먼저 `GET /health`를 실행한다.

기대 응답:

```json
{
  "status": "ok"
}
```

## 3. 기본 테스트 순서

### 3.1 새 수업 세션 생성

`POST /sessions`

요청 JSON:

```json
{
  "subject_name": "데이터베이스",
  "class_date": "2026-05-18",
  "start_time": "09:00",
  "end_time": "10:50"
}
```

기대 응답:

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

### 3.2 활성 세션 확인

`GET /sessions/active`

기대 응답:

```json
{
  "success": true,
  "message": "활성 출석 세션 조회 성공",
  "session": {
    "session_id": 1,
    "subject_name": "데이터베이스",
    "class_date": "2026-05-18",
    "is_active": true
  }
}
```

### 3.3 학생 등록

`POST /students`

`multipart/form-data` 필드:

```text
student_no: 22260065
name: 최용탁
department: 스마트소프트웨어학과
image: 얼굴 사진 파일
```

주의:

- 얼굴이 정확히 1명인 이미지를 사용한다.
- 이미 등록된 학번이면 실패한다.
- 기존 DB를 새로 만들었다면 학생을 다시 등록해야 한다.

기대 응답:

```json
{
  "success": true,
  "message": "학생 등록이 완료되었습니다.",
  "student": {
    "student_id": 1,
    "student_no": "22260065",
    "name": "최용탁",
    "department": "스마트소프트웨어학과"
  }
}
```

### 3.4 출석 인식 요청

`POST /attendance/recognize`

`multipart/form-data` 필드:

```text
image: 등록된 학생 얼굴 이미지
session_id: 선택값
```

`session_id`를 보내지 않으면 현재 활성 세션 기준으로 처리한다.

Android 앱은 이 요청을 0.5초~1초 간격으로 반복 호출한다.

## 4. 기대 응답 예시

### 4.1 3초 전: recognizing

등록된 학생 얼굴이 인식되었지만 3초가 지나지 않은 상태다. 이때는 DB에 출석 저장이 되면 안 된다.

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
    "class_date": "2026-05-18"
  },
  "student": {
    "student_id": 1,
    "student_no": "22260065",
    "name": "최용탁",
    "department": "스마트소프트웨어학과"
  },
  "recognition": {
    "distance": 0.064
  },
  "attendance": {
    "marked": false,
    "message": "미출석"
  }
}
```

### 4.2 3초 후: attended

같은 `session_id + student_id` 조합으로 3초 이상 유지되면 출석 저장이 발생한다.

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
    "name": "최용탁",
    "department": "스마트소프트웨어학과"
  },
  "recognition": {
    "distance": 0.064
  },
  "attendance": {
    "marked": true,
    "message": "출석 완료"
  }
}
```

### 4.3 다시 요청: already_attended

같은 세션에서 같은 학생이 이미 출석했다면 중복 저장하지 않는다.

```json
{
  "success": true,
  "matched": true,
  "status": "already_attended",
  "message": "이미 출석함",
  "session": {
    "session_id": 1,
    "subject_name": "데이터베이스",
    "class_date": "2026-05-18"
  },
  "student": {
    "student_id": 1,
    "student_no": "22260065",
    "name": "최용탁",
    "department": "스마트소프트웨어학과"
  },
  "attendance": {
    "marked": false,
    "message": "이미 출석함"
  }
}
```

### 4.4 미등록 사용자: unknown

```json
{
  "success": true,
  "matched": false,
  "status": "unknown",
  "message": "미등록 사용자"
}
```

### 4.5 얼굴 없음: no_face

```json
{
  "success": false,
  "matched": false,
  "status": "no_face",
  "message": "얼굴을 찾지 못했습니다."
}
```

### 4.6 여러 얼굴: multiple_faces

```json
{
  "success": false,
  "matched": false,
  "status": "multiple_faces",
  "message": "한 명의 얼굴만 촬영해 주세요."
}
```

## 5. 다음 수업 세션 테스트

같은 날짜라도 새 세션을 만들면 같은 학생도 다시 출석 가능해야 한다.

1. `POST /sessions`로 다른 과목 세션 생성

```json
{
  "subject_name": "운영체제",
  "class_date": "2026-05-18",
  "start_time": "11:00",
  "end_time": "12:50"
}
```

2. `GET /sessions/active`로 새 세션이 활성화되었는지 확인
3. 같은 학생 얼굴로 `POST /attendance/recognize` 반복 요청
4. 3초 전 `recognizing` 확인
5. 3초 후 `attended` 확인
6. 다시 요청 시 `already_attended` 확인

## 6. 출석 목록 조회

### 6.1 세션 기준 조회

`GET /attendance?session_id=1`

기대 응답:

```json
{
  "success": true,
  "message": "세션 출석 목록 조회 성공",
  "session": {
    "session_id": 1,
    "subject_name": "데이터베이스",
    "class_date": "2026-05-18"
  },
  "items": [
    {
      "attendance_id": 1,
      "student_id": 1,
      "student_no": "22260065",
      "name": "최용탁",
      "department": "스마트소프트웨어학과",
      "attendance_date": "2026-05-18",
      "attendance_time": "09:12:30",
      "status": "present",
      "distance": 0.064
    }
  ]
}
```

### 6.2 오늘 출석 조회

`GET /attendance/today`

오늘 날짜에 해당하는 모든 출석 기록을 반환한다.

### 6.3 날짜 기준 조회

`GET /attendance?date=2026-05-18`

해당 날짜의 모든 세션 출석 기록을 반환한다.

### 6.4 파라미터 없이 조회

`GET /attendance`

현재 활성 세션의 출석 목록을 반환한다.

## 7. CSV 다운로드

### 7.1 세션 기준 CSV

`GET /attendance/export?session_id=1`

### 7.2 날짜 기준 CSV

`GET /attendance/export?date=2026-05-18`

### 7.3 활성 세션 CSV

`GET /attendance/export`

CSV 컬럼:

```text
세션ID, 과목명, 날짜, 시간, 학번, 이름, 학과, 출석상태, 거리값
```

CSV 파일은 `utf-8-sig` 인코딩으로 저장되므로 Excel에서 한글이 깨지지 않아야 한다.

## 8. Python requests 간단 테스트

아래 코드는 서버가 켜진 상태에서 실행한다.

```python
import time
import requests

BASE_URL = "http://127.0.0.1:8000"
IMAGE_PATH = r"C:\path\to\face.jpg"

print(requests.get(f"{BASE_URL}/health").json())

session_response = requests.post(
    f"{BASE_URL}/sessions",
    json={
        "subject_name": "데이터베이스",
        "class_date": "2026-05-18",
        "start_time": "09:00",
        "end_time": "10:50",
    },
).json()
print(session_response)

session_id = session_response["session"]["session_id"]

for i in range(5):
    with open(IMAGE_PATH, "rb") as image_file:
        response = requests.post(
            f"{BASE_URL}/attendance/recognize",
            files={"image": image_file},
            data={"session_id": str(session_id)},
        ).json()

    print(i, response.get("status"), response.get("message"), response.get("elapsed_seconds"))
    time.sleep(0.8)

print(requests.get(f"{BASE_URL}/attendance", params={"session_id": session_id}).json())
```

기대 흐름:

```text
recognizing
recognizing
recognizing
attended
already_attended
```

## 9. Android 연동 전 네트워크 확인

1. 태블릿/휴대폰과 PC가 같은 Wi-Fi에 연결되어 있는지 확인한다.
2. PC 내부 IP를 확인한다.

Windows:

```powershell
ipconfig
```

예:

```text
IPv4 주소: 192.168.0.23
```

3. 서버를 반드시 `0.0.0.0`으로 실행한다.

```powershell
venv\Scripts\python.exe -m uvicorn api.server:app --host 0.0.0.0 --port 8000 --reload
```

4. 모바일 브라우저에서 접속한다.

```text
http://192.168.0.23:8000/health
```

기대 응답:

```json
{
  "status": "ok"
}
```

Android 에뮬레이터에서는 다음 주소를 사용한다.

```text
http://10.0.2.2:8000
```

## 10. 문제 발생 시 확인할 것

- 서버가 `0.0.0.0`으로 실행되었는지 확인한다.
- Windows 방화벽이 8000 포트를 막고 있지 않은지 확인한다.
- `models/yolo26_face.pt` 경로가 맞는지 확인한다.
- `config.py`의 `YOLO_MODEL_PATH`가 실제 모델 파일을 가리키는지 확인한다.
- 학생이 FaceNet 임베딩으로 다시 등록되어 있는지 확인한다.
- `FACE_RECOGNITION_THRESHOLD`가 너무 낮거나 높지 않은지 확인한다.
- 얼굴이 1명만 포함된 이미지인지 확인한다.
- 같은 수업에서 중복 출석이면 `already_attended`가 정상이다.
- 다른 수업에서 다시 출석하려면 새 세션을 생성하거나 다른 세션을 활성화해야 한다.
- `/attendance/recognize` 요청을 3초 이상 반복 호출하고 있는지 확인한다.
- `session_id`를 직접 보내는 경우 현재 활성 세션과 무관하게 해당 세션 기준으로 처리된다.

