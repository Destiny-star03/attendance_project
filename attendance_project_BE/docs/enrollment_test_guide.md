# 실시간 다중 프레임 학생 얼굴 등록 테스트 가이드

이 문서는 Android 실시간 얼굴 등록 기능을 서버 API 기준으로 테스트하는 방법을 정리한 문서입니다.
기존 단일 이미지 학생 등록, 출석 처리, 수업 세션, CSV 내보내기 기능은 수정하지 않고 신규 등록 API만 검증합니다.

## 1. 서버 실행 방법

PowerShell 또는 명령 프롬프트에서 아래 명령을 실행합니다.

```powershell
cd C:\Project\attendance_yolo26_project
venv\Scripts\python.exe -m uvicorn api.server:app --host 0.0.0.0 --port 8000 --reload
```

서버가 정상 실행되면 브라우저에서 다음 주소로 접속할 수 있습니다.

```text
http://127.0.0.1:8000/docs
```

## 2. Swagger 접속

브라우저에서 Swagger 문서 페이지를 엽니다.

```text
http://127.0.0.1:8000/docs
```

Swagger에서 각 API 항목을 펼친 뒤 `Try it out` 버튼을 누르면 값을 입력하고 요청을 보낼 수 있습니다.
요청 실행은 `Execute` 버튼으로 합니다.

## 3. 실시간 학생 얼굴 등록 테스트 순서

실시간 등록은 다음 순서로 진행합니다.

1. 등록 세션 시작
2. 정면 얼굴 업로드
3. 왼쪽 얼굴 업로드
4. 오른쪽 얼굴 업로드
5. 위 얼굴 업로드
6. 아래 얼굴 업로드
7. 진행 상태 확인
8. 최종 등록 완료

### 1단계: 등록 세션 시작

Swagger에서 `POST /students/enroll/start` 항목을 엽니다.

입력값:

- `student_no`: 학번
- `name`: 이름
- `department`: 학과

예시:

```text
student_no = 22260065
name = 최용탁
department = 스마트소프트웨어학과
```

기대 응답:

```json
{
  "success": true,
  "message": "얼굴 등록 세션이 시작되었습니다.",
  "enroll_id": "uuid-string",
  "required_poses": ["front", "left", "right", "up", "down"]
}
```

응답의 `enroll_id`는 이후 모든 프레임 업로드와 완료 요청에서 사용하므로 복사해 둡니다.

### 2단계: 정면 얼굴 업로드

Swagger에서 `POST /students/enroll/frame` 항목을 엽니다.

입력값:

- `enroll_id`: 1단계에서 받은 값
- `pose`: `front`
- `image`: 정면 얼굴 이미지 파일

기대 응답:

```json
{
  "success": true,
  "message": "front 얼굴이 등록되었습니다.",
  "enroll_id": "uuid-string",
  "pose": "front",
  "completed_poses": ["front"],
  "remaining_poses": ["left", "right", "up", "down"],
  "progress": 20
}
```

### 3단계: 왼쪽 얼굴 업로드

다시 `POST /students/enroll/frame`을 실행합니다.

입력값:

- `enroll_id`: 같은 등록 세션 ID
- `pose`: `left`
- `image`: 얼굴을 왼쪽으로 돌린 이미지 파일

기대 응답:

```json
{
  "success": true,
  "pose": "left",
  "completed_poses": ["front", "left"],
  "remaining_poses": ["right", "up", "down"],
  "progress": 40
}
```

### 4단계: 오른쪽 얼굴 업로드

다시 `POST /students/enroll/frame`을 실행합니다.

입력값:

- `enroll_id`: 같은 등록 세션 ID
- `pose`: `right`
- `image`: 얼굴을 오른쪽으로 돌린 이미지 파일

기대 응답:

```json
{
  "success": true,
  "pose": "right",
  "completed_poses": ["front", "left", "right"],
  "remaining_poses": ["up", "down"],
  "progress": 60
}
```

### 5단계: 위 얼굴 업로드

다시 `POST /students/enroll/frame`을 실행합니다.

