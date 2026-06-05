# Android /attendance/recognize 클라이언트 처리 설계

이 문서는 FastAPI 서버의 `POST /attendance/recognize` 응답이 3초 유지 출석 방식으로 변경된 것에 맞춰 Android 앱에서 수정해야 할 data class와 UI 처리 로직을 정리한다.

현재 저장소에는 Android Studio 프로젝트 파일이 없으므로, 아래 코드는 Android 프로젝트에 추가하거나 기존 파일에 맞춰 옮겨 적용한다.

## 1. 처리 흐름

Android 앱은 카메라 프레임 또는 촬영 이미지를 0.5초~1초 간격으로 서버에 전송한다. Android는 출석 완료 여부를 직접 판단하지 않고, 반드시 서버 응답의 `status` 값을 기준으로 UI 상태를 바꾼다.

```text
Camera frame / captured image
        |
        v
POST /attendance/recognize
multipart/form-data:
- image
- session_id optional
        |
        v
Server response status
        |
        +-- recognizing       -> 학생정보 + 인식 중 + 남은 시간 표시
        +-- attended          -> 출석 완료 UI 표시, 필요 시 출석 목록 갱신
        +-- already_attended  -> 이미 출석함 안내
        +-- unknown           -> 미등록 사용자 표시
        +-- no_face           -> 얼굴을 화면에 맞춰달라는 안내
        +-- multiple_faces    -> 한 명만 촬영해 달라는 안내
```

중복 요청을 줄이기 위해 요청 중에는 다음 요청을 보내지 않는다. 즉, `isRequestInFlight == true`이면 프레임 전송을 건너뛴다.

## 2. 상태별 UI 정책

| status | matched | Android 처리 |
| --- | --- | --- |
| `recognizing` | true | 이름, 학번, 학과 표시. `"인식 중"` 표시. `remaining_seconds` 또는 `elapsed_seconds / hold_seconds` 표시. 출석 완료 처리 금지. |
| `attended` | true | 이름, 학번 표시. `"출석 완료"` 성공 UI 표시. 오늘 출석 목록 갱신 가능. |
| `already_attended` | true | 이름, 학번 표시. `"이미 출석함"` 안내 표시. |
| `unknown` | false | `"미등록 사용자"` 표시. 학생정보 영역 비움. |
| `no_face` | false | `"얼굴을 화면에 맞춰주세요"` 표시. |
| `multiple_faces` | false | `"한 명만 촬영해 주세요"` 표시. |
| 기타 오류 | false | 서버의 `message` 표시. |

## 3. Data Class

권장 파일:

```text
app/src/main/java/com/example/attendance/data/AttendanceModels.kt
```

```kotlin
package com.example.attendance.data

import com.google.gson.annotations.SerializedName

data class AttendanceRecognizeResponse(
    val success: Boolean,
    val matched: Boolean,
    val status: String? = null,
    val message: String? = null,

    @SerializedName("hold_seconds")
    val holdSeconds: Double? = null,

    @SerializedName("elapsed_seconds")
    val elapsedSeconds: Double? = null,

    @SerializedName("remaining_seconds")
    val remainingSeconds: Double? = null,

    val student: StudentDto? = null,
    val recognition: RecognitionDto? = null,
    val attendance: AttendanceDto? = null
)

data class StudentDto(
    @SerializedName("student_id")
    val studentId: Int,

    @SerializedName("student_no")
    val studentNo: String,

    val name: String,
    val department: String? = null
)

data class RecognitionDto(
    val distance: Double? = null,
    val similarity: Double? = null
)

data class AttendanceDto(
    val marked: Boolean? = null,
    val message: String? = null
)
```

## 4. Retrofit API

권장 파일:

```text
app/src/main/java/com/example/attendance/network/AttendanceApi.kt
```

```kotlin
package com.example.attendance.network

import com.example.attendance.data.AttendanceRecognizeResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface AttendanceApi {
    @Multipart
    @POST("/attendance/recognize")
    suspend fun recognizeAttendance(
        @Part image: MultipartBody.Part,
        @Part("session_id") sessionId: RequestBody? = null
    ): AttendanceRecognizeResponse
}
```

Retrofit 생성 예시:

```kotlin
package com.example.attendance.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "http://10.0.2.2:8000"

    val attendanceApi: AttendanceApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AttendanceApi::class.java)
    }
}
```

실제 스마트폰에서는 `BASE_URL`을 PC 내부 IP로 바꾼다.

