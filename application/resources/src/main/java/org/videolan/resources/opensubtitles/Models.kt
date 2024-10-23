package org.videolan.resources.opensubtitles
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


data class OpenSubtitle(
        @field:Json(name = "MatchedBy") val matchedBy: String,
        @field:Json(name = "IDSubMovieFile") val idSubMovieFile: String,
        @field:Json(name = "MovieHash") val movieHash: String,
        @field:Json(name = "MovieByteSize") val movieByteSize: String,
        @field:Json(name = "MovieTimeMS") val movieTimeMS: String,
        @field:Json(name = "IDSubtitleFile") val idSubtitleFile: String,
        @field:Json(name = "SubFileName") val subFileName: String,
        @field:Json(name = "SubActualCD") val subActualCD: String,
        @field:Json(name = "SubSize") val subSize: String,
        @field:Json(name = "SubHash") val subHash: String,
        @field:Json(name = "SubLastTS") val subLastTS: String,
        @field:Json(name = "SubTSGroup") val subTSGroup: String,
        @field:Json(name = "InfoReleaseGroup") val infoReleaseGroup: String,
        @field:Json(name = "InfoFormat") val infoFormat: String,
        @field:Json(name = "InfoOther") val infoOther: String,
        @field:Json(name = "IDSubtitle") val idSubtitle: String,
        @field:Json(name = "UserID") val userID: String,
        @field:Json(name = "SubLanguageID") val subLanguageID: String,
        @field:Json(name = "SubFormat") val subFormat: String,
        @field:Json(name = "SubSumCD") val subSumCD: String,
        @field:Json(name = "SubAuthorComment") val subAuthorComment: String,
        @field:Json(name = "SubAddDate") val subAddDate: String,
        @field:Json(name = "SubBad") val subBad: String,
        @field:Json(name = "SubRating") val subRating: String,
        @field:Json(name = "SubSumVotes") val subSumVotes: String,
        @field:Json(name = "SubDownloadsCnt") val subDownloadsCnt: String,
        @field:Json(name = "MovieReleaseName") val movieReleaseName: String,
        @field:Json(name = "MovieFPS") val movieFPS: String,
        @field:Json(name = "IDMovie") val idMovie: String,
        @field:Json(name = "IDMovieImdb") val idMovieImdb: String,
        @field:Json(name = "MovieName") val movieName: String,
        @field:Json(name = "MovieNameEng") val movieNameEng: Any,
        @field:Json(name = "MovieYear") val movieYear: String,
        @field:Json(name = "MovieImdbRating") val movieImdbRating: Any,
        @field:Json(name = "SubFeatured") val subFeatured: String,
        @field:Json(name = "UserNickName") val userNickName: String,
        @field:Json(name = "SubTranslator") val subTranslator: String,
        @field:Json(name = "ISO639") val iSO639: String,
        @field:Json(name = "LanguageName") val languageName: String,
        @field:Json(name = "SubComments") val subComments: String,
        @field:Json(name = "SubHearingImpaired") val subHearingImpaired: String,
        @field:Json(name = "UserRank") val userRank: String,
        @field:Json(name = "SeriesSeason") val seriesSeason: String,
        @field:Json(name = "SeriesEpisode") val seriesEpisode: String,
        @field:Json(name = "MovieKind") val movieKind: String,
        @field:Json(name = "SubHD") val subHD: String,
        @field:Json(name = "SeriesIMDBParent") val seriesIMDBParent: String,
        @field:Json(name = "SubEncoding") val subEncoding: String,
        @field:Json(name = "SubAutoTranslation") val subAutoTranslation: String,
        @field:Json(name = "SubForeignPartsOnly") val subForeignPartsOnly: String,
        @field:Json(name = "SubFromTrusted") val subFromTrusted: String,
        @field:Json(name = "QueryParameters") val queryParameters: QueryParameters,
        @field:Json(name = "SubTSGroupHash") val subTSGroupHash: String,
        @field:Json(name = "SubDownloadLink") val subDownloadLink: String,
        @field:Json(name = "ZipDownloadLink") val zipDownloadLink: String,
        @field:Json(name = "SubtitlesLink") val subtitlesLink: String,
        @field:Json(name = "QueryNumber") val queryNumber: String,
        @field:Json(name = "Score") val score: Double
)