입력값:

- `enroll_id`: 같은 등록 세션 ID
- `pose`: `up`
- `image`: 얼굴을 위로 든 이미지 파일

기대 응답:

```json
{
  "success": true,
  "pose": "up",
  "completed_poses": ["front", "left", "right", "up"],
  "remaining_poses": ["down"],
  "progress": 80
}
```

### 6단계: 아래 얼굴 업로드

다시 `POST /students/enroll/frame`을 실행합니다.

입력값:

- `enroll_id`: 같은 등록 세션 ID
- `pose`: `down`
- `image`: 얼굴을 아래로 숙인 이미지 파일

기대 응답:

```json
{
  "success": true,
  "pose": "down",
  "completed_poses": ["front", "left", "right", "up", "down"],
  "remaining_poses": [],
  "progress": 100
}
```

### 7단계: 진행률 확인

Swagger에서 `GET /students/enroll/{enroll_id}/status` 항목을 엽니다.

입력값:

- `enroll_id`: 등록 세션 ID

기대 응답:

```json
{
  "success": true,
  "enroll_id": "uuid-string",
  "student_no": "22260065",
  "name": "최용탁",
  "required_poses": ["front", "left", "right", "up", "down"],
  "completed_poses": ["front", "left", "right", "up", "down"],
  "remaining_poses": [],
  "progress": 100
}
```

### 8단계: 최종 등록 완료

Swagger에서 `POST /students/enroll/complete` 항목을 엽니다.

입력값:

- `enroll_id`: 등록 세션 ID

처리 내용:

- 수집된 5개 pose 임베딩을 평균화합니다.
- 평균 임베딩을 L2 normalize합니다.
- 기존 `students.face_encoding` BLOB 방식으로 저장합니다.
- 기존 학번이 있으면 `ALLOW_UPDATE_EXISTING_STUDENT_FACE` 정책에 따라 갱신하거나 중복 오류를 반환합니다.

기대 응답:

```json
{
  "success": true,
  "message": "학생 얼굴 등록이 완료되었습니다.",
  "created": true,
  "updated": false,
  "student": {
    "student_id": 1,
    "student_no": "22260065",
    "name": "최용탁",
    "department": "스마트소프트웨어학과"
  }
}
```

기존 학번의 얼굴 정보가 갱신된 경우에는 다음처럼 응답할 수 있습니다.

```json
{
  "success": true,
  "message": "학생 얼굴 정보가 갱신되었습니다.",
  "created": false,
  "updated": true,
  "student": {
    "student_id": 1,
    "student_no": "22260065",
    "name": "최용탁",
    "department": "스마트소프트웨어학과"
  }
}
```

## 4. 실패 케이스 테스트

### 얼굴 없는 이미지

`POST /students/enroll/frame`에서 얼굴이 없는 이미지를 업로드합니다.

기대 응답:

```json
{
  "success": false,
  "status": "no_face",
  "message": "얼굴을 찾지 못했습니다."
}
```

### 여러 명 얼굴 이미지

`POST /students/enroll/frame`에서 2명 이상 얼굴이 있는 이미지를 업로드합니다.

기대 응답:

```json
{
  "success": false,
  "status": "multiple_faces",
  "message": "한 명의 얼굴만 촬영해 주세요."
}
```

### 잘못된 pose

`pose`에 `front`, `left`, `right`, `up`, `down`이 아닌 값을 입력합니다.

예시:

```text
pose = side
```

기대 응답:

```json
{
  "success": false,
  "message": "지원하지 않는 pose입니다."
}
```

### 존재하지 않는 enroll_id

임의의 잘못된 `enroll_id`로 상태 조회, 프레임 업로드, 완료 요청을 실행합니다.

기대 응답:

```json
{
  "success": false,
  "message": "등록 세션을 찾을 수 없습니다."
}
```

### pose 누락 상태에서 complete 요청

예를 들어 `front`, `left`만 업로드한 뒤 `POST /students/enroll/complete`를 실행합니다.

기대 응답:

