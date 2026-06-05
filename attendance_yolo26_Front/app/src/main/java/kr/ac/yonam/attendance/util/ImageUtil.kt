package kr.ac.yonam.attendance.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.camera.core.ImageProxy
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

object ImageUtil {
    fun createImagePart(
        context: Context,
        imageUri: Uri,
        partName: String = "image"
    ): MultipartBody.Part {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(imageUri) ?: "image/jpeg"
        val fileName = getDisplayName(context, imageUri) ?: "face_image.jpg"
        val bytes = resolver.openInputStream(imageUri)?.use { input ->
            input.readBytes()
        } ?: throw IllegalArgumentException("이미지 파일을 읽을 수 없습니다.")

        // 얼굴 검출과 인식은 서버가 담당하므로 Android는 이미지 바이트만 전송한다.
        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, fileName, requestBody)
    }

    fun createJpegImagePart(
        jpegBytes: ByteArray,
        partName: String = "image",
        fileName: String = "attendance_frame.jpg"
    ): MultipartBody.Part {
        val requestBody = jpegBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, fileName, requestBody)
    }

    fun imageProxyToJpegBytes(imageProxy: ImageProxy, quality: Int = 80): ByteArray {
        val bitmap = rgbaImageProxyToBitmap(imageProxy)
        val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
        val output = ByteArrayOutputStream()
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)

        if (rotatedBitmap != bitmap) {
            rotatedBitmap.recycle()
        }
        bitmap.recycle()

        return output.toByteArray()
    }

    private fun getDisplayName(context: Context, imageUri: Uri): String? {
        return context.contentResolver.query(imageUri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }

    private fun rgbaImageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val plane = imageProxy.planes.firstOrNull()
            ?: throw IllegalArgumentException("카메라 프레임 데이터가 없습니다.")
        val buffer = plane.buffer
        buffer.rewind()

        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val rowPadding = rowStride - pixelStride * width
        val bitmapWidth = width + rowPadding / pixelStride

        val paddedBitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(buffer)
        }

        return if (bitmapWidth == width) {
            paddedBitmap
        } else {
            Bitmap.createBitmap(paddedBitmap, 0, 0, width, height).also {
                paddedBitmap.recycle()
            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap

        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
