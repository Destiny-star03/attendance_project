# Android 앱과 Python 출석 서버 연동 계획

## 1. 연동 목표

본 프로젝트의 Python 프로그램은 YOLO26 기반 얼굴 검출, 얼굴 인식, 학생 정보 조회, 출석 처리를 수행한다. 추후 Android Studio로 제작한 앱과 연동할 때는 Android 앱이 카메라 또는 갤러리에서 이미지를 준비하고, Python 서버가 인공지능 추론과 데이터베이스 처리를 담당하는 구조로 확장한다.

Android 앱은 사용자 인터페이스와 이미지 입력을 담당하고, Python 서버는 다음 기능을 담당한다.

- 이미지에서 얼굴 검출
- 얼굴 crop 및 특징값 추출
- 등록된 학생 face_encoding과 비교
- 학생 정보 조회
- 출석 처리
- 처리 결과를 JSON으로 응답

## 2. 전체 시스템 구조

```text
Android 앱
  - 카메라 촬영
  - 갤러리 이미지 선택
  - 이미지 파일 압축 및 multipart/form-data 전송
        |
        | HTTP 요청
        v
Python 서버
  - FastAPI 엔드포인트 수신
  - FaceDetector 얼굴 검출
  - FaceRecognizer 얼굴 인식
  - student_service 학생 조회
  - attendance_service 출석 처리
        |
        v
SQLite DB
  - students 테이블
  - attendance 테이블
        |
        v
Python 서버 JSON 응답
        |
        v
Android 앱
  - 이름, 학번, 출석 상태 표시
```

## 3. Android 이미지 전송 방식

Android 앱은 두 가지 방식으로 이미지를 준비할 수 있다.

1. 카메라 촬영
   - Android CameraX 또는 기본 카메라 Intent를 사용한다.
   - 촬영된 이미지를 앱 내부 저장소에 임시 파일로 저장한다.
   - 서버 전송 전에 JPEG 형식으로 압축한다.

2. 갤러리 이미지 선택
   - Photo Picker 또는 Storage Access Framework를 사용한다.
   - 선택한 이미지 URI를 읽어 임시 파일 또는 byte array로 변환한다.
   - 서버에 `multipart/form-data` 형식으로 전송한다.

전송 형식은 다음과 같이 설계한다.

```text
POST /attendance/recognize
Content-Type: multipart/form-data

fields:
- image: 얼굴이 포함된 이미지 파일
```

이미지 크기가 너무 크면 네트워크 지연이 커질 수 있으므로 Android 앱에서 긴 변 기준 1280px 이하로 리사이즈한 뒤 전송하는 방식을 권장한다.

## 4. Python 서버 처리 흐름

서버는 이미지를 받은 뒤 다음 순서로 처리한다.

1. 업로드된 이미지 파일을 OpenCV 이미지로 변환한다.
2. `FaceDetector.detect_faces(frame)`으로 얼굴을 검출한다.
3. 얼굴이 없으면 미검출 응답을 반환한다.
4. 얼굴이 2명 이상이면 다중 얼굴 오류 응답을 반환한다.
5. 얼굴이 1명일 때만 box 기준으로 crop한다.
6. `FaceRecognizer.recognize(face_crop)`으로 학생을 인식한다.
7. 미등록 사용자이면 `matched=false`로 응답한다.
8. 등록된 학생이면 `student_service.get_student_by_id()`로 학생 정보를 조회한다.
9. `attendance_service.mark_attendance()`로 출석 처리한다.
10. 이름, 학번, 출석 상태를 JSON으로 응답한다.

## 5. JSON 응답 설계

정상적으로 등록 학생이 인식된 경우:

```json
{
  "success": true,
  "matched": true,
  "student": {
    "student_id": 1,
    "student_no": "2024001",
    "name": "홍길동",
    "department": "인공지능학과"
  },
  "recognition": {
    "distance": 0.21
  },
  "attendance": {
    "message": "출석 완료"
  }
}
```

이미 출석한 학생인 경우:

```json
{
  "success": true,
  "matched": true,
  "student": {
    "student_id": 1,
    "student_no": "2024001",
    "name": "홍길동",
    "department": "인공지능학과"
  },
  "recognition": {
    "distance": 0.21
  },
  "attendance": {
    "message": "이미 출석함"
  }
}
```

미등록 사용자인 경우:

