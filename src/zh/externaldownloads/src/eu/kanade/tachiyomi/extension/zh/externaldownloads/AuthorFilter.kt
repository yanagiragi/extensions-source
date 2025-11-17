package eu.kanade.tachiyomi.extension.zh.externaldownloads

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second.takeUnless { it.isEmpty() }
}

private val sortPairs = listOf(
    "1" to "",
    "2：本日" to "popular-today",
    "3：本週" to "popular-week",
    "4：所有" to "popular",
)

class AuthorFilter : SelectFilter("Author", sortPairs)
