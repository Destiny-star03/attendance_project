package kr.ac.yonam.attendance.util

import android.content.Context

object ServerConfig {
    // Android 에뮬레이터에서 개발 PC의 localhost:8000 서버로 접속하는 기본 주소다.
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

    // 실제 태블릿에서는 같은 네트워크의 PC IP로 변경해서 사용한다. 예: http://192.168.0.10:8000/
    const val TABLET_BASE_URL_SAMPLE = "http://PC_IP:8000/"

    private const val PREF_NAME = "server_config"
    private const val KEY_BASE_URL = "base_url"

    fun getBaseUrl(context: Context): String {
        val savedUrl = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, null)
        return normalizeBaseUrl(savedUrl)
    }

    fun saveBaseUrl(context: Context, baseUrl: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, normalizeBaseUrl(baseUrl))
            .apply()
    }

    fun normalizeBaseUrl(baseUrl: String?): String {
        val trimmed = baseUrl?.trim().orEmpty().ifBlank { DEFAULT_BASE_URL }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    fun displayBaseUrl(baseUrl: String?): String {
        return normalizeBaseUrl(baseUrl).removeSuffix("/")
    }
}
