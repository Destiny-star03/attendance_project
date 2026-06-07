from __future__ import annotations

import time
import uuid
from dataclasses import dataclass, field
from typing import Any

import numpy as np

from config import REQUIRED_ENROLL_POSES, ENROLL_SESSION_TIMEOUT_SECONDS


@dataclass
class EnrollmentSession:
    enroll_id: str
    student_no: str
    name: str
    department: str | None
    required_poses: list[str]
    created_at: float
    updated_at: float
    embeddings: dict[str, np.ndarray] = field(default_factory=dict)


class EnrollmentService:
    """실시간 다중 포즈 얼굴 등록 세션을 메모리에서 관리한다."""

    def __init__(
        self,
        required_poses: list[str] | None = None,
        timeout_seconds: float = ENROLL_SESSION_TIMEOUT_SECONDS,
    ) -> None:
        self.required_poses = [pose.strip().lower() for pose in (required_poses or REQUIRED_ENROLL_POSES)]
        self.timeout_seconds = float(timeout_seconds)
        self._sessions: dict[str, EnrollmentSession] = {}

    def start_enrollment(
        self,
        student_no: str,
        name: str,
        department: str | None = None,
    ) -> dict[str, Any]:
        """학생 정보와 필수 pose 목록을 가진 등록 세션을 시작한다."""
        self.cleanup_expired_sessions()

        normalized_student_no = student_no.strip()
        normalized_name = name.strip()
        normalized_department = department.strip() if department else None

        enroll_id = uuid.uuid4().hex
        now = time.time()
        session = EnrollmentSession(
            enroll_id=enroll_id,
            student_no=normalized_student_no,
            name=normalized_name,
            department=normalized_department,
            required_poses=list(self.required_poses),
            created_at=now,
            updated_at=now,
            embeddings={},
        )
        self._sessions[enroll_id] = session

        return {
            "success": True,
            "status": "started",
            "message": "얼굴 등록 세션이 시작되었습니다.",
            "enroll_id": enroll_id,
            **self._status_payload(session),
        }

    def add_frame(self, enroll_id: str, pose: str, embedding: np.ndarray) -> dict[str, Any]:
        """pose별 최신 얼굴 임베딩을 저장하고 Android가 필요한 진행 상태를 반환한다."""
        session = self._get_active_session(enroll_id)
        if session is None:
            return {
                "success": False,
                "status": "invalid_enroll_id",
                "message": "얼굴 등록 세션을 찾을 수 없습니다.",
                "enroll_id": enroll_id,
            }

        normalized_pose = pose.strip().lower()
        if normalized_pose not in session.required_poses:
            return {
                "success": False,
                "status": "invalid_pose",
                "message": "지원하지 않는 얼굴 방향입니다.",
                "enroll_id": enroll_id,
                "pose": normalized_pose,
                **self._status_payload(session),
            }

        if normalized_pose in session.embeddings:
            return {
                "success": False,
                "status": "already_completed",
                "message": f"{normalized_pose} 얼굴 방향은 이미 등록되었습니다.",
                "enroll_id": enroll_id,
                "pose": normalized_pose,
                **self._status_payload(session),
            }

        session.embeddings[normalized_pose] = self._normalize_embedding(embedding)
        session.updated_at = time.time()

        status = self._status_payload(session)
        response_status = "ready_to_complete" if status["ready_to_complete"] else "accepted"
        response_message = (
            "모든 얼굴 방향 등록이 완료되었습니다."
            if status["ready_to_complete"]
            else f"{normalized_pose} 얼굴이 등록되었습니다."
        )

        return {
            "success": True,
            "status": response_status,
            "message": response_message,
            "enroll_id": enroll_id,
            "pose": normalized_pose,
            **status,
        }

    def get_status(self, enroll_id: str) -> dict[str, Any]:
        """현재 등록 진행 상태를 반환한다."""
        session = self._get_active_session(enroll_id)
        if session is None:
            return {
                "success": False,
                "status": "invalid_enroll_id",
                "message": "얼굴 등록 세션을 찾을 수 없습니다.",
                "enroll_id": enroll_id,
            }

        status = self._status_payload(session)
        return {
            "success": True,
            "status": "ready_to_complete" if status["ready_to_complete"] else "active",
            "message": "등록 상태 조회 성공",
            "enroll_id": enroll_id,
            "student_no": session.student_no,
            "name": session.name,
            "department": session.department,
            **status,
        }

    def complete_enrollment(self, enroll_id: str) -> dict[str, Any]:
        """필수 pose가 모두 수집되었는지 확인하고 평균 임베딩을 반환한다."""
        session = self._get_active_session(enroll_id)
        if session is None:
            return {
                "success": False,
                "status": "invalid_enroll_id",
                "message": "얼굴 등록 세션을 찾을 수 없습니다.",
                "enroll_id": enroll_id,
            }

        remaining_poses = self._remaining_poses(session)
        if remaining_poses:
            return {
                "success": False,
                "status": "incomplete",
                "message": "아직 완료되지 않은 자세가 있습니다.",
                "enroll_id": enroll_id,
                **self._status_payload(session),
            }

        embeddings = [session.embeddings[pose] for pose in session.required_poses]
        mean_embedding = np.mean(np.stack(embeddings), axis=0).astype(np.float32)
        normalized_mean_embedding = self._normalize_embedding(mean_embedding)

        return {
            "success": True,
            "status": "completed",
            "message": "얼굴 등록 임베딩 생성이 완료되었습니다.",
            "enroll_id": enroll_id,
            "student_no": session.student_no,
            "name": session.name,
            "department": session.department,
            "embedding": normalized_mean_embedding,
            **self._status_payload(session),
        }

    def cancel_enrollment(self, enroll_id: str) -> dict[str, Any]:
        """진행 중 등록 세션을 삭제한다."""
        self.cleanup_expired_sessions()
        removed = self._sessions.pop(enroll_id, None) is not None
        return {
            "success": removed,
            "status": "cancelled" if removed else "invalid_enroll_id",
            "message": "얼굴 등록 세션이 취소되었습니다." if removed else "얼굴 등록 세션을 찾을 수 없습니다.",
            "enroll_id": enroll_id,
        }

    def remove_enrollment(self, enroll_id: str) -> None:
        """DB 저장이 끝난 등록 세션을 메모리에서 제거한다."""
        self._sessions.pop(enroll_id, None)

    def cleanup_expired_sessions(self) -> dict[str, Any]:
        """마지막 갱신 이후 timeout이 지난 세션을 제거한다."""
        now = time.time()
        expired_ids = [
            enroll_id
            for enroll_id, session in self._sessions.items()
            if now - session.updated_at > self.timeout_seconds
        ]

        for enroll_id in expired_ids:
            self._sessions.pop(enroll_id, None)

        return {
            "success": True,
            "removed_count": len(expired_ids),
            "removed_enroll_ids": expired_ids,
        }

    def _get_active_session(self, enroll_id: str) -> EnrollmentSession | None:
        self.cleanup_expired_sessions()
        return self._sessions.get(enroll_id)

    def _completed_poses(self, session: EnrollmentSession) -> list[str]:
        return [pose for pose in session.required_poses if pose in session.embeddings]

    def _remaining_poses(self, session: EnrollmentSession) -> list[str]:
        return [pose for pose in session.required_poses if pose not in session.embeddings]

    def _progress(self, session: EnrollmentSession) -> int:
        if not session.required_poses:
            return 0
        completed = len(self._completed_poses(session))
        return int((completed / len(session.required_poses)) * 100)

    def _status_payload(self, session: EnrollmentSession) -> dict[str, Any]:
        completed_poses = self._completed_poses(session)
        remaining_poses = self._remaining_poses(session)
        return {
            "required_poses": list(session.required_poses),
            "completed_poses": completed_poses,
            "remaining_poses": remaining_poses,
            "next_pose": remaining_poses[0] if remaining_poses else None,
            "progress": self._progress(session),
            "ready_to_complete": not remaining_poses,
        }

    @staticmethod
    def _normalize_embedding(embedding: np.ndarray) -> np.ndarray:
        normalized = embedding.astype(np.float32).reshape(-1)
        norm = np.linalg.norm(normalized)
        if norm == 0:
            raise ValueError("얼굴 임베딩이 올바르지 않습니다.")
        return normalized / norm
