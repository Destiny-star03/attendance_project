from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

import cv2
import numpy as np

from config import ATTENDANCE_HOLD_SECONDS, CAMERA_INDEX, FRAME_HEIGHT, FRAME_WIDTH
from modules.attendance_service import get_or_create_active_session, has_attended, mark_attendance
from modules.attendance_state import AttendanceRecognitionTracker
from modules.detector import FaceDetector
from modules.face_pipeline import crop_face
from modules.recognizer import FaceRecognizer
from modules.student_service import get_student_by_id


WINDOW_NAME = "YOLO26 Attendance"
FONT_PATH = Path("C:/Windows/Fonts/malgun.ttf")


def _draw_text(
    frame: np.ndarray,
    text: str,
    position: tuple[int, int],
    color: tuple[int, int, int] = (0, 255, 0),
    font_size: int = 22,
) -> np.ndarray:
    try:
        from PIL import Image, ImageDraw, ImageFont

        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        image = Image.fromarray(rgb_frame)
        draw = ImageDraw.Draw(image)
        font = ImageFont.truetype(str(FONT_PATH), font_size) if FONT_PATH.exists() else ImageFont.load_default()
        b, g, r = color
        draw.text(position, text, font=font, fill=(r, g, b))
        return cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
    except Exception:
        cv2.putText(frame, text, position, cv2.FONT_HERSHEY_SIMPLEX, 0.7, color, 2, cv2.LINE_AA)
        return frame


def _draw_status_panel(frame: np.ndarray, lines: list[str]) -> np.ndarray:
    panel_height = 38 + len(lines) * 28
    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (frame.shape[1], panel_height), (0, 0, 0), -1)
    frame = cv2.addWeighted(overlay, 0.55, frame, 0.45, 0)

    for index, line in enumerate(lines):
        frame = _draw_text(frame, line, (16, 18 + index * 28), color=(255, 255, 255), font_size=21)

    return frame


def _draw_face_result(
    frame: np.ndarray,
    box: list[int],
    label_lines: list[str],
    matched: bool,
) -> np.ndarray:
    x1, y1, x2, y2 = box
    color = (0, 255, 0) if matched else (0, 0, 255)
    cv2.rectangle(frame, (x1, y1), (x2, y2), color, 2)

    text_y = max(130, y1 - (len(label_lines) * 26) - 8)
    for index, line in enumerate(label_lines):
        frame = _draw_text(frame, line, (max(0, x1), text_y + index * 26), color=color, font_size=21)

    return frame


def _build_registered_label(
    student: dict[str, Any],
    status_message: str,
    progress_message: str | None = None,
) -> list[str]:
    lines = [
        f"이름: {student.get('name', '-')}",
        f"학번: {student.get('student_no', '-')}",
        f"상태: {status_message}",
    ]
    if progress_message:
        lines.append(progress_message)
    return lines


def _format_distance(distance: Any) -> str:
    return "-" if distance is None else f"{float(distance):.3f}"


