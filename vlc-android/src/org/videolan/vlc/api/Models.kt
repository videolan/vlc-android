package org.videolan.vlc.api
import com.google.gson.annotations.SerializedName

data class OpenSubtitle(
    @SerializedName("MatchedBy") val matchedBy: String,
    @SerializedName("IDSubMovieFile") val idSubMovieFile: String,
    @SerializedName("MovieHash") val movieHash: String,
    @SerializedName("MovieByteSize") val movieByteSize: String,
    @SerializedName("MovieTimeMS") val movieTimeMS: String,
    @SerializedName("IDSubtitleFile") val idSubtitleFile: String,
    @SerializedName("SubFileName") val subFileName: String,
    @SerializedName("SubActualCD") val subActualCD: String,
    @SerializedName("SubSize") val subSize: String,
    @SerializedName("SubHash") val subHash: String,
    @SerializedName("SubLastTS") val subLastTS: String,
    @SerializedName("SubTSGroup") val subTSGroup: String,
    @SerializedName("InfoReleaseGroup") val infoReleaseGroup: String,
    @SerializedName("InfoFormat") val infoFormat: String,
    @SerializedName("InfoOther") val infoOther: String,
    @SerializedName("IDSubtitle") val idSubtitle: String,
    @SerializedName("UserID") val userID: String,
    @SerializedName("SubLanguageID") val subLanguageID: String,
    @SerializedName("SubFormat") val subFormat: String,
    @SerializedName("SubSumCD") val subSumCD: String,
    @SerializedName("SubAuthorComment") val subAuthorComment: String,
    @SerializedName("SubAddDate") val subAddDate: String,
    @SerializedName("SubBad") val subBad: String,
    @SerializedName("SubRating") val subRating: String,
    @SerializedName("SubSumVotes") val subSumVotes: String,
    @SerializedName("SubDownloadsCnt") val subDownloadsCnt: String,
    @SerializedName("MovieReleaseName") val movieReleaseName: String,
    @SerializedName("MovieFPS") val movieFPS: String,
    @SerializedName("IDMovie") val idMovie: String,
    @SerializedName("IDMovieImdb") val idMovieImdb: String,
    @SerializedName("MovieName") val movieName: String,
    @SerializedName("MovieNameEng") val movieNameEng: Any,
    @SerializedName("MovieYear") val movieYear: String,
    @SerializedName("MovieImdbRating") val movieImdbRating: Any,
    @SerializedName("SubFeatured") val subFeatured: String,
    @SerializedName("UserNickName") val userNickName: String,
    @SerializedName("SubTranslator") val subTranslator: String,
    @SerializedName("ISO639") val iSO639: String,
    @SerializedName("LanguageName") val languageName: String,
    @SerializedName("SubComments") val subComments: String,
    @SerializedName("SubHearingImpaired") val subHearingImpaired: String,
    @SerializedName("UserRank") val userRank: String,
    @SerializedName("SeriesSeason") val seriesSeason: String,
    @SerializedName("SeriesEpisode") val seriesEpisode: String,
    @SerializedName("MovieKind") val movieKind: String,
    @SerializedName("SubHD") val subHD: String,
    @SerializedName("SeriesIMDBParent") val seriesIMDBParent: String,
    @SerializedName("SubEncoding") val subEncoding: String,
    @SerializedName("SubAutoTranslation") val subAutoTranslation: String,
    @SerializedName("SubForeignPartsOnly") val subForeignPartsOnly: String,
    @SerializedName("SubFromTrusted") val subFromTrusted: String,
    @SerializedName("QueryParameters") val queryParameters: QueryParameters,
    @SerializedName("SubTSGroupHash") val subTSGroupHash: String,
    @SerializedName("SubDownloadLink") val subDownloadLink: String,
    @SerializedName("ZipDownloadLink") val zipDownloadLink: String,
    @SerializedName("SubtitlesLink") val subtitlesLink: String,
    @SerializedName("QueryNumber") val queryNumber: String,
    @SerializedName("Score") val score: Double,
    var state: Int = 0
)

data class QueryParameters(
    @SerializedName("query") val query: String,
    @SerializedName("episode") val episode: String,
    @SerializedName("season") val season: String
)

