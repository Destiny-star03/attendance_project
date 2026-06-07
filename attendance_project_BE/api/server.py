from __future__ import annotations

import csv
from datetime import datetime
from pathlib import Path
from typing import Any

from fastapi import FastAPI, File, Form, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

from config import (
    ALLOW_UPDATE_EXISTING_STUDENT_FACE,
    ATTENDANCE_HOLD_SECONDS,
    EXPORT_DIR,
    TRACKER_TIMEOUT_SECONDS,
    ensure_directories,
)
from modules.attendance_service import (
    activate_session,
    close_session,
    create_attendance_session,
    get_active_session,
    get_attendance_records,
    get_session_by_id,
    get_student_attendance_stats,
    has_attended,
    list_sessions,
    mark_attendance,
)
from modules import subject_service
from modules.attendance_state import AttendanceRecognitionTracker
from modules.detector import FaceDetector
from modules.enrollment_service import EnrollmentService
from modules.face_pipeline import extract_single_face_from_bytes
from modules.recognizer import FaceRecognizer, encoding_to_bytes, extract_face_encoding
from modules.student_registration_state import StudentRegistrationTracker
from modules.student_service import (
    add_student,
    get_student_by_id,
    get_all_students,
    is_student_no_exists,
    upsert_student_face,
)


class SessionCreateRequest(BaseModel):
    subject_name: str
    class_date: str
    start_time: str | None = None
    end_time: str | None = None



class SubjectCreateRequest(BaseModel):
    subject_name: str
    professor_name: str | None = None
    classroom: str | None = None


class SubjectUpdateRequest(BaseModel):
    subject_name: str | None = None
    professor_name: str | None = None
    classroom: str | None = None


class SubjectSessionCreateRequest(BaseModel):
    class_date: str
    start_time: str | None = None
    end_time: str | None = None

class StudentRegistrationStartRequest(BaseModel):
    student_no: str
    name: str
    department: str | None = None