def run_attendance(camera_index: int = CAMERA_INDEX) -> int:
    try:
        detector = FaceDetector()
        recognizer = FaceRecognizer()
        active_session = get_or_create_active_session()
    except FileNotFoundError:
        print("모델 오류: YOLO26 얼굴 검출 모델 파일을 찾을 수 없습니다.")
        print("학습 결과 best.pt를 models/yolo26_face.pt로 배치한 뒤 다시 실행해 주세요.")
        return 1
    except Exception as error:
        print(f"초기화 오류: 프로그램을 시작할 수 없습니다. ({error})")
        return 1

    capture = cv2.VideoCapture(camera_index)
    if not capture.isOpened():
        print(f"웹캠 오류: 카메라를 열 수 없습니다. camera_index={camera_index}")
        return 1

    capture.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH)
    capture.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT)

    tracker = AttendanceRecognitionTracker(ATTENDANCE_HOLD_SECONDS)
    session_id = int(active_session["session_id"])
    session_name = active_session.get("session_name", "-")
    last_status = "대기 중"

    print("출석 프로그램을 시작합니다. 종료하려면 ESC 키를 누르세요.")
    print(f"활성 출석 세션: {session_name} (session_id={session_id})")

    try:
        while True:
            success, frame = capture.read()
            if not success or frame is None:
                print("웹캠 오류: 프레임을 읽을 수 없습니다.")
                break

            try:
                faces = detector.detect_faces(frame)
            except Exception as error:
                faces = []
                tracker.reset_if_missing()
                last_status = f"얼굴 검출 오류: {error}"

            if not faces:
                tracker.reset_if_missing()
                last_status = "얼굴 없음"

            if len(faces) >= 2:
                tracker.reset_if_missing()
                last_status = "여러 얼굴 감지"
                frame = _draw_status_panel(
                    frame,
                    [
                        "카메라 실행 중",
                        f"출석 세션: {session_name} ({session_id})",
                        f"검출 얼굴 수: {len(faces)}",
                        f"최근 상태: {last_status}",
                        "ESC: 종료",
                    ],
                )
                cv2.imshow(WINDOW_NAME, frame)
                if cv2.waitKey(1) & 0xFF == 27:
                    break
                continue

            for face in faces[:1]:
                box = face["box"]
                confidence = face["confidence"]

                try:
                    face_crop = crop_face(frame, box)
                    recognition = recognizer.recognize(face_crop)
                except Exception as error:
                    tracker.reset_if_missing()
                    last_status = f"인식 오류: {error}"
                    frame = _draw_face_result(frame, box, [last_status], matched=False)
                    continue

                if not recognition["matched"]:
                    tracker.reset_if_missing()
                    distance_text = _format_distance(recognition.get("distance"))
                    last_status = "미등록 사용자"
                    frame = _draw_face_result(
                        frame,
                        box,
                        [
                            "미등록 사용자",
                            f"검출 신뢰도: {confidence:.2f}",
                            f"인식 거리: {distance_text}",
                        ],
                        matched=False,
                    )
                    continue

                student_id = int(recognition["student_id"])
                student = get_student_by_id(student_id)
                if student is None:
                    tracker.reset_if_missing()
                    last_status = "학생 정보 조회 실패"
                    frame = _draw_face_result(frame, box, ["미등록 사용자"], matched=False)
                    continue

                if has_attended(student_id, session_id):
                    tracker.mark_attended(student_id, session_id=session_id)
                    last_status = f"{student.get('name', '-')} - 이미 출석함"
                    frame = _draw_face_result(
                        frame,
                        box,
                        _build_registered_label(student, "이미 출석함"),
                        matched=True,
                    )
                    continue

                progress = tracker.update(session_id=session_id, student_id=student_id)
                elapsed = progress["elapsed_seconds"]
                hold_seconds = progress["hold_seconds"]

                if progress["ready"]:
                    attendance_result = mark_attendance(
                        student_id,
                        session_id=session_id,
                        confidence=confidence,
                        distance=recognition.get("distance"),
                    )
                    tracker.mark_attended(student_id, session_id=session_id)
                    attendance_message = attendance_result.get("message", "출석 처리 오류")
                    last_status = f"{student.get('name', '-')} - {attendance_message}"
                    frame = _draw_face_result(
                        frame,
                        box,
                        _build_registered_label(student, attendance_message),
                        matched=attendance_result.get("success", False),
                    )
                    continue

                progress_text = f"인식 중... {elapsed:.1f}/{hold_seconds:.1f}초"
                last_status = f"{student.get('name', '-')} - {progress_text}"
                frame = _draw_face_result(
                    frame,
                    box,
                    _build_registered_label(student, "인식 중", progress_text),
                    matched=True,
                )

            frame = _draw_status_panel(
                frame,
                [
                    "카메라 실행 중",
                    f"출석 세션: {session_name} ({session_id})",
                    f"검출 얼굴 수: {len(faces)}",
                    f"최근 상태: {last_status}",
                    "ESC: 종료",
                ],
            )

            cv2.imshow(WINDOW_NAME, frame)

            if cv2.waitKey(1) & 0xFF == 27:
                break

    except KeyboardInterrupt:
        print("사용자 요청으로 종료합니다.")
    finally:
        capture.release()
        cv2.destroyAllWindows()

    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run webcam attendance recognition.")
    parser.add_argument("--camera", type=int, default=CAMERA_INDEX, help="OpenCV camera index.")
    args = parser.parse_args()

    raise SystemExit(run_attendance(args.camera))
