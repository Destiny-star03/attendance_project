from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent

DATA_DIR = BASE_DIR / "data"
DB_PATH = DATA_DIR / "students.db"

MODEL_DIR = BASE_DIR / "models"
YOLO_MODEL_PATH = MODEL_DIR / "yolo26_face.pt"

KNOWN_FACES_DIR = DATA_DIR / "known_faces"
EXPORT_DIR = DATA_DIR / "exports"
LOG_DIR = DATA_DIR / "logs"

CAMERA_INDEX = 0

FACE_DETECTION_CONFIDENCE = 0.5
FACE_RECOGNITION_MODEL = "facenet-vggface2"
FACE_RECOGNITION_THRESHOLD = 1.0
FACE_RECOGNITION_MARGIN = 0.20
ATTENDANCE_HOLD_SECONDS = 3.0
TRACKER_TIMEOUT_SECONDS = 5.0
REGISTRATION_DIRECTIONS = ["front", "left", "right", "up", "down"]
REGISTRATION_MIN_FRAMES_PER_DIRECTION = 1
REGISTRATION_MAX_FRAMES_PER_DIRECTION = 5
REGISTRATION_SESSION_TIMEOUT_SECONDS = 300.0
REQUIRED_ENROLL_POSES = ["front", "left", "right", "up", "down"]
ENROLL_SESSION_TIMEOUT_SECONDS = 600
ALLOW_UPDATE_EXISTING_STUDENT_FACE = True

FRAME_WIDTH = 1280
FRAME_HEIGHT = 720


def ensure_directories() -> None:
    required_dirs = [
        DATA_DIR,
        KNOWN_FACES_DIR,
        EXPORT_DIR,
        LOG_DIR,
        MODEL_DIR,
    ]

    for directory in required_dirs:
        directory.mkdir(parents=True, exist_ok=True)
