package org.videolan.vlc.repository

import org.videolan.vlc.api.IOpenSubtitleService
import org.videolan.vlc.api.OpenSubtitle
import org.videolan.vlc.api.OpenSubtitleClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
        return retrofitResponseCall { openSubtitleService.query(
                imdbId = String.format("%07d", imdbId),
                tag = actualTag,
                episode = actualEpisode,
                season = actualSeason,
                languageId = actualLanguageId) }
    }

    suspend fun queryWithHash(movieByteSize: Long, movieHash: String, languageId: String?): List<OpenSubtitle> {
        val actualLanguageId = languageId ?: ""
        return  retrofitResponseCall { openSubtitleService.query(
                movieByteSize = movieByteSize.toString(),
                movieHash = movieHash,
                languageId = actualLanguageId) }
    }

    suspend fun queryWithName(name: String, episode: Int?, season: Int?, languageId: String?): List<OpenSubtitle> {
        val actualEpisode = episode ?: 0
        val actualSeason = season ?: 0
        val actualLanguageId = languageId ?: ""
        return retrofitResponseCall { openSubtitleService.query(
                name = name,
                episode = actualEpisode,
                season = actualSeason,
                languageId = actualLanguageId) }
    }

    suspend fun queryWithImdbid(imdbId: Int, tag: String?, episode: Int? , season: Int?, languageIds: List<String>? ): List<OpenSubtitle> {
        val actualEpisode = episode ?: 0
        val actualSeason = season ?: 0
        val actualLanguageIds = languageIds?.toSet()?.run { if (contains("") || isEmpty()) setOf("") else this } ?: setOf("")
        val actualTag = tag ?: ""
        return actualLanguageIds.flatMap {
            retrofitResponseCall { openSubtitleService.query(
                    imdbId = String.format("%07d", imdbId),
                    tag = actualTag,
                    episode = actualEpisode,
                    season = actualSeason,
                    languageId = it) }
        }
    }

    suspend fun queryWithHash(movieByteSize: Long, movieHash: String, languageIds: List<String>?): List<OpenSubtitle> {
        val actualLanguageIds = languageIds?.toSet()?.run { if (contains("") || isEmpty()) setOf("") else this } ?: setOf("")
        return actualLanguageIds.flatMap {
            retrofitResponseCall {
                openSubtitleService.query(
                        movieByteSize = movieByteSize.toString(),
                        movieHash = movieHash,
                        languageId = it)
            }
        }
    }

    suspend fun queryWithName(name: String, episode: Int?, season: Int?, languageIds: List<String>?): List<OpenSubtitle> {
        val actualEpisode = episode ?: 0
        val actualSeason = season ?: 0
        val actualLanguageIds = languageIds?.toSet()?.run { if (contains("") || isEmpty()) setOf("") else this } ?: setOf("")
        return actualLanguageIds.flatMap {
            retrofitResponseCall {
                openSubtitleService.query(
                        name = name,
                        episode = actualEpisode,
                        season = actualSeason,
                        languageId = it)
            }
        }
    }

    companion object { fun getInstance() = OpenSubtitleRepository(OpenSubtitleClient.instance)}

    private suspend inline fun <reified T> retrofitResponseCall(crossinline call: () -> Call<T>) : T {
        with(retrofitSuspendCall(call)) {
            if (isSuccessful) return body()!!
            else throw Exception(message())
        }
    }

    private suspend inline fun <reified T> retrofitSuspendCall(crossinline call: () -> Call<T>) : Response<T> = suspendCoroutine { continuation ->
        call.invoke().enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>?, response: Response<T>) = continuation.resume(response)
            override fun onFailure(call: Call<T>, t: Throwable) = continuation.resumeWithException(t)
        })
    }
}
