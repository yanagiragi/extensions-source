package eu.kanade.tachiyomi.extension.zh.externaldownloads

import android.util.Log
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SManga.Companion.COMPLETED
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.math.min

class ExternalDownloads : ParsedHttpSource(), ConfigurableSource {
    // region Info
    override val name: String = "External Downloads"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = false
    // endregion

    // region Preferences
    private val preferences = getPreferences { preferenceMigration() }

    override val baseUrl: String = preferences.baseUrl!!

    private val updateArtistInterceptor = UpdateArtistInterceptor(preferences)

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(updateArtistInterceptor)
        .build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        eu.kanade.tachiyomi.extension.zh.externaldownloads.getPreferencesInternal(
            screen.context,
            preferences,
            updateArtistInterceptor.isUpdated,
        ).forEach(screen::addPreference)
    }
    // endregion

    // region Popular (WIP)
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/json")

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonArray = JSONArray(response.body.string())
        val mangas = mutableListOf<SManga>()
        val len = min(jsonArray.length(), 10) // TODO: Add pagination
        for (i in 0 until len) {
            val obj = jsonArray.getJSONObject(i)
            val thumb = obj.getString("thumb")
            val thumbUri = if (thumb.startsWith("http")) thumb else "$baseUrl/assets/$thumb"
            Log.d("EXTERNAL_DOWNLOADS", "popularMangaParse: $thumbUri")
            mangas.add(
                SManga.create().apply {
                    setUrlWithoutDomain("/details/${obj.getString("title")}")
                    title = obj.getString("title")
                    thumbnail_url = thumbUri
                    artist = obj.getString("artist")
                    status = COMPLETED
                },
            )
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    override fun popularMangaSelector(): String = throw UnsupportedOperationException("Not used.")
    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used.")
    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException("Not used.")
    // endregion

    // region Search (WIP)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = "$baseUrl/search".toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("query", query)
            .addQueryParameter("page", "$page")
        Log.d("EXTERNAL_DOWNLOADS", "searchMangaRequest: $searchUrl")
        return GET(searchUrl.build())
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("a").attr("href"))
            title = element.select("div.comic-rows-videos-title").text()
            thumbnail_url = element.select("img").attr("data-srcset")
        }
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used.")
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used.")
    // endregion

    // region Details & Chapters
    override fun mangaDetailsParse(document: Document): SManga {
        val obj = JSONObject(document.body().text())
        return SManga.create().apply {
            title = obj.getString("title")
            val thumb = obj.getString("thumb")
            thumbnail_url = if (thumb.startsWith("http")) thumb else "$baseUrl/assets/$thumb"
            artist = obj.optString("artist")
            status = COMPLETED
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonObject = JSONObject(response.body.string())
        Log.d("EXTERNAL_DOWNLOADS", "chapterListParse: /assets/${jsonObject.getString("localPath")}")
        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain("/details/${jsonObject.getString("title")}")
                name = "Single"
            },
        )
    }

    override fun pageListParse(document: Document): List<Page> {
        Log.d("EXTERNAL_DOWNLOADS", "pageListParse: ${document.body().text()}")
        val obj = JSONObject(document.body().text())
        val pageSize = obj.getInt("filecount")
        return List(pageSize) { index ->
            Log.d("EXTERNAL_DOWNLOADS", "pageListParse: $baseUrl/images/${obj.getString("title")}/${index + 1}")
            Page(index, imageUrl = "$baseUrl/images/${obj.getString("title")}/${index + 1}")
        }
    }
    // endregion

    // region Filters
    override fun getFilterList(): FilterList {
        val artists = JSONArray(preferences.artists)
        return FilterList(AuthorFilter(List(artists.length()) { i -> artists.getString(i) }))
    }
    // endregion

    // region Not Used
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()
    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    // endregion
}
