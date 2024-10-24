package org.videolan.resources.opensubtitles

import org.videolan.resources.opensubtitles.OpenSubV1
import retrofit2.http.GET
import retrofit2.http.Query

interface IOpenSubtitleService {
    @GET("subtitles")
    suspend fun query(
        @Query("episode_number") episode: Int? = null,
        @Query("hearing_impaired") hearingImpaired: String,
        @Query("imdb_id") imdbId: String? = null,
        @Query("languages") languageId: String = "",
        @Query("moviehash") movieHash: String? = null,
        @Query("query") name: String? = null,
        @Query("season_number") season: Int? = null,
    ): OpenSubV1

}