app = FastAPI(title="YOLO26 FaceNet Attendance API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

_detector: FaceDetector | None = None
_recognizer: FaceRecognizer | None = None
recognition_tracker = AttendanceRecognitionTracker(
    hold_seconds=ATTENDANCE_HOLD_SECONDS,
    timeout_seconds=TRACKER_TIMEOUT_SECONDS,
)
student_registration_tracker = StudentRegistrationTracker()
enrollment_service = EnrollmentService()


def _get_detector() -> FaceDetector:
    global _detector
    if _detector is None:
        _detector = FaceDetector()
    return _detector


def _get_recognizer() -> FaceRecognizer:
    global _recognizer
    if _recognizer is None:
        _recognizer = FaceRecognizer()
    return _recognizer


async def _read_upload_image(image: UploadFile | None) -> bytes:
    if image is None:
        raise ValueError("이미지 파일을 첨부해 주세요.")

    data = await image.read()
    if not data:
        raise ValueError("이미지 파일이 비어 있습니다.")

    return data


def _validate_date(date: str | None) -> str | None:
    if date is None or not date.strip():
        return None

    value = date.strip()
    datetime.strptime(value, "%Y-%m-%d")
    return value


def _get_request_session(session_id: int | None = None) -> dict[str, Any] | None:
    if session_id is not None:
        return get_session_by_id(int(session_id))
    return get_active_session()


def _session_payload(session: dict[str, Any] | None) -> dict[str, Any] | None:
    if session is None:
        return None

    return {
        "session_id": session.get("session_id"),
        "subject_name": session.get("subject_name") or session.get("session_name"),
        "subject_id": session.get("subject_id"),
        "class_date": session.get("class_date"),
        "start_time": session.get("start_time"),
        "end_time": session.get("end_time"),
        "is_active": session.get("is_active"),
    }


def _student_payload(student: dict[str, Any]) -> dict[str, Any]:
    return {
        "student_id": student["id"],
        "student_no": student["student_no"],
        "name": student["name"],
        "department": student.get("department"),
        "created_at": student.get("created_at"),
    }


def _attendance_record_payload(record: dict[str, Any]) -> dict[str, Any]:
    return {
        "attendance_id": record.get("id"),
        "session_id": record.get("session_id"),
        "subject_name": record.get("subject_name"),
        "class_date": record.get("class_date"),
        "start_time": record.get("start_time"),
        "end_time": record.get("end_time"),
        "student_id": record.get("student_id"),
        "student_no": record.get("student_no"),
        "name": record.get("name"),
        "department": record.get("department"),
        "attendance_date": record.get("attendance_date"),
        "attendance_time": record.get("attendance_time"),
        "status": record.get("status"),
        "confidence": record.get("confidence"),
        "distance": record.get("distance"),
    }


def _attendance_response(result: dict[str, Any]) -> dict[str, Any]:
    success = bool(result.get("success", False))
    return {
        "success": success,
        "message": "출석 목록 조회 성공" if success else result.get("message", "출석 목록을 조회하지 못했습니다."),
        "session": _session_payload(result.get("session")),
        "date": result.get("date"),
        "items": [_attendance_record_payload(record) for record in result.get("items", [])],
    }

def _recognition_payload(recognition: dict[str, Any]) -> dict[str, Any]:
    return {
        "distance": recognition.get("distance"),
        "similarity": recognition.get("similarity"),
    }


async def _read_request_data(request: Request) -> dict[str, Any]:
    content_type = request.headers.get("content-type", "")
    if "application/json" in content_type:
        data = await request.json()
        return dict(data or {})

    form = await request.form()
    return dict(form)


def _enroll_face_failure_response(
    faces: list[dict[str, Any]] | None,
    *,
    enroll_id: str | None = None,
    pose: str | None = None,
) -> dict[str, Any]:
    if faces is None:
        return {
            "success": False,
            "status": "image_error",
            "message": "이미지를 읽을 수 없습니다.",
            "enroll_id": enroll_id,
            "pose": pose,
        }

    if len(faces) >= 2:
        return {
            "success": False,
            "status": "multiple_faces",
            "message": "한 명만 촬영해 주세요.",
            "enroll_id": enroll_id,
            "pose": pose,
        }

    return {
        "success": False,
        "status": "no_face",
        "message": "얼굴을 찾지 못했습니다.",
        "enroll_id": enroll_id,
        "pose": pose,
    }

def _student_registration_response(registration: dict[str, Any]) -> dict[str, Any]:
    return {
        "registration_id": registration.get("registration_id"),
        "student_no": registration.get("student_no"),
        "name": registration.get("name"),
        "department": registration.get("department"),
        "directions": registration.get("directions"),
        "accepted_count": registration.get("accepted_count"),
        "direction_counts": registration.get("direction_counts"),
        "completed_directions": registration.get("completed_directions"),
        "missing_directions": registration.get("missing_directions"),
        "is_complete": registration.get("is_complete"),
    }


def _face_failure_response(faces: list[dict[str, Any]] | None) -> dict[str, Any]:
    recognition_tracker.cleanup()
    recognition_tracker.reset()

    if faces is None:
        return {
            "success": False,
            "matched": False,
            "status": "image_error",
            "message": "이미지를 읽을 수 없습니다.",
        }

    if len(faces) >= 2:
        return {
            "success": False,
            "matched": False,
            "status": "multiple_faces",
            "message": "한 명만 촬영해 주세요.",
        }

    return {
        "success": False,
        "matched": False,
        "status": "no_face",
        "message": "얼굴을 찾지 못했습니다.",
    }

def _csv_file_name(session_id: int | None, date: str | None, session: dict[str, Any] | None) -> str:
    if session_id is not None:
        return f"attendance_session_{session_id}.csv"
    if date is not None:
        return f"attendance_{date}.csv"
    if session is not None:
        return f"attendance_session_{session.get('session_id')}.csv"
    return "attendance.csv"


def _export_attendance_csv(result: dict[str, Any], file_name: str) -> Path:
    ensure_directories()
    export_path = Path(EXPORT_DIR) / file_name
    columns = ["session_id", "subject_name", "date", "time", "student_no", "name", "department", "status", "distance"]

    with export_path.open("w", newline="", encoding="utf-8-sig") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=columns)
        writer.writeheader()

        for record in result.get("items", []):
            writer.writerow(
                {
                    "session_id": record.get("session_id", ""),
                    "subject_name": record.get("subject_name", ""),
                    "date": record.get("attendance_date", ""),
                    "time": record.get("attendance_time", ""),
                    "student_no": record.get("student_no", ""),
                    "name": record.get("name", ""),
                    "department": record.get("department") or "",
                    "status": record.get("status", ""),
                    "distance": record.get("distance") if record.get("distance") is not None else "",
                }
            )

    return export_path

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/sessions", response_model=None)
def create_session(request: SessionCreateRequest) -> dict[str, Any]:
    result = create_attendance_session(
        subject_name=request.subject_name,
        class_date=request.class_date,
        start_time=request.start_time,
        end_time=request.end_time,
    )
    if result.get("success"):
        recognition_tracker.reset()
    if result.get("session") is not None:
        result["session"] = _session_payload(result["session"])
    return result


