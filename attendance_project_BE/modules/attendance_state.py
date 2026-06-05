from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Any

from config import ATTENDANCE_HOLD_SECONDS, TRACKER_TIMEOUT_SECONDS


TrackerKey = tuple[int, int]


@dataclass
class RecognitionState:
    session_id: int
    student_id: int
    first_seen: float
    last_seen: float


class AttendanceRecognitionTracker:
    """출석 확정 전 3초 유지 여부를 판단하는 서버 메모리 상태 관리자."""

    def __init__(
        self,
        hold_seconds: float = ATTENDANCE_HOLD_SECONDS,
        timeout_seconds: float = TRACKER_TIMEOUT_SECONDS,
    ) -> None:
        self.hold_seconds = float(hold_seconds)
        self.timeout_seconds = float(timeout_seconds)
        self._states: dict[TrackerKey, RecognitionState] = {}

    def update(self, session_id: int, student_id: int) -> dict[str, Any]:
        """같은 수업 세션에서 같은 학생 얼굴이 계속 들어오는지 갱신한다."""
        now = time.monotonic()
        self.cleanup(now=now)

        key = self._make_key(session_id, student_id)
        state = self._states.get(key)
        if state is None:
            state = RecognitionState(
                session_id=int(session_id),
                student_id=int(student_id),
                first_seen=now,
                last_seen=now,
            )
            self._states[key] = state
        else:
            state.last_seen = now

        return self._build_result(state, now=now)

    def reset(self, session_id: int | None = None, student_id: int | None = None) -> None:
        """얼굴 없음, 미등록 사용자, 여러 얼굴 감지 시 관련 추적 상태를 제거한다."""
        if session_id is None and student_id is None:
            self._states.clear()
            return

        remove_keys = []
        for key, state in self._states.items():
            if session_id is not None and state.session_id != int(session_id):
                continue
            if student_id is not None and state.student_id != int(student_id):
                continue
            remove_keys.append(key)

        for key in remove_keys:
            self._states.pop(key, None)

    def cleanup(self, now: float | None = None) -> None:
        """마지막 인식 이후 timeout_seconds가 지난 오래된 추적 상태를 제거한다."""
        current_time = time.monotonic() if now is None else now
        stale_keys = [
            key
            for key, state in self._states.items()
            if current_time - state.last_seen > self.timeout_seconds
        ]

        for key in stale_keys:
            self._states.pop(key, None)

    def get_state(self, session_id: int, student_id: int) -> dict[str, Any] | None:
        """현재 추적 상태를 조회한다. 없으면 None을 반환한다."""
        self.cleanup()
        state = self._states.get(self._make_key(session_id, student_id))
        if state is None:
            return None
        return self._build_result(state)

    def is_ready(self, session_id: int, student_id: int) -> bool:
        """3초 이상 유지되어 출석 처리 가능한 상태인지 확인한다."""
        state = self.get_state(session_id, student_id)
        return bool(state and state["ready"])

    def mark_attended(
        self,
        student_id: int,
        session_id: int = 0,
    ) -> None:
        """DB 저장이 끝난 학생의 임시 추적 상태는 더 이상 유지하지 않는다."""
        self.reset(session_id=session_id, student_id=student_id)

    def reset_if_missing(self) -> None:
        """기존 호출부 호환용: 얼굴이 없으면 전체 추적 상태를 초기화한다."""
        self.reset()

    def update_for_session(
        self,
        session_id: int,
        student_id: int,
        *,
        reset_on_identity_change: bool = False,
    ) -> dict[str, Any]:
        """기존 호출부 호환용 래퍼."""
        if reset_on_identity_change:
            for key, state in list(self._states.items()):
                if state.session_id == int(session_id) and state.student_id != int(student_id):
                    self._states.pop(key, None)

        result = self.update(session_id=session_id, student_id=student_id)
        result["should_mark"] = result["ready"]
        return result

    def cleanup_stale(self, now: float | None = None) -> None:
        """기존 호출부 호환용 래퍼."""
        self.cleanup(now=now)

    def should_mark_attendance(self, student_id: int, session_id: int = 0) -> bool:
        """기존 호출부 호환용 래퍼."""
        return self.is_ready(session_id=session_id, student_id=student_id)

    def get_elapsed(self, student_id: int, session_id: int = 0) -> float:
        """기존 호출부 호환용 래퍼."""
        state = self.get_state(session_id=session_id, student_id=student_id)
        if state is None:
            return 0.0
        return float(state["elapsed_seconds"])

    def _build_result(self, state: RecognitionState, now: float | None = None) -> dict[str, Any]:
        current_time = time.monotonic() if now is None else now
        elapsed_seconds = max(0.0, current_time - state.first_seen)
        remaining_seconds = max(0.0, self.hold_seconds - elapsed_seconds)
        ready = elapsed_seconds >= self.hold_seconds

        return {
            "session_id": state.session_id,
            "student_id": state.student_id,
            "elapsed_seconds": elapsed_seconds,
            "remaining_seconds": remaining_seconds,
            "hold_seconds": self.hold_seconds,
            "ready": ready,
        }

    @staticmethod
    def _make_key(session_id: int, student_id: int) -> TrackerKey:
        return int(session_id), int(student_id)