data class QueryParameters(
        @field:Json(name = "query") val query: String,
        @field:Json(name = "episode") val episode: String,
        @field:Json(name = "season") val season: String
)

data class OpenSubV1(
        @field:Json(name = "data")
        val `data`: List<Data>,
        @field:Json(name = "page")
        val page: Int,
        @field:Json(name = "per_page")
        val perPage: Int,
        @field:Json(name = "total_count")
        val totalCount: Int,
        @field:Json(name = "total_pages")
        val totalPages: Int
)

data class Data(
        @field:Json(name = "attributes")
        val attributes: Attributes,
        @field:Json(name = "id")
        val id: String,
        @field:Json(name = "type")
        val type: String
)

data class Attributes(
        @field:Json(name = "ai_translated")
        val aiTranslated: Boolean,
        @field:Json(name = "comments")
        val comments: String,
        @field:Json(name = "download_count")
        val downloadCount: Int,
        @field:Json(name = "feature_details")
        val featureDetails: FeatureDetails,
        @field:Json(name = "files")
        val files: List<File>,
        @field:Json(name = "foreign_parts_only")
        val foreignPartsOnly: Boolean,
        @field:Json(name = "fps")
        val fps: Double,
        @field:Json(name = "from_trusted")
        val fromTrusted: Boolean,
        @field:Json(name = "hd")
        val hd: Boolean,
        @field:Json(name = "hearing_impaired")
        val hearingImpaired: Boolean,
        @field:Json(name = "language")
        val language: String,
        @field:Json(name = "legacy_subtitle_id")
        val legacySubtitleId: Int,
        @field:Json(name = "legacy_uploader_id")
        val legacyUploaderId: Int,
        @field:Json(name = "machine_translated")
        val machineTranslated: Boolean,
        @field:Json(name = "moviehash_match")
        val moviehashMatch: Boolean,
        @field:Json(name = "nb_cd")
        val nbCd: Int,
        @field:Json(name = "new_download_count")
        val newDownloadCount: Int,
        @field:Json(name = "ratings")
        val ratings: Int,
        @field:Json(name = "related_links")
        val relatedLinks: List<RelatedLink>,
        @field:Json(name = "release")
        val release: String,
        @field:Json(name = "slug")
        val slug: String,
        @field:Json(name = "subtitle_id")
        val subtitleId: String,
        @field:Json(name = "upload_date")
        val uploadDate: String,
        @field:Json(name = "uploader")
        val uploader: Uploader,
        @field:Json(name = "url")
        val url: String,
        @field:Json(name = "votes")
        val votes: Int
)

data class FeatureDetails(
        @field:Json(name = "episode_number")
        val episodeNumber: Int,
        @field:Json(name = "feature_id")
        val featureId: Int,
        @field:Json(name = "feature_type")
        val featureType: String,
        @field:Json(name = "imdb_id")
        val imdbId: Int,
        @field:Json(name = "movie_name")
        val movieName: String,
        @field:Json(name = "parent_feature_id")
        val parentFeatureId: Int,
        @field:Json(name = "parent_imdb_id")
        val parentImdbId: Int,
        @field:Json(name = "parent_title")
        val parentTitle: String,
        @field:Json(name = "parent_tmdb_id")
        val parentTmdbId: Int,
        @field:Json(name = "season_number")
        val seasonNumber: Int,
        @field:Json(name = "title")
        val title: String,
        @field:Json(name = "tmdb_id")
        val tmdbId: Int? = null,
        @field:Json(name = "year")
        val year: Int
)

data class File(
        @field:Json(name = "cd_number")
        val cdNumber: Int,
        @field:Json(name = "file_id")
        val fileId: Int,
        @field:Json(name = "file_name")
        val fileName: String
)

data class RelatedLink(
        @field:Json(name = "img_url")
        val imgUrl: String,
        @field:Json(name = "label")
        val label: String,
        @field:Json(name = "url")
        val url: String
)

data class Uploader(
        @field:Json(name = "name")
        val name: String,
        @field:Json(name = "rank")
        val rank: String,
        @field:Json(name = "uploader_id")
        val uploaderId: Int? = null
)