@app.get("/sessions", response_model=None)
def sessions() -> dict[str, Any]:
    return {
        "success": True,
        "message": "출석 세션 목록 조회 성공",
        "items": [_session_payload(session) for session in list_sessions()],
    }


@app.get("/sessions/active", response_model=None)
def active_session() -> dict[str, Any]:
    session = get_active_session()
    if session is None:
        return {
            "success": False,
            "message": "활성 출석 세션이 없습니다.",
            "session": None,
        }

    return {
        "success": True,
        "message": "활성 출석 세션 조회 성공",
        "session": _session_payload(session),
    }


@app.post("/sessions/{session_id}/activate", response_model=None)
def activate_attendance_session_api(session_id: int) -> dict[str, Any]:
    result = activate_session(session_id)
    if result.get("success"):
        recognition_tracker.reset()
    if result.get("session") is not None:
        result["session"] = _session_payload(result["session"])
    return result


@app.post("/sessions/{session_id}/close", response_model=None)
def close_attendance_session_api(session_id: int) -> dict[str, Any]:
    result = close_session(session_id)
    if result.get("success"):
        recognition_tracker.reset()
    if result.get("session") is not None:
        result["session"] = _session_payload(result["session"])
    return result



@app.post("/subjects", response_model=None)
def create_subject_api(request: SubjectCreateRequest) -> dict[str, Any]:
    return subject_service.create_subject(
        subject_name=request.subject_name,
        professor_name=request.professor_name,
        classroom=request.classroom,
    )


@app.get("/subjects", response_model=None)
def subjects_api() -> dict[str, Any]:
    return subject_service.get_subjects()


@app.get("/subjects/{subject_id}", response_model=None)
def subject_detail_api(subject_id: int) -> dict[str, Any]:
    return subject_service.get_subject_by_id(subject_id)


@app.put("/subjects/{subject_id}", response_model=None)
def update_subject_api(subject_id: int, request: SubjectUpdateRequest) -> dict[str, Any]:
    return subject_service.update_subject(
        subject_id=subject_id,
        subject_name=request.subject_name,
        professor_name=request.professor_name,
        classroom=request.classroom,
    )


@app.delete("/subjects/{subject_id}", response_model=None)
def delete_subject_api(subject_id: int) -> dict[str, Any]:
    return subject_service.delete_subject(subject_id)


@app.post("/subjects/{subject_id}/students/{student_id}", response_model=None)
def add_subject_student_api(subject_id: int, student_id: int) -> dict[str, Any]:
    return subject_service.add_student_to_subject(subject_id, student_id)


@app.delete("/subjects/{subject_id}/students/{student_id}", response_model=None)
def remove_subject_student_api(subject_id: int, student_id: int) -> dict[str, Any]:
    return subject_service.remove_student_from_subject(subject_id, student_id)


@app.get("/subjects/{subject_id}/students", response_model=None)
def subject_students_api(subject_id: int) -> dict[str, Any]:
    return subject_service.get_subject_students(subject_id)


