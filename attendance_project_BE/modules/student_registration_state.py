from __future__ import annotations

import time
import uuid
from dataclasses import dataclass, field
from typing import Any

import numpy as np

from config import (
    REGISTRATION_DIRECTIONS,
    REGISTRATION_MAX_FRAMES_PER_DIRECTION,
    REGISTRATION_MIN_FRAMES_PER_DIRECTION,
    REGISTRATION_SESSION_TIMEOUT_SECONDS,
)


@dataclass
class StudentRegistrationSession:
    registration_id: str
    student_no: str
    name: str
    department: str | None
    created_at: float
    updated_at: float
    embeddings_by_direction: dict[str, list[np.ndarray]] = field(default_factory=dict)


class StudentRegistrationTracker:
    def __init__(
        self,
        directions: list[str] | None = None,
        min_frames_per_direction: int = REGISTRATION_MIN_FRAMES_PER_DIRECTION,
        max_frames_per_direction: int = REGISTRATION_MAX_FRAMES_PER_DIRECTION,
        timeout_seconds: float = REGISTRATION_SESSION_TIMEOUT_SECONDS,
    ) -> None:
        self.directions = list(directions or REGISTRATION_DIRECTIONS)
        self.min_frames_per_direction = int(min_frames_per_direction)
        self.max_frames_per_direction = int(max_frames_per_direction)
        self.timeout_seconds = float(timeout_seconds)
        self._sessions: dict[str, StudentRegistrationSession] = {}

    def start(self, student_no: str, name: str, department: str | None = None) -> dict[str, Any]:
        self.cleanup()
        now = time.time()
        registration_id = uuid.uuid4().hex
        session = StudentRegistrationSession(
            registration_id=registration_id,
            student_no=student_no.strip(),
            name=name.strip(),
            department=department.strip() if department else None,
            created_at=now,
            updated_at=now,
            embeddings_by_direction={direction: [] for direction in self.directions},
        )
        self._sessions[registration_id] = session
        return self.to_payload(session)

    def get(self, registration_id: str) -> StudentRegistrationSession | None:
        self.cleanup()
        return self._sessions.get(registration_id)

    def cancel(self, registration_id: str) -> bool:
        self.cleanup()
        return self._sessions.pop(registration_id, None) is not None

    def add_embedding(
        self,
        registration_id: str,
        direction: str,
        embedding: np.ndarray,
    ) -> dict[str, Any]:
        session = self.get(registration_id)
        if session is None:
            return {
                "success": False,
                "message": "등록 세션을 찾을 수 없습니다.",
            }

        normalized_direction = direction.strip().lower()
        if normalized_direction not in self.directions:
            return {
                "success": False,
                "message": "지원하지 않는 얼굴 방향입니다.",
                "directions": self.directions,
            }

        direction_embeddings = session.embeddings_by_direction.setdefault(normalized_direction, [])
        if len(direction_embeddings) >= self.max_frames_per_direction:
            return {
                "success": False,
                "message": "해당 방향의 얼굴 프레임을 이미 충분히 수집했습니다.",
                **self.progress_payload(session),
                "direction": normalized_direction,
            }

        direction_embeddings.append(self._normalize_embedding(embedding))
        session.updated_at = time.time()

        return {
            "success": True,
            "message": "얼굴 프레임이 등록 세션에 추가되었습니다.",
            **self.progress_payload(session),
            "direction": normalized_direction,
        }

    def is_complete(self, session: StudentRegistrationSession) -> bool:
        return not self.missing_directions(session)

    def missing_directions(self, session: StudentRegistrationSession) -> list[str]:
        counts = self.direction_counts(session)
        return [
            direction
            for direction in self.directions
            if counts.get(direction, 0) < self.min_frames_per_direction
        ]

    def direction_counts(self, session: StudentRegistrationSession) -> dict[str, int]:
        return {
            direction: len(session.embeddings_by_direction.get(direction, []))
            for direction in self.directions
        }

    def completed_directions(self, session: StudentRegistrationSession) -> list[str]:
        counts = self.direction_counts(session)
        return [
            direction
            for direction in self.directions
            if counts.get(direction, 0) >= self.min_frames_per_direction
        ]

    def frame_count(self, session: StudentRegistrationSession) -> int:
        return sum(self.direction_counts(session).values())

    def mean_embedding(self, registration_id: str) -> np.ndarray:
        session = self.get(registration_id)
        if session is None:
            raise ValueError("등록 세션을 찾을 수 없습니다.")

        missing = self.missing_directions(session)
        if missing:
            raise ValueError(f"필수 얼굴 방향이 아직 부족합니다: {', '.join(missing)}")

        embeddings = [
            embedding
            for direction in self.directions
            for embedding in session.embeddings_by_direction.get(direction, [])
        ]
        if not embeddings:
            raise ValueError("사용 가능한 얼굴 프레임이 없습니다.")

        mean_embedding = np.mean(np.stack(embeddings), axis=0).astype(np.float32)
        return self._normalize_embedding(mean_embedding)

    def complete(self, registration_id: str) -> StudentRegistrationSession | None:
        return self._sessions.pop(registration_id, None)

    def cleanup(self, now: float | None = None) -> None:
        current_time = time.time() if now is None else now
        stale_ids = [
            registration_id
            for registration_id, session in self._sessions.items()
            if current_time - session.updated_at > self.timeout_seconds
        ]
        for registration_id in stale_ids:
            self._sessions.pop(registration_id, None)

    def to_payload(self, session: StudentRegistrationSession) -> dict[str, Any]:
        return {
            "registration_id": session.registration_id,
            "student_no": session.student_no,
            "name": session.name,
            "department": session.department,
            "directions": self.directions,
            **self.progress_payload(session),
        }

    def progress_payload(self, session: StudentRegistrationSession) -> dict[str, Any]:
        counts = self.direction_counts(session)
        completed = self.completed_directions(session)
        missing = self.missing_directions(session)
        return {
            "accepted_count": self.frame_count(session),
            "direction_counts": counts,
            "completed_directions": completed,
            "missing_directions": missing,
            "is_complete": not missing,
        }

    @staticmethod
    def _normalize_embedding(embedding: np.ndarray) -> np.ndarray:
        normalized = embedding.astype(np.float32).reshape(-1)
        norm = np.linalg.norm(normalized)
        if norm == 0:
            raise ValueError("얼굴 임베딩이 올바르지 않습니다.")
        return normalized / norm