```json
{
  "success": true,
  "matched": false,
  "message": "미등록 사용자"
}
```

얼굴이 검출되지 않은 경우:

```json
{
  "success": false,
  "matched": false,
  "message": "얼굴을 찾지 못했습니다."
}
```

얼굴이 여러 명 검출된 경우:

```json
{
  "success": false,
  "matched": false,
  "message": "한 명의 얼굴만 포함된 이미지를 전송해 주세요."
}
```

## 6. FastAPI 엔드포인트 설계

추후 Python 프로젝트에 FastAPI를 추가하면 다음과 같은 API 구조로 확장할 수 있다.

### 6.1 상태 확인

```text
GET /health
```

서버 실행 상태를 확인한다.

응답 예시:

```json
{
  "status": "ok"
}
```

### 6.2 출석 인식

```text
POST /attendance/recognize
```

Android 앱에서 전송한 이미지를 기반으로 얼굴 검출, 얼굴 인식, 출석 처리를 한 번에 수행한다.

요청:

```text
multipart/form-data
- image: 이미지 파일
```

응답:

```json
{
  "success": true,
  "matched": true,
  "student": {
    "student_id": 1,
    "student_no": "2024001",
    "name": "홍길동",
    "department": "인공지능학과"
  },
  "attendance": {
    "message": "출석 완료"
  }
}
```

### 6.3 학생 등록

```text
POST /students
```

관리자용 Android 화면 또는 웹 관리 화면에서 학생을 등록한다.

요청:

```text
multipart/form-data
- student_no: 학번
- name: 이름
- department: 학과
- image: 얼굴 사진 파일
```

응답 예시:

```json
{
  "success": true,
  "student_id": 1,
  "message": "학생 등록이 완료되었습니다."
}
```

### 6.4 오늘 출석 목록 조회

```text
GET /attendance/today
```

응답 예시:

```json
{
  "success": true,
  "items": [
    {
      "date": "2026-05-13",
      "time": "09:10:22",
      "name": "홍길동",
      "student_no": "2024001",
      "department": "인공지능학과",
      "status": "present"
    }
  ]
}
```

### 6.5 날짜별 출석 목록 조회

```text
GET /attendance?date=2026-05-13
```

특정 날짜의 출석 기록을 조회한다.

### 6.6 날짜별 CSV 내보내기

```text
GET /attendance/export?date=2026-05-13
```

서버가 `data/exports/attendance_YYYY-MM-DD.csv` 파일을 생성하고 다운로드할 수 있도록 응답한다.

## 7. Android 앱 화면 구성 예시

Android 앱은 다음 화면으로 구성할 수 있다.

- 로그인 또는 관리자 선택 화면
- 학생 출석 촬영 화면
- 출석 결과 화면
- 학생 등록 화면
- 날짜별 출석 조회 화면
- CSV 내보내기 화면

출석 촬영 화면에서는 촬영 버튼을 누르면 이미지를 서버로 전송하고, 서버 응답에 따라 결과를 표시한다.

표시 예시:

```text
이름: 홍길동
학번: 2024001
출석 상태: 출석 완료
```

미등록 사용자인 경우:

```text
미등록 사용자입니다.
관리자에게 문의하세요.
```

## 8. 보안 및 운영 고려사항

학교 프로젝트 수준의 MVP에서는 같은 Wi-Fi 네트워크에서 Android 앱과 Python 서버를 연결할 수 있다. 실제 운영 환경에서는 다음 항목을 추가로 고려해야 한다.

- HTTPS 적용
- 관리자 API 인증
- 학생 얼굴 이미지 및 face_encoding 보호
- 서버 접근 IP 제한
- 출석 조작 방지를 위한 시간 기록
- 이미지 파일 장기 저장 여부 정책
- 개인정보 처리 동의 및 보관 기간 명시

## 9. 단계별 구현 계획

1. Python 서버에 FastAPI 추가
2. `/health` 엔드포인트 구현
3. `/attendance/recognize` 엔드포인트 구현
4. Android 앱에서 이미지 촬영 및 서버 전송 구현
5. 서버 JSON 응답을 Android 화면에 표시
6. 학생 등록 API와 관리자 화면 추가
7. 날짜별 출석 조회 및 CSV 내보내기 기능 추가

이 구조를 사용하면 현재 Python 데스크톱 출석 프로그램을 크게 변경하지 않고도 Android 앱 연동 구조로 확장할 수 있다.