@app.post("/subjects/{subject_id}/sessions", response_model=None)
def create_subject_session_api(subject_id: int, request: SubjectSessionCreateRequest) -> dict[str, Any]:
    subject_result = subject_service.get_subject_by_id(subject_id)
    if not subject_result.get("success") or subject_result.get("subject") is None:
        return {
            "success": False,
            "message": subject_result.get("message", "과목을 찾을 수 없습니다."),
            "session": None,
        }

    subject = subject_result["subject"]
    result = create_attendance_session(
        subject_name=subject["subject_name"],
        class_date=request.class_date,
        start_time=request.start_time,
        end_time=request.end_time,
        subject_id=subject_id,
    )
    if result.get("success"):
        recognition_tracker.reset()
        result["message"] = "수업 세션이 생성되었습니다."
    if result.get("session") is not None:
        result["session"] = _session_payload(result["session"])
    return result

@app.post("/students/registration-sessions", response_model=None)
def start_student_registration_session(request: StudentRegistrationStartRequest) -> dict[str, Any]:
    try:
        student_no = request.student_no.strip()
        name = request.name.strip()
        department = request.department.strip() if request.department else None

        if not student_no:
            return {"success": False, "message": "학번을 입력해 주세요."}
        if not name:
            return {"success": False, "message": "이름을 입력해 주세요."}
        if is_student_no_exists(student_no):
            return {"success": False, "message": "이미 등록된 학번입니다."}

        registration = student_registration_tracker.start(
            student_no=student_no,
            name=name,
            department=department,
        )
        registration_payload = _student_registration_response(registration)
        return {
            "success": True,
            "message": "학생 얼굴 등록 세션이 시작되었습니다.",
            "registration_id": registration_payload["registration_id"],
            "directions": registration_payload["directions"],
            "registration": registration_payload,
        }

    except Exception as error:
        return {
            "success": False,
            "message": f"학생 얼굴 등록 세션 시작 중 오류가 발생했습니다: {error}",
        }


@app.post("/students/registration-sessions/{registration_id}/frames", response_model=None)
async def add_student_registration_frame(
    registration_id: str,
    direction: str = Form(...),
    image: UploadFile | None = File(None),
) -> dict[str, Any]:
    try:
        image_bytes = await _read_upload_image(image)
        result = extract_single_face_from_bytes(image_bytes, _get_detector(), build_encoding=False)

        if not result.success or result.face_crop is None:
            failure = _face_failure_response(result.faces)
            failure["registration_id"] = registration_id
            failure["direction"] = direction
            return failure

        embedding = extract_face_encoding(result.face_crop)
        add_result = student_registration_tracker.add_embedding(
            registration_id=registration_id,
            direction=direction,
            embedding=embedding,
        )

        if not add_result.get("success"):
            return {
                "success": False,
                "message": add_result.get("message", "얼굴 프레임을 추가하지 못했습니다."),
                "registration_id": registration_id,
                "direction": add_result.get("direction", direction),
                "directions": add_result.get("directions"),
                "accepted_count": add_result.get("accepted_count"),
                "direction_counts": add_result.get("direction_counts"),
                "completed_directions": add_result.get("completed_directions"),
                "missing_directions": add_result.get("missing_directions"),
                "is_complete": add_result.get("is_complete"),
            }

        return {
            "success": True,
            "message": add_result.get("message", "얼굴 프레임이 추가되었습니다."),
            "registration_id": registration_id,
            "direction": add_result.get("direction", direction),
            "accepted_count": add_result.get("accepted_count"),
            "direction_counts": add_result.get("direction_counts"),
            "completed_directions": add_result.get("completed_directions"),
            "missing_directions": add_result.get("missing_directions"),
            "is_complete": add_result.get("is_complete"),
        }

    except FileNotFoundError:
        return {
            "success": False,
            "registration_id": registration_id,
            "direction": direction,
            "message": "YOLO26 얼굴 검출 모델 파일을 찾을 수 없습니다.",
        }
    except RuntimeError as error:
        return {
            "success": False,
            "registration_id": registration_id,
            "direction": direction,
            "message": str(error),
        }
    except ValueError as error:
        return {
            "success": False,
            "registration_id": registration_id,
            "direction": direction,
            "message": str(error),
        }
    except Exception as error:
        return {
            "success": False,
            "registration_id": registration_id,
            "direction": direction,
            "message": f"학생 얼굴 프레임 추가 중 오류가 발생했습니다: {error}",
        }


