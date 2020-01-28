package org.videolan.resources.opensubtitles

class OpenSubtitleRepository(private val openSubtitleService: IOpenSubtitleService) {

    /*
    Multiple query functions are created based on below rules:
        1) Tags are valid only with imdbid
        2) We should use moviehash and moviebytesize together
        3) precedence: (movieBytesize and moviehash) > imdbid > name
    */

    suspend fun queryWithImdbid(imdbId: Int, tag: String?, episode: Int? , season: Int?, languageId: String? ): List<OpenSubtitle> {
        val actualEpisode = episode ?: 0
        val actualSeason = season ?: 0
        val actualLanguageId = languageId ?: ""
        val actualTag = tag ?: ""
        return openSubtitleService.query(
                imdbId = String.format("%07d", imdbId),
                tag = actualTag,
                episode = actualEpisode,
                season = actualSeason,
                languageId = actualLanguageId)
    }

    suspend fun queryWithHash(movieByteSize: Long, movieHash: String, languageId: String?): List<OpenSubtitle> {
        val actualLanguageId = languageId ?: ""
        return openSubtitleService.query(
                movieByteSize = movieByteSize.toString(),
                movieHash = movieHash,
                languageId = actualLanguageId)
    }

    suspend fun queryWithName(name: String, episode: Int?, season: Int?, languageId: String?): List<OpenSubtitle> {
        val actualEpisode = episode ?: 0
        val actualSeason = season ?: 0
        val actualLanguageId = languageId ?: ""
        return openSubtitleService.query(
                name = name,
                episode = actualEpisode,
                season = actualSeason,
                languageId = actualLanguageId)
    }

    suspend fun queryWithImdbid(imdbId: Int, tag: String?, episode: Int? , season: Int?, languageIds: List<String>? ): List<OpenSubtitle> {
        val actualEpisode = episode ?: 0
        val actualSeason = season ?: 0
        val actualLanguageIds = languageIds?.toSet()?.run { if (contains("") || isEmpty()) setOf("") else this } ?: setOf("")
        val actualTag = tag ?: ""
        return actualLanguageIds.flatMap {
            openSubtitleService.query(
                    imdbId = String.format("%07d", imdbId),
                    tag = actualTag,
                    episode = actualEpisode,
                    season = actualSeason,
                    languageId = it) }
    }

    suspend fun queryWithHash(movieByteSize: Long, movieHash: String?, languageIds: List<String>?): List<OpenSubtitle> {
        val actualLanguageIds = languageIds?.toSet()?.run { if (contains("") || isEmpty()) setOf("") else this } ?: setOf("")
        return actualLanguageIds.flatMap {
            openSubtitleService.query(
                    movieByteSize = movieByteSize.toString(),
                    movieHash = movieHash ?: "",
                    languageId = it)
        }
    }

    suspend fun queryWithName(name: String, episode: Int?, season: Int?, languageIds: List<String>?): List<OpenSubtitle> {
        val actualEpisode = episode ?: 0
        val actualSeason = season ?: 0
        val actualLanguageIds = languageIds?.toSet()?.run { if (contains("") || isEmpty()) setOf("") else this } ?: setOf("")
        return actualLanguageIds.flatMap {
            openSubtitleService.query(
                    name = name,
                    episode = actualEpisode,
                    season = actualSeason,
                    languageId = it)
        }
    }

    companion object {
        // To ensure the instance can be overridden in tests.
        var instance = lazy { OpenSubtitleRepository(OpenSubtitleClient.instance) }
        fun getInstance() = instance.value
    }
}
