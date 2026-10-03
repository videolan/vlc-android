import * as BunnySDK from "npm:@bunny.net/edgescript-sdk@0.12.1";
import { createClient } from "npm:@libsql/client@0.17.4/web";
import process from "node:process";

const db = createClient({
	url: process.env.BUNNY_DATABASE_URL!,
	authToken: process.env.BUNNY_DATABASE_AUTH_TOKEN,
});

const randomNumber = () => Math.floor(Math.random() * 1000) + 1;
const DISCOURSE_URL =
	`https://osho.b-cdn.net/seed_data/discourses.json?v=${randomNumber()}`;

const DISCOURSE_AUDIO_URL =
	`https://osho.b-cdn.net/seed_data/discourse_audio.json?v=${randomNumber()}`;

const BATCH_SIZE = 100;

type Discourse = {
	id: string;
	title: string;
	thumbnail_url: string | null;
	is_audio_cleaned: boolean;
	total_tracks: number;
	total_likes: number;
	language: string;
	slug: string;
};

type DiscourseAudio = {
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
};

const jsonResponse = (
	data: unknown,
	status = 200,
) =>
	new Response(
		JSON.stringify(data, null, 2),
		{
			status,
			headers: {
				"content-type":
					"application/json",
			},
		},
	);

async function fetchJson<T>(
	url: string,
): Promise<T> {
	const response = await fetch(
		url,
		{
			headers: {
				accept:
					"application/json",
			},
		},
	);

	if (!response.ok) {
		throw new Error(
			`Failed to fetch ${url}: ${response.status} ${response.statusText}`,
		);
	}

	return (await response.json()) as T;
}

async function createTables() {
	await db.batch(
		[
			{
				sql: `
          CREATE TABLE IF NOT EXISTS discourse (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            thumbnail_url TEXT,
            is_audio_cleaned INTEGER NOT NULL DEFAULT 0,
            language TEXT NOT NULL,
			total_tracks INTEGER NOT NULL DEFAULT 0,
			total_likes INTEGER NOT NULL DEFAULT 0,
            slug TEXT NOT NULL UNIQUE,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
          )
        `,
				args: [],
			},
			{
				sql: `
          CREATE TABLE IF NOT EXISTS discourse_audio (
            id TEXT PRIMARY KEY,
            discourse_id TEXT NOT NULL,
            discourse_name TEXT NOT NULL,
            discourse_thumbnail_url TEXT,
            language TEXT NOT NULL,
            title TEXT NOT NULL,
            audio_url TEXT NOT NULL,
            duration_seconds REAL,
            file_size INTEGER,
            mime_type TEXT,
            track_number INTEGER,
            total_likes INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (discourse_id) REFERENCES discourse(id)
          )
        `,
				args: [],
			},
		],
		"write",
	);
}

function validateDiscourses(
	rows: Discourse[],
) {
	if (!Array.isArray(rows)) {
		throw new Error(
			"discourses.json must be an array.",
		);
	}

	if (rows.length === 0) {
		throw new Error(
			"discourses.json is empty.",
		);
	}

	for (const row of rows) {
		if (
			!row.id ||
			!row.title ||
			!row.slug
		) {
			throw new Error(
				`Invalid discourse: ${JSON.stringify(row)}`,
			);
		}
	}
}

function validateDiscourseAudios(
	rows: DiscourseAudio[],
) {
	if (!Array.isArray(rows)) {
		throw new Error(
			"discourse_audio.json must be an array.",
		);
	}

	if (rows.length === 0) {
		throw new Error(
			"discourse_audio.json is empty.",
		);
	}

	for (const row of rows) {
		if (
			!row.id ||
			!row.discourse_id ||
			!row.title ||
			!row.audio_url
		) {
			throw new Error(
				`Invalid discourse_audio: ${JSON.stringify(row)}`,
			);
		}
	}
}

function validateRelations(
	discourses: Discourse[],
	audios: DiscourseAudio[],
) {
	const ids = new Set(
		discourses.map(
			(item) => item.id,
		),
	);

	const invalid =
		audios.find(
			(audio) =>
				!ids.has(
					audio.discourse_id,
				),
		);

	if (invalid) {
		throw new Error(
			`Invalid discourse_id reference: ${invalid.id} -> ${invalid.discourse_id}`,
		);
	}
}

async function clearExistingData() {
	await db.batch(
		[
			{
				sql: `
          DELETE FROM discourse_audio
        `,
				args: [],
			},
			{
				sql: `
          DELETE FROM discourse
        `,
				args: [],
			},
		],
		"write",
	);
}