```json
{
  "success": false,
  "message": "필수 얼굴 방향이 누락되었습니다.",
  "remaining_poses": ["right", "up", "down"]
}
```

### 중복 학번

이미 등록된 학번으로 `POST /students/enroll/start`를 실행합니다.

`config.ALLOW_UPDATE_EXISTING_STUDENT_FACE = true`인 경우:

- 등록 세션이 시작됩니다.
- 최종 완료 시 기존 학생의 얼굴 임베딩, 이름, 학과가 갱신됩니다.

`config.ALLOW_UPDATE_EXISTING_STUDENT_FACE = false`인 경우:

```json
{
  "success": false,
  "message": "이미 등록된 학번입니다."
}
```

## 5. 등록 완료 후 확인

학생 얼굴 등록이 완료되면 기존 출석 인식 API로 검증합니다.

### 얼굴 인식 테스트

Swagger에서 `POST /attendance/recognize`를 실행합니다.

입력값:

- `image`: 방금 등록한 학생의 얼굴 이미지
- `session_id`: 선택 사항

기대 동작:

- 등록된 학생이면 `matched=true`가 반환됩니다.
- 3초 유지 전에는 `status="recognizing"`이 반환됩니다.
- 같은 학생 얼굴을 3초 이상 반복 요청하면 `status="attended"`가 반환됩니다.
- 같은 수업 세션에서 다시 요청하면 `status="already_attended"`가 반환됩니다.

인식 중 응답 예시:

```json
{
  "success": true,
  "matched": true,
  "status": "recognizing",
  "message": "인식 중",
  "student": {
    "student_no": "22260065",
    "name": "최용탁",
    "department": "스마트소프트웨어학과"
  },
  "attendance": {
    "marked": false,
    "message": "미출석"
  }
}
```

출석 완료 응답 예시:

```json
{
  "success": true,
  "matched": true,
  "status": "attended",
  "message": "출석 완료",
  "attendance": {
    "marked": true,
    "message": "출석 완료"
  }
}
```

## 6. Android 연동 방향

Android 앱에서는 신규 등록 API를 다음 흐름으로 사용하면 됩니다.

1. 학생 등록 화면에서 학번, 이름, 학과를 입력합니다.
2. `POST /students/enroll/start`를 호출해 `enroll_id`를 받습니다.
3. 카메라 화면에서 `front`, `left`, `right`, `up`, `down` 순서로 안내 문구를 표시합니다.
4. 각 방향마다 얼굴 이미지 프레임을 `POST /students/enroll/frame`으로 전송합니다.
5. 서버 응답이 `success=true`이면 다음 방향 안내로 넘어갑니다.
6. 서버 응답이 `no_face`이면 "얼굴을 화면에 맞춰주세요"를 표시합니다.
7. 서버 응답이 `multiple_faces`이면 "한 명만 촬영해 주세요"를 표시합니다.
8. 모든 방향의 진행률이 100%가 되면 `POST /students/enroll/complete`를 호출합니다.
9. 완료 응답이 성공이면 "학생 얼굴 등록이 완료되었습니다."를 표시합니다.

Android 앱은 얼굴 방향을 서버에 `pose` 값으로 전달합니다.
현재 서버는 실제 고개 방향을 판별하지 않고, Android에서 전달한 `pose` 기준으로 임베딩을 분류합니다.

## 7. 테스트 시 확인할 것

- `models/yolo26_face.pt` 모델 파일이 존재해야 합니다.
- FaceNet 관련 패키지가 설치되어 있어야 합니다.
- 등록 이미지에는 한 명의 얼굴만 보여야 합니다.
- 각 pose별로 얼굴이 너무 흐리거나 어둡지 않아야 합니다.
- 기존 학생 얼굴을 갱신하려면 `config.ALLOW_UPDATE_EXISTING_STUDENT_FACE = True`인지 확인합니다.
- Swagger에서 업로드한 이미지가 JPEG 또는 PNG처럼 OpenCV에서 디코딩 가능한 형식인지 확인합니다.
