package kr.ac.yonam.attendance.network

import android.content.Context
import kr.ac.yonam.attendance.util.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    const val DEFAULT_BASE_URL = ServerConfig.DEFAULT_BASE_URL

    fun create(context: Context): AttendanceApi {
        // 저장된 서버 주소가 있으면 그 값을 사용하고, 없으면 에뮬레이터 기본 주소를 사용한다.
        return create(ServerConfig.getBaseUrl(context))
    }

    fun create(baseUrl: String = DEFAULT_BASE_URL): AttendanceApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // 개발 중 서버 API 요청과 응답의 기본 정보를 Logcat에서 확인한다.
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(ServerConfig.normalizeBaseUrl(baseUrl))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AttendanceApi::class.java)
    }
}