```kotlin
private const val BASE_URL = "http://192.168.0.xx:8000"
```

## 5. UI State

권장 파일:

```text
app/src/main/java/com/example/attendance/ui/AttendanceUiState.kt
```

```kotlin
package com.example.attendance.ui

import com.example.attendance.data.StudentDto

data class AttendanceUiState(
    val status: AttendanceStatus = AttendanceStatus.Idle,
    val message: String = "대기 중",
    val student: StudentDto? = null,
    val holdSeconds: Double = 3.0,
    val elapsedSeconds: Double = 0.0,
    val remainingSeconds: Double = 3.0,
    val distance: Double? = null,
    val isRequestInFlight: Boolean = false
)

enum class AttendanceStatus {
    Idle,
    Recognizing,
    Attended,
    AlreadyAttended,
    Unknown,
    NoFace,
    MultipleFaces,
    Error
}
```

## 6. ViewModel 처리 로직

권장 파일:

```text
app/src/main/java/com/example/attendance/ui/AttendanceViewModel.kt
```

```kotlin
package com.example.attendance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.attendance.data.AttendanceRecognizeResponse
import com.example.attendance.network.AttendanceApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class AttendanceViewModel(
    private val api: AttendanceApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    fun recognizeFrame(imageFile: File, sessionId: Int? = null) {
        if (_uiState.value.isRequestInFlight) return

        _uiState.value = _uiState.value.copy(isRequestInFlight = true)

        viewModelScope.launch {
            try {
                val imageBody = imageFile
                    .asRequestBody("image/jpeg".toMediaType())

                val imagePart = MultipartBody.Part.createFormData(
                    name = "image",
                    filename = imageFile.name,
                    body = imageBody
                )

                val sessionBody = sessionId
                    ?.toString()
                    ?.toRequestBody("text/plain".toMediaType())

                val response = api.recognizeAttendance(
                    image = imagePart,
                    sessionId = sessionBody
                )

                _uiState.value = response.toUiState().copy(isRequestInFlight = false)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = AttendanceStatus.Error,
                    message = "서버 요청 실패: ${error.message}",
                    isRequestInFlight = false
                )
            }
        }
    }

    private fun AttendanceRecognizeResponse.toUiState(): AttendanceUiState {
        return when (status) {
            "recognizing" -> AttendanceUiState(
                status = AttendanceStatus.Recognizing,
                message = message ?: "인식 중",
                student = student,
                holdSeconds = holdSeconds ?: 3.0,
                elapsedSeconds = elapsedSeconds ?: 0.0,
                remainingSeconds = remainingSeconds ?: 0.0,
                distance = recognition?.distance
            )

            "attended" -> AttendanceUiState(
                status = AttendanceStatus.Attended,
                message = message ?: "출석 완료",
                student = student,
                holdSeconds = holdSeconds ?: 3.0,
                elapsedSeconds = elapsedSeconds ?: 3.0,
                remainingSeconds = 0.0,
                distance = recognition?.distance
            )

            "already_attended" -> AttendanceUiState(
                status = AttendanceStatus.AlreadyAttended,
                message = message ?: "이미 출석함",
                student = student,
                distance = recognition?.distance
            )

            "unknown" -> AttendanceUiState(
                status = AttendanceStatus.Unknown,
                message = message ?: "미등록 사용자",
                distance = recognition?.distance
            )

            "no_face" -> AttendanceUiState(
                status = AttendanceStatus.NoFace,
                message = "얼굴을 화면에 맞춰주세요"
            )

            "multiple_faces" -> AttendanceUiState(
                status = AttendanceStatus.MultipleFaces,
                message = "한 명만 촬영해 주세요"
            )

            else -> AttendanceUiState(
                status = AttendanceStatus.Error,
                message = message ?: "처리 중 오류가 발생했습니다.",
                distance = recognition?.distance
            )
        }
    }
}
```

핵심은 `status == "attended"`가 오기 전까지 Android가 출석 완료로 판단하지 않는 것이다.

## 7. 0.5초~1초 간격 요청 루프

CameraX 프레임 분석 또는 촬영 이미지 전송부에서 다음 원칙을 사용한다.

```kotlin
private var lastRequestTimeMs: Long = 0L
private val requestIntervalMs: Long = 700L

fun onFrameImageReady(imageFile: File, sessionId: Int?) {
    val now = System.currentTimeMillis()
    if (now - lastRequestTimeMs < requestIntervalMs) return

    lastRequestTimeMs = now
    viewModel.recognizeFrame(imageFile, sessionId)
}
```