@app.post("/students/registration-sessions/{registration_id}/complete", response_model=None)
def complete_student_registration_session(registration_id: str) -> dict[str, Any]:
    try:
        registration_session = student_registration_tracker.get(registration_id)
        if registration_session is None:
            return {
                "success": False,
                "message": "등록 세션을 찾을 수 없습니다.",
                "registration_id": registration_id,
            }

        if is_student_no_exists(registration_session.student_no):
            return {
                "success": False,
                "message": "이미 등록된 학번입니다.",
                "registration_id": registration_id,
                **student_registration_tracker.progress_payload(registration_session),
            }

        mean_embedding = student_registration_tracker.mean_embedding(registration_id)
        face_encoding = encoding_to_bytes(mean_embedding)
        save_result = add_student(
            student_no=registration_session.student_no,
            name=registration_session.name,
            department=registration_session.department,
            face_encoding=face_encoding,
        )

        if not save_result.get("success"):
            return {
                "success": False,
                "message": save_result.get("message", "학생 정보를 저장하지 못했습니다."),
                "registration_id": registration_id,
                **student_registration_tracker.progress_payload(registration_session),
            }

        progress = student_registration_tracker.progress_payload(registration_session)
        student_registration_tracker.complete(registration_id)

        return {
            "success": True,
            "message": "학생 등록이 완료되었습니다.",
            "registration_id": registration_id,
            "student": {
                "student_id": save_result["student_id"],
                "student_no": registration_session.student_no,
                "name": registration_session.name,
                "department": registration_session.department,
            },
            "frame_count": progress["accepted_count"],
            "direction_counts": progress["direction_counts"],
        }

    except ValueError as error:
        registration_session = student_registration_tracker.get(registration_id)
        progress = (
            student_registration_tracker.progress_payload(registration_session)
            if registration_session is not None
            else {}
        )
        return {
            "success": False,
            "message": str(error),
            "registration_id": registration_id,
            **progress,
        }
    except Exception as error:
        return {
            "success": False,
            "message": f"학생 등록 완료 처리 중 오류가 발생했습니다: {error}",
            "registration_id": registration_id,
        }


@app.delete("/students/registration-sessions/{registration_id}", response_model=None)
def cancel_student_registration_session(registration_id: str) -> dict[str, Any]:
    removed = student_registration_tracker.cancel(registration_id)
    return {
        "success": removed,
        "message": "학생 얼굴 등록 세션이 취소되었습니다." if removed else "등록 세션을 찾을 수 없습니다.",
        "registration_id": registration_id,
    }


@app.post("/students/enroll/start", response_model=None)
async def start_student_enrollment(
    request: Request,
    student_no: str | None = Form(None),
    name: str | None = Form(None),
    department: str | None = Form(None),
) -> dict[str, Any]:
    try:
        data = {} if student_no or name or department else await _read_request_data(request)
        resolved_student_no = str(student_no or data.get("student_no") or "").strip()
        resolved_name = str(name or data.get("name") or "").strip()
        department_value = department or data.get("department")
        resolved_department = str(department_value).strip() if department_value else None

        if not resolved_student_no:
            return {"success": False, "message": "학번을 입력해 주세요."}
        if not resolved_name:
            return {"success": False, "message": "이름을 입력해 주세요."}
        if is_student_no_exists(resolved_student_no) and not ALLOW_UPDATE_EXISTING_STUDENT_FACE:
            return {"success": False, "message": "이미 등록된 학번입니다."}

        result = enrollment_service.start_enrollment(
            student_no=resolved_student_no,
            name=resolved_name,
            department=resolved_department,
        )
        return result

    except Exception as error:
        return {
            "success": False,
            "message": f"얼굴 등록 세션 시작 중 오류가 발생했습니다: {error}",
        }


