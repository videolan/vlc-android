export type PaginationMeta = {
	page: number;
	page_size: number;
	total_records: number;
	total_pages: number;
};

export type Discourse = {
	id: string;
	title: string;
	thumbnail_url: string | null;
	is_audio_cleaned: boolean;
	language: string;
	total_tracks: number;
	total_likes: number;
	slug: string;
	created_at: string;
	updated_at: string;
};

export type DiscourseAudio = {
	id: string;
	discourse_id: string;
	discourse_name: string;
	discourse_thumbnail_url: string | null;
	language: string;
	title: string;
	audio_url: string;
	duration_seconds: number | null;
	file_size: number | null;
	mime_type: string | null;
	track_number: number | null;
	total_likes: number;
	created_at: string;
	updated_at: string;
};

export type GetDiscoursesRequest = {
	page?: number;
	search?: string;
	is_audio_cleaned?: boolean;
	language?: DiscourseLanguage;
	sort?: "most_liked";
};

export type GetDiscoursesResponse = {
	data: Discourse[];
	meta: PaginationMeta;
};

export type GetDiscourseAudiosResponse = {
	data: DiscourseAudio[];
	meta: PaginationMeta;
};

export type GetDiscourseAudiosByDiscourseRequest = {
	discourse_id: string;
};

export type GetDiscourseAudiosByDiscourseResponse = {
	data: DiscourseAudio[];
	meta: {
		total_records: number;
	};
};

export type DiscourseLanguage =
	| "hindi"
	| "english";

export type GetDiscourseAudiosRequest = {
	page?: number;
	search?: string;
	discourse_name?: string;
	language?: DiscourseLanguage;
};

export type ListeningStatsBy = "discourse" | "discourse_audio";

export type ListeningStatsTime = "7_days" | "24_hours";

export type ListeningStatsResult =
	| (Discourse & { plays: number })
	| (DiscourseAudio & { plays: number });

export type GetListeningStatsResponse<T extends ListeningStatsBy = ListeningStatsBy> = {
	data: T extends "discourse"
		? (Discourse & { plays: number })[]
		: T extends "discourse_audio"
			? (DiscourseAudio & { plays: number })[]
			: ListeningStatsResult[];
	meta: {
		by: T;
		time: ListeningStatsTime;
		limit: number;
	};
};
