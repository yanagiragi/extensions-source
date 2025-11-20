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

class ArtistFilter(options: List<String>) : SelectFilter(
    "Author",
    listOf("None" to "None") + options.map { it to it },
)