@app.post("/students/enroll/frame", response_model=None)
async def add_student_enrollment_frame(
    enroll_id: str = Form(...),
    pose: str = Form(...),
    image: UploadFile | None = File(None),
) -> dict[str, Any]:
    try:
        image_bytes = await _read_upload_image(image)
        result = extract_single_face_from_bytes(image_bytes, _get_detector(), build_encoding=False)

        if not result.success or result.face_crop is None:
            return _enroll_face_failure_response(result.faces, enroll_id=enroll_id, pose=pose)

        embedding = extract_face_encoding(result.face_crop)
        add_result = enrollment_service.add_frame(enroll_id=enroll_id, pose=pose, embedding=embedding)
        if not add_result.get("success"):
            return add_result

        return add_result

    except FileNotFoundError:
        return {
            "success": False,
            "status": "model_error",
            "message": "YOLO26 얼굴 검출 모델 파일을 찾을 수 없습니다.",
            "enroll_id": enroll_id,
            "pose": pose,
        }
    except RuntimeError as error:
        return {
            "success": False,
            "status": "runtime_error",
            "message": str(error),
            "enroll_id": enroll_id,
            "pose": pose,
        }
    except ValueError as error:
        return {
            "success": False,
            "status": "bad_request",
            "message": str(error),
            "enroll_id": enroll_id,
            "pose": pose,
        }
    except Exception as error:
        return {
            "success": False,
            "status": "server_error",
            "message": f"얼굴 프레임 등록 중 오류가 발생했습니다: {error}",
            "enroll_id": enroll_id,
            "pose": pose,
        }


@app.get("/students/enroll/{enroll_id}/status", response_model=None)
def get_student_enrollment_status(enroll_id: str) -> dict[str, Any]:
    return enrollment_service.get_status(enroll_id)


@app.post("/students/enroll/complete", response_model=None)
async def complete_student_enrollment(
    request: Request,
    enroll_id: str | None = Form(None),
) -> dict[str, Any]:
    try:
        data = {} if enroll_id else await _read_request_data(request)
        resolved_enroll_id = str(enroll_id or data.get("enroll_id") or "").strip()
        if not resolved_enroll_id:
            return {"success": False, "status": "bad_request", "message": "enroll_id를 입력해 주세요."}

        result = enrollment_service.complete_enrollment(resolved_enroll_id)
        if not result.get("success"):
            return result

        face_encoding = encoding_to_bytes(result["embedding"])
        student_no = result["student_no"]
        name = result["name"]
        department = result.get("department")

        if is_student_no_exists(student_no) and not ALLOW_UPDATE_EXISTING_STUDENT_FACE:
            return {
                "success": False,
                "message": "이미 등록된 학번입니다.",
                "enroll_id": resolved_enroll_id,
            }

        save_result = upsert_student_face(
            student_no=student_no,
            name=name,
            department=department,
            face_encoding=face_encoding,
        )

        if not save_result.get("success"):
            return {
                "success": False,
                "message": save_result.get("message", "학생 얼굴 정보를 저장하지 못했습니다."),
                "enroll_id": resolved_enroll_id,
            }

        enrollment_service.remove_enrollment(resolved_enroll_id)
        student = save_result.get("student") or {}
        return {
            "success": True,
            "status": "completed",
            "message": "학생 얼굴 등록이 완료되었습니다.",
            "enroll_id": resolved_enroll_id,
            "created": save_result.get("created", False),
            "updated": save_result.get("updated", False),
            "student": {
                "student_id": student.get("id", save_result.get("student_id")),
                "student_no": student.get("student_no", student_no),
                "name": student.get("name", name),
                "department": student.get("department", department),
            },
        }

    except Exception as error:
        return {
            "success": False,
            "message": f"학생 얼굴 등록 완료 중 오류가 발생했습니다: {error}",
        }


@app.delete("/students/enroll/{enroll_id}", response_model=None)
def cancel_student_enrollment(enroll_id: str) -> dict[str, Any]:
    return enrollment_service.cancel_enrollment(enroll_id)


@app.get("/students", response_model=None)
def students() -> dict[str, Any]:
    return {
        "success": True,
        "message": "학생 목록 조회 성공",
        "items": [_student_payload(student) for student in get_all_students()],
    }