async function insertDiscourses(
	rows: Discourse[],
) {
	let inserted = 0;

	for (
		let i = 0;
		i < rows.length;
		i += BATCH_SIZE
	) {
		const batch =
			rows
				.slice(
					i,
					i + BATCH_SIZE,
				)
				.map(
					(row) => ({
						sql: `
                            INSERT INTO discourse (
                                id,
                                title,
                                thumbnail_url,
                                is_audio_cleaned,
                                language,
                                total_tracks,
                                total_likes,
                                slug,
                                created_at,
                                updated_at
                            )
                            VALUES (
                                       ?, ?, ?, ?, ?, ?, ?, ?,
                                       CURRENT_TIMESTAMP,
                                       CURRENT_TIMESTAMP
                                   )
						`,
						args: [
							row.id,
							row.title,
							row.thumbnail_url ?? null,
							row.is_audio_cleaned ? 1 : 0,
							row.language,
							row.total_tracks ?? 0,
							row.total_likes ?? 0,
							row.slug,
						],
					}),
				);

		await db.batch(
			batch,
			"write",
		);

		inserted +=
			batch.length;

		console.log(
			`Inserted ${inserted}/${rows.length} discourses`,
		);
	}

	return inserted;
}

async function insertDiscourseAudios(
	rows: DiscourseAudio[],
) {
	let inserted = 0;

	for (
		let i = 0;
		i < rows.length;
		i += BATCH_SIZE
	) {
		const batch =
			rows
				.slice(
					i,
					i + BATCH_SIZE,
				)
				.map(
					(row) => ({
						sql: `
              INSERT INTO discourse_audio (
                id,
                discourse_id,
                discourse_name,
                discourse_thumbnail_url,
                language,
                title,
                audio_url,
                duration_seconds,
                file_size,
                mime_type,
                track_number,
                total_likes,
                created_at,
                updated_at
              )
              VALUES (
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
              )
            `,
						args: [
							row.id,
							row.discourse_id,
							row.discourse_name,
							row.discourse_thumbnail_url ??
							null,
							row.language,
							row.title,
							row.audio_url,
							row.duration_seconds ??
							null,
							row.file_size ??
							null,
							row.mime_type ??
							null,
							row.track_number ??
							null,
							row.total_likes ?? 0,
						],
					}),
				);

		await db.batch(
			batch,
			"write",
		);

		inserted +=
			batch.length;

		console.log(
			`Inserted ${inserted}/${rows.length} discourse_audio rows`,
		);
	}

	return inserted;
}

async function getCounts() {
	const discourseCount =
		await db.execute(`
      SELECT COUNT(*) AS total
      FROM discourse
    `);

	const audioCount =
		await db.execute(`
      SELECT COUNT(*) AS total
      FROM discourse_audio
    `);

	return {
		discourse: Number(
			discourseCount.rows[0]
				?.total ?? 0,
		),

		discourse_audio:
			Number(
				audioCount.rows[0]
					?.total ?? 0,
			),
	};
}

async function seedDatabase() {
	console.log(
		"Fetching seed files...",
	);

	const [
		discourses,
		discourseAudios,
	] = await Promise.all([
		fetchJson<Discourse[]>(
			DISCOURSE_URL,
		),

		fetchJson<
			DiscourseAudio[]
		>(
			DISCOURSE_AUDIO_URL,
		),
	]);

	console.log(
		`Fetched ${discourses.length} discourses`,
	);

	console.log(
		`Fetched ${discourseAudios.length} audio rows`,
	);

	/**
	 * Validate BEFORE deleting.
	 */
	validateDiscourses(
		discourses,
	);

	validateDiscourseAudios(
		discourseAudios,
	);

	validateRelations(
		discourses,
		discourseAudios,
	);

	console.log(
		"Seed data validated.",
	);

	await createTables();

	/**
	 * Clear existing rows.
	 */
	console.log(
		"Deleting existing data...",
	);

	await clearExistingData();

	console.log(
		"Existing data deleted.",
	);

	/**
	 * Insert parent rows first.
	 */
	const insertedDiscourses =
		await insertDiscourses(
			discourses,
		);

	/**
	 * Then child rows.
	 */
	const insertedAudios =
		await insertDiscourseAudios(
			discourseAudios,
		);

	const counts =
		await getCounts();

	return {
		success: true,

		source: {
			discourse:
			DISCOURSE_URL,

			discourse_audio:
			DISCOURSE_AUDIO_URL,
		},

		inserted: {
			discourse:
			insertedDiscourses,

			discourse_audio:
			insertedAudios,
		},

		database: counts,
	};
}

BunnySDK.net.http.serve(
	async (
		request: Request,
	): Promise<Response> => {
		const url = new URL(
			request.url,
		);

		if (
			url.pathname !==
			"/seed"
		) {
			return jsonResponse(
				{
					error:
						"Not found",
					usage:
						"POST /seed",
				},
				404,
			);
		}

		if (
			request.method !==
			"POST"
		) {
			return jsonResponse(
				{
					error:
						"Method not allowed",
					usage:
						"POST /seed",
				},
				405,
			);
		}

		try {
			console.log(
				"Starting seed...",
			);

			const result =
				await seedDatabase();

			console.log(
				"Seed complete.",
			);

			return jsonResponse(
				result,
			);
		} catch (error) {
			console.error(
				"Seed failed:",
				error,
			);

			return jsonResponse(
				{
					success: false,

					error:
						error instanceof Error
							? error.message
							: String(error),
				},
				500,
			);
		}
	},
);
