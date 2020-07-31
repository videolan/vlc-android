package org.videolan.resources.opensubtitles

import retrofit2.http.GET
import retrofit2.http.Path

//Passing 0 for numbers and "" for strings ignores that parameters
interface IOpenSubtitleService {
    @GET("episode-{episode}/imdbid-{imdbId}/moviebytesize-{movieByteSize}/moviehash-{movieHash}/query-{name}/season-{season}/sublanguageid-{subLanguageId}/tag_{tag}")
    suspend fun query( @Path("movieByteSize") movieByteSize: String = "",
               @Path("movieHash") movieHash: String = "",
               @Path("name") name: String = "",
               @Path("imdbId") imdbId: String = "" ,
               @Path("tag") tag: String = "",
               @Path("episode") episode: Int = 0,
               @Path("season") season: Int = 0,
               @Path("subLanguageId") languageId: String = ""): List<OpenSubtitle>

}