@app.get("/students/{student_id}/stats", response_model=None)
def student_stats(student_id: int) -> dict[str, Any]:
    student = get_student_by_id(student_id)
    if student is None:
        return {
            "success": False,
            "status": "not_found",
            "message": "학생을 찾을 수 없습니다.",
            "student": None,
            "stats": {
                "attendance_count": 0,
                "late_count": 0,
                "absence_count": 0,
            },
        }

    stats = get_student_attendance_stats(student_id)
    if not stats.get("success"):
        return {
            "success": False,
            "status": "server_error",
            "message": stats.get("message", "학생 통계를 조회하지 못했습니다."),
            "student": _student_payload(student),
            "stats": {
                "attendance_count": 0,
                "late_count": 0,
                "absence_count": 0,
            },
        }

    return {
        "success": True,
        "message": "학생 통계 조회 성공",
        "student": _student_payload(student),
        "stats": {
            "attendance_count": stats.get("attendance_count", 0),
            "late_count": stats.get("late_count", 0),
            "absence_count": stats.get("absence_count", 0),
        },
    }

@app.post("/students")
async def create_student(
    student_no: str = Form(...),
    name: str = Form(...),
    department: str | None = Form(None),
    image: UploadFile | None = File(None),
) -> dict[str, Any]:
    try:
        if is_student_no_exists(student_no):
            return {
                "success": False,
                "message": "이미 등록된 학번입니다.",
            }

        image_bytes = await _read_upload_image(image)
        result = extract_single_face_from_bytes(image_bytes, _get_detector(), build_encoding=True)

        if not result.success:
            return _face_failure_response(result.faces)

        save_result = add_student(student_no, name, department, result.face_encoding or b"")
        if not save_result.get("success"):
            return {
                "success": False,
                "message": save_result.get("message", "학생 등록에 실패했습니다."),
            }

        return {
            "success": True,
            "message": "학생 등록이 완료되었습니다.",
            "student": {
                "student_id": save_result["student_id"],
                "student_no": student_no,
                "name": name,
                "department": department,
            },
        }

    except FileNotFoundError:
        return {"success": False, "message": "YOLO26 얼굴 검출 모델 파일을 찾을 수 없습니다."}
    except RuntimeError as error:
        return {"success": False, "message": str(error)}
    except ValueError as error:
        return {"success": False, "message": str(error)}
    except Exception as error:
        return {"success": False, "message": f"학생 등록 중 오류가 발생했습니다: {error}"}


