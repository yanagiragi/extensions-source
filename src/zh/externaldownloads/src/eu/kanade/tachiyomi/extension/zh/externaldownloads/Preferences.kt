package eu.kanade.tachiyomi.extension.zh.externaldownloads

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.network.GET
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException

private const val DEFAULT_URL = "http://127.0.0.1"
private const val DEFAULT_LIST = "[]"

fun getPreferencesInternal(
    context: Context,
    preferences: SharedPreferences,
    isUpdated: Boolean,
) = arrayOf(
    EditTextPreference(context).apply {
        key = URL_PREF
        title = "Url"
        summary = if (isUpdated) "Restart app to take affect." else "Service Endpoint"
        text = preferences.baseUrl
    },
    ListPreference(context).apply {
        key = ARTISTS_PREF
        title = "Artists"

        val artists = JSONArray(preferences.artists)
        val count = artists.length()
        val options = mutableListOf<String>()
        for (i in 0 until count) {
            val obj = artists.getString(i)
            options.add(obj)
        }

        summary = if (isUpdated) "Restart app to take affect." else "Artist filter ($count)"
        entries = options.toTypedArray()
        entryValues = Array(count, Int::toString)
    },
)

val SharedPreferences.baseUrl get() = getString(URL_PREF, "DEFAULT_LIST")
val SharedPreferences.artists get() = getString(ARTISTS_PREF, DEFAULT_LIST)!!

fun SharedPreferences.preferenceMigration() {
    if (getString(URL_PREF, "")!! == "") {
        edit()
            .putString(URL_PREF, DEFAULT_URL)
            .apply()
    }

    try {
        JSONArray(getString(ARTISTS_PREF, DEFAULT_LIST))
    } catch (_: Throwable) {
        edit()
            .putString(ARTISTS_PREF, DEFAULT_LIST)
            .apply()
    }
}

class UpdateArtistInterceptor(private val preferences: SharedPreferences) : Interceptor {
    private val baseUrl = preferences.baseUrl
    var isUpdated = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val failedResponse = try {
            val response = chain.proceed(request)
            if (response.isSuccessful) {
                return response
            }
            response.close()
            Result.success(response)
        } catch (e: Throwable) {
            if (chain.call().isCanceled()) throw e
            Result.failure(e)
        }

        if (isUpdated || updateArtist(chain)) {
            throw IOException("Artist list has been updated. Restart the app to take affect.")
        }
        return failedResponse.getOrThrow()
    }

    @Synchronized
    private fun updateArtist(chain: Interceptor.Chain): Boolean {
        if (isUpdated) return true
        val response = try {
            chain.proceed(GET("$baseUrl/artists"))
        } catch (_: Throwable) {
            return false
        }
        if (!response.isSuccessful) {
            response.close()
            return false
        }
        val newList = response.body.string()
        if (newList != preferences.getString(ARTISTS_PREF, "")!!) {
            preferences.edit()
                .putString(ARTISTS_PREF, newList)
                .apply()
        }
        isUpdated = true
        return true
    }
}

private const val URL_PREF = "baseUrl"
private const val ARTISTS_PREF = "artists"
