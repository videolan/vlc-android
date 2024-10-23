package org.videolan.resources.opensubtitles

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

//Passing 0 for numbers and "" for strings ignores that parameters
interface IOpenSubtitleService {
//    @GET("episode-{episode}/imdbid-{imdbId}/moviebytesize-{movieByteSize}/moviehash-{movieHash}/query-{name}/season-{season}/sublanguageid-{subLanguageId}/tag_{tag}")
//    suspend fun query( @Path("movieByteSize") movieByteSize: String = "",
//               @Path("movieHash") movieHash: String = "",
//               @Path("name") name: String = "",
//               @Path("imdbId") imdbId: String = "" ,
//               @Path("tag") tag: String = "",
//               @Path("episode") episode: Int = 0,
//               @Path("season") season: Int = 0,
//               @Path("subLanguageId") languageId: String = ""): List<OpenSubtitle>


    @GET("subtitles")
    suspend fun query( @Query("languages") languageId: String = "",
                       @Query("movieHash") movieHash: String? = null,
                       @Query("query") name: String? = null,
                       @Query("imdb_id") imdbId: String? = null ,
                       @Query("episode_number") episode: Int? = null,
                       @Query("season_number") season: Int? = null,
                       ): OpenSubV1

}