@app.post("/attendance/recognize")
async def recognize_attendance(
    image: UploadFile | None = File(None),
    session_id: int | None = Form(None),
) -> dict[str, Any]:
    try:
        recognition_tracker.cleanup()

        image_bytes = await _read_upload_image(image)
        face_result = extract_single_face_from_bytes(image_bytes, _get_detector())

        if not face_result.success or face_result.face_crop is None:
            return _face_failure_response(face_result.faces)

        recognition = _get_recognizer().recognize(face_result.face_crop)
        recognition_data = _recognition_payload(recognition)

        if not recognition["matched"]:
            recognition_tracker.reset()
            return {
                "success": True,
                "matched": False,
                "status": "unknown",
                "message": "미등록 사용자",
                "recognition": recognition_data,
            }

        student = get_student_by_id(recognition["student_id"])
        if student is None:
            recognition_tracker.reset()
            return {
                "success": True,
                "matched": False,
                "status": "unknown",
                "message": "미등록 사용자",
                "recognition": recognition_data,
            }

        session = _get_request_session(session_id)
        if session is None:
            recognition_tracker.reset()
            return {
                "success": False,
                "matched": True,
                "status": "no_active_session",
                "message": "활성 출석 세션이 없습니다.",
                "student": _student_payload(student),
                "recognition": recognition_data,
                "attendance": {
                    "marked": False,
                    "message": "출석 세션 없음",
                },
            }

        resolved_session_id = int(session["session_id"])
        session_data = _session_payload(session)
        student_id = int(student["id"])
        student_data = _student_payload(student)

        if has_attended(student_id, resolved_session_id):
            recognition_tracker.mark_attended(student_id, session_id=resolved_session_id)
            return {
                "success": True,
                "matched": True,
                "status": "already_attended",
                "message": "이미 출석함",
                "session": session_data,
                "student": student_data,
                "recognition": recognition_data,
                "attendance": {
                    "marked": False,
                    "message": "이미 출석함",
                },
            }

        progress = recognition_tracker.update(
            session_id=resolved_session_id,
            student_id=student_id,
        )

        if not progress["ready"]:
            return {
                "success": True,
                "matched": True,
                "status": "recognizing",
                "message": "인식 중",
                "hold_seconds": progress["hold_seconds"],
                "elapsed_seconds": round(progress["elapsed_seconds"], 3),
                "remaining_seconds": round(progress["remaining_seconds"], 3),
                "session": session_data,
                "student": student_data,
                "recognition": recognition_data,
                "attendance": {
                    "marked": False,
                    "message": "미출석",
                },
            }

        face_confidence = None
        if face_result.face is not None:
            face_confidence = face_result.face.get("confidence")

        attendance_result = mark_attendance(
            student_id,
            session_id=resolved_session_id,
            confidence=face_confidence,
            distance=recognition.get("distance"),
        )
        recognition_tracker.mark_attended(student_id, session_id=resolved_session_id)

        if not attendance_result.get("success"):
            return {
                "success": False,
                "matched": True,
                "status": "attendance_error",
                "message": attendance_result.get("message", "출석 처리 오류"),
                "session": session_data,
                "student": student_data,
                "recognition": recognition_data,
                "attendance": {
                    "marked": False,
                    "message": attendance_result.get("message", "출석 처리 오류"),
                },
            }

        attendance_message = attendance_result.get("message", "출석 완료")
        status = "already_attended" if attendance_message == "이미 출석함" else "attended"
        return {
            "success": True,
            "matched": True,
            "status": status,
            "message": attendance_message,
            "hold_seconds": progress["hold_seconds"],
            "elapsed_seconds": round(progress["elapsed_seconds"], 3),
            "remaining_seconds": 0.0,
            "session": session_data,
            "student": student_data,
            "recognition": recognition_data,
            "attendance": {
                "marked": status == "attended",
                "message": attendance_message,
            },
        }

    except FileNotFoundError:
        return {
            "success": False,
            "matched": False,
            "status": "model_error",
            "message": "YOLO26 얼굴 검출 모델 파일을 찾을 수 없습니다.",
        }
    except RuntimeError as error:
        return {"success": False, "matched": False, "status": "runtime_error", "message": str(error)}
    except ValueError as error:
        return {"success": False, "matched": False, "status": "bad_request", "message": str(error)}
    except Exception as error:
        return {
            "success": False,
            "matched": False,
            "status": "server_error",
            "message": f"출석 인식 중 오류가 발생했습니다: {error}",
        }


@app.get("/attendance/today")
def attendance_today() -> dict[str, Any]:
    result = get_attendance_records(date=datetime.now().date().isoformat())
    return _attendance_response(result)


@app.get("/attendance", response_model=None)
def attendance(session_id: int | None = None, date: str | None = None) -> dict[str, Any]:
    try:
        normalized_date = _validate_date(date)
    except ValueError:
        return {
            "success": False,
            "message": "날짜 형식은 YYYY-MM-DD여야 합니다.",
            "session": None,
            "items": [],
        }

    result = get_attendance_records(session_id=session_id, date=normalized_date)
    return _attendance_response(result)


@app.get("/attendance/export", response_model=None)
def attendance_export(session_id: int | None = None, date: str | None = None):
    try:
        normalized_date = _validate_date(date)
    except ValueError:
        return {"success": False, "message": "날짜 형식은 YYYY-MM-DD여야 합니다."}

    result = get_attendance_records(session_id=session_id, date=normalized_date)
    if not result.get("success"):
        return {
            "success": False,
            "message": result.get("message", "출석 기록을 조회할 수 없습니다."),
        }

    file_name = _csv_file_name(session_id, normalized_date, result.get("session"))
    file_path = _export_attendance_csv(result, file_name)
    return FileResponse(
        path=str(file_path),
        media_type="text/csv; charset=utf-8",
        filename=file_path.name,
    )