`ViewModel` 내부에서도 `isRequestInFlight`를 확인하므로, 네트워크 응답이 느릴 때 중복 요청이 쌓이지 않는다.

## 8. Compose UI 예시

권장 파일:

```text
app/src/main/java/com/example/attendance/ui/AttendanceScreen.kt
```

```kotlin
package com.example.attendance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AttendanceStatusPanel(
    state: AttendanceUiState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = state.message,
            style = MaterialTheme.typography.titleLarge
        )

        state.student?.let { student ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "이름: ${student.name}")
            Text(text = "학번: ${student.studentNo}")
            Text(text = "학과: ${student.department ?: "-"}")
        }

        when (state.status) {
            AttendanceStatus.Recognizing -> {
                Spacer(modifier = Modifier.height(12.dp))
                val progress = if (state.holdSeconds > 0.0) {
                    (state.elapsedSeconds / state.holdSeconds).coerceIn(0.0, 1.0).toFloat()
                } else {
                    0f
                }
                LinearProgressIndicator(progress = { progress })
                Text(
                    text = "인식 중... %.1f/%.1f초, 남은 시간 %.1f초".format(
                        state.elapsedSeconds,
                        state.holdSeconds,
                        state.remainingSeconds
                    )
                )
            }

            AttendanceStatus.Attended -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "출석 완료")
            }

            AttendanceStatus.AlreadyAttended -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "이미 출석한 학생입니다.")
            }

            AttendanceStatus.Unknown -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "등록되지 않은 얼굴입니다.")
            }

            AttendanceStatus.NoFace -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "얼굴을 화면 중앙에 맞춰주세요.")
            }

            AttendanceStatus.MultipleFaces -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "한 명만 촬영해 주세요.")
            }

            AttendanceStatus.Error -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = state.message)
            }

            AttendanceStatus.Idle -> Unit
        }

        state.distance?.let { distance ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "인식 거리: %.3f".format(distance))
        }
    }
}
```

## 9. XML/View 기반 UI 처리 예시

Compose를 쓰지 않는 경우에는 응답 처리 후 ViewModel 또는 Activity에서 다음처럼 분기한다.

```kotlin
fun renderState(state: AttendanceUiState) {
    statusTextView.text = state.message

    nameTextView.text = state.student?.name ?: "-"
    studentNoTextView.text = state.student?.studentNo ?: "-"
    departmentTextView.text = state.student?.department ?: "-"

    when (state.status) {
        AttendanceStatus.Recognizing -> {
            progressBar.isIndeterminate = false
            progressBar.progress = ((state.elapsedSeconds / state.holdSeconds) * 100).toInt()
            timerTextView.text = "%.1f초 남음".format(state.remainingSeconds)
        }

        AttendanceStatus.Attended -> {
            progressBar.progress = 100
            timerTextView.text = "출석 완료"
            refreshTodayAttendance()
        }

        AttendanceStatus.AlreadyAttended -> {
            timerTextView.text = "이미 출석함"
        }

        AttendanceStatus.Unknown -> {
            timerTextView.text = "미등록 사용자"
        }

        AttendanceStatus.NoFace -> {
            timerTextView.text = "얼굴을 화면에 맞춰주세요"
        }

        AttendanceStatus.MultipleFaces -> {
            timerTextView.text = "한 명만 촬영해 주세요"
        }

        AttendanceStatus.Error,
        AttendanceStatus.Idle -> {
            timerTextView.text = state.message
        }
    }
}
```

## 10. 구현 시 주의사항

- Android에서 3초를 직접 재서 출석 완료 처리하지 않는다.
- 출석 완료 판단은 서버의 `status == "attended"` 응답만 기준으로 한다.
- `recognizing`은 아직 DB 저장 전 상태다.
- `already_attended`는 이미 서버 DB에 출석 기록이 있다는 뜻이다.
- Android는 SQLite DB를 직접 수정하지 않는다.
- 같은 화면에서 연속 요청 시 `isRequestInFlight`로 요청 중복을 막는다.
- 요청 주기는 0.5초~1초 정도가 적당하다.
- 서버 재시작 시 tracker가 초기화되므로, 앱은 `recognizing` 상태가 다시 0초부터 시작될 수 있음을 허용해야 한다.

