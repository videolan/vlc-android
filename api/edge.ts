import * as BunnySDK from "npm:@bunny.net/edgescript-sdk@0.12.1";
import { createClient } from "npm:@libsql/client@0.17.4/web";
import process from "node:process";

const db = createClient({
	url: process.env.BUNNY_DATABASE_URL!,
	authToken: process.env.BUNNY_DATABASE_AUTH_TOKEN,
});

const PAGE_SIZE = 16;

/**
 * Schema
 */

await db.execute(`
  CREATE TABLE IF NOT EXISTS discourse (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    thumbnail_url TEXT,
    is_audio_cleaned INTEGER NOT NULL DEFAULT 0,
    language TEXT NOT NULL DEFAULT '',
    total_tracks INTEGER NOT NULL DEFAULT 0,
    total_likes INTEGER NOT NULL DEFAULT 0,
    slug TEXT UNIQUE,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
  )
`);

await db.execute(`
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

    FOREIGN KEY (discourse_id)
      REFERENCES discourse(id)
      ON DELETE CASCADE
  )
`);

for (const [table, column] of [
	["discourse", "total_tracks"],
	["discourse", "total_likes"],
	["discourse", "language"],
	["discourse_audio", "total_likes"],
]) {
	const columns = await db.execute(`PRAGMA table_info(${table})`);
	if (!columns.rows.some((row) => row.name === column)) {
		await db.execute(`ALTER TABLE ${table} ADD COLUMN ${column} INTEGER NOT NULL DEFAULT 0`);
	}
}

await db.execute(`
  CREATE TABLE IF NOT EXISTS discourse_likes (
    discourse_id TEXT NOT NULL,
    liked_by_user_id TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (discourse_id, liked_by_user_id),

    FOREIGN KEY (discourse_id)
      REFERENCES discourse(id)
      ON DELETE CASCADE
  )
`);

await db.execute(`
  CREATE TABLE IF NOT EXISTS discourse_audio_likes (
    discourse_audio_id TEXT NOT NULL,
    liked_by_user_id TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (discourse_audio_id, liked_by_user_id),

    FOREIGN KEY (discourse_audio_id)
      REFERENCES discourse_audio(id)
      ON DELETE CASCADE
  )
`);

/**
 * Indexes
 */

await db.execute(`
  CREATE INDEX IF NOT EXISTS idx_discourse_title
  ON discourse(title)
`);

await db.execute(`
  CREATE INDEX IF NOT EXISTS idx_discourse_audio_cleaned
  ON discourse(is_audio_cleaned)
`);

await db.execute(`
  CREATE INDEX IF NOT EXISTS idx_discourse_audio_discourse_id
  ON discourse_audio(discourse_id)
`);

await db.execute(`
  CREATE INDEX IF NOT EXISTS idx_discourse_audio_title
  ON discourse_audio(title)
`);

await db.execute(`
  CREATE INDEX IF NOT EXISTS idx_discourse_audio_discourse_name
  ON discourse_audio(discourse_name)
`);

await db.execute(`
  CREATE INDEX IF NOT EXISTS idx_discourse_audio_language
  ON discourse_audio(language)
`);

/**
 * Helpers
 */

const jsonResponse = (
	data: unknown,
	status = 200,
) =>
	new Response(JSON.stringify(data), {
		status,
		headers: {
			"content-type": "application/json",
		},
	});

const parsePage = (value: string | null) => {
	const page = Number(value ?? "1");

	if (
		!Number.isInteger(page) ||
		page < 1
	) {
		return 1;
	}

	return page;
};

const parseBoolean = (
	value: string | null,
): boolean | null => {
	if (value === null) {
		return null;
	}

	const normalized =
		value.toLowerCase();

	if (
		normalized === "true" ||
		normalized === "1"
	) {
		return true;
	}

	if (
		normalized === "false" ||
		normalized === "0"
	) {
		return false;
	}

	return null;
};

const paginationMeta = (
	page: number,
	totalRecords: number,
) => ({
	page,
	page_size: PAGE_SIZE,
	total_records: totalRecords,
	total_pages: Math.ceil(
		totalRecords / PAGE_SIZE,
	),
});

/**
 * Convert SQLite integer booleans to JS booleans.
 */
const normalizeDiscourse = (
	row: any,
) => ({
	...row,
	is_audio_cleaned:
		Boolean(row.is_audio_cleaned),
});

/**
 * GET /discourses
 *
 * Query:
 *
 * ?page=1
 * ?search=dhyan
 * ?is_audio_cleaned=true
 * ?language=hindi
 * ?sort=most_liked
 */

async function getDiscourses(
	url: URL,
): Promise<Response> {
	const page = parsePage(
		url.searchParams.get("page"),
	);
	const sortByLikes = url.searchParams.get("sort") === "most_liked";

	const search =
		url.searchParams
			.get("search")
			?.trim() ?? "";
	const language =
		url.searchParams
			.get("language")
			?.trim() ?? "";

	const cleanedRaw =
		url.searchParams.get(
			"is_audio_cleaned",
		);

	const isAudioCleaned =
		parseBoolean(cleanedRaw);

	if (
		cleanedRaw !== null &&
		isAudioCleaned === null
	) {
		return jsonResponse(
			{
				error:
					"is_audio_cleaned must be true or false.",
			},
			400,
		);
	}

	const where: string[] = [];
	const args: any[] = [];

	if (search) {
		where.push(
			"title LIKE ? COLLATE NOCASE",
		);

		args.push(`%${search}%`);
	}
	if (language) {
		where.push("language = ? COLLATE NOCASE");
		args.push(language);
	}

	if (isAudioCleaned !== null) {
		where.push(
			"is_audio_cleaned = ?",
		);

		args.push(
			isAudioCleaned ? 1 : 0,
		);
	}

	const whereSql =
		where.length > 0
			? `WHERE ${where.join(" AND ")}`
			: "";

	/**
	 * Count matching records.
	 */
	const countResult =
		await db.execute({
			sql: `
        SELECT COUNT(*) AS total
        FROM discourse
        ${whereSql}
      `,
			args,
		});

	const totalRecords = Number(
		countResult.rows[0]?.total ?? 0,
	);

	const offset =
		(page - 1) * PAGE_SIZE;

	const result =
		await db.execute({
			sql: `
        SELECT *
        FROM discourse
        ${whereSql}
		ORDER BY ${sortByLikes ? "total_likes DESC, title COLLATE NOCASE ASC" : "title COLLATE NOCASE ASC"}
        LIMIT ?
        OFFSET ?
      `,
			args: [
				...args,
				PAGE_SIZE,
				offset,
			],
		});

	return jsonResponse({
		data: result.rows.map(
			normalizeDiscourse,
		),

		meta: paginationMeta(
			page,
			totalRecords,
		),
	});
}

/**
 * GET /discourse-audios
 *
 * Query:
 *
 * ?page=1
 * ?search=upanishad
 * ?discourse_name=Adhyatam Upanishad
 * ?language=hindi
 * ?sort=most_liked
 *
 * OR:
 *
 * ?discourse_id=UUID
 *
 * discourse_id intentionally returns ALL
 * tracks without pagination.
 */

async function getDiscourseAudios(
	url: URL,
): Promise<Response> {
	const discourseId =
		url.searchParams
			.get("discourse_id")
			?.trim();

	/**
	 * Special case:
	 *
	 * Fetch ALL audio for one discourse.
	 * No pagination.
	 */
	if (discourseId) {
		const result =
			await db.execute({
				sql: `
          SELECT *
          FROM discourse_audio
          WHERE discourse_id = ?
          ORDER BY
            track_number ASC,
            title COLLATE NOCASE ASC
        `,
				args: [discourseId],
			});

		return jsonResponse({
			data: result.rows,

			meta: {
				total_records:
				result.rows.length,
			},
		});
	}

	const page = parsePage(
		url.searchParams.get("page"),
	);
	const sortByLikes = url.searchParams.get("sort") === "most_liked";

	const search =
		url.searchParams
			.get("search")
			?.trim() ?? "";

	const discourseName =
		url.searchParams
			.get("discourse_name")
			?.trim() ?? "";

	const language =
		url.searchParams
			.get("language")
			?.trim() ?? "";

	const where: string[] = [];
	const args: any[] = [];

	/**
	 * Search audio title
	 */
	if (search) {
		where.push(
			"title LIKE ? COLLATE NOCASE",
		);

		args.push(`%${search}%`);
	}

	/**
	 * Discourse name filter.
	 *
	 * Exact match, case insensitive.
	 */
	if (discourseName) {
		where.push(
			"discourse_name = ? COLLATE NOCASE",
		);

		args.push(discourseName);
	}

	/**
	 * Language filter.
	 *
	 * Example:
	 *
	 * hindi
	 * english
	 */
	if (language) {
		where.push(
			"language = ? COLLATE NOCASE",
		);

		args.push(language);
	}

	const whereSql =
		where.length > 0
			? `WHERE ${where.join(" AND ")}`
			: "";

	/**
	 * Count
	 */
	const countResult =
		await db.execute({
			sql: `
        SELECT COUNT(*) AS total
        FROM discourse_audio
        ${whereSql}
      `,
			args,
		});

	const totalRecords = Number(
		countResult.rows[0]?.total ?? 0,
	);

	const offset =
		(page - 1) * PAGE_SIZE;

	/**
	 * Records
	 */
	const result =
		await db.execute({
			sql: `
        SELECT *
        FROM discourse_audio
        ${whereSql}

		ORDER BY ${sortByLikes
			? "total_likes DESC, discourse_name COLLATE NOCASE ASC, track_number ASC, title COLLATE NOCASE ASC"
			: "discourse_name COLLATE NOCASE ASC, track_number ASC, title COLLATE NOCASE ASC"}

        LIMIT ?
        OFFSET ?
      `,
			args: [
				...args,
				PAGE_SIZE,
				offset,
			],
		});

	return jsonResponse({
		data: result.rows,

		meta: paginationMeta(
			page,
			totalRecords,
		),
	});
}

/**
 * PUT /discourses/:id/like
 *
 * Body:
 *
 * {
 *   "user_id": "123"
 * }
 */

async function likeDiscourse(
	discourseId: string,
	request: Request,
): Promise<Response> {
	const payload: any =
		await request
			.json()
			.catch(() => null);

	const userId =
		payload?.user_id?.trim();

	if (!userId) {
		return jsonResponse(
			{
				error:
					"user_id is required.",
			},
			400,
		);
	}

	/**
	 * Make sure discourse exists.
	 */
	const discourse =
		await db.execute({
			sql: `
        SELECT id
        FROM discourse
        WHERE id = ?
        LIMIT 1
      `,
			args: [discourseId],
		});

	if (!discourse.rows.length) {
		return jsonResponse(
			{
				error:
					"Discourse not found.",
			},
			404,
		);
	}

	/**
	 * INSERT OR IGNORE makes this
	 * endpoint idempotent.
	 */
	const likeResult = await db.execute({
		sql: `
      INSERT OR IGNORE INTO discourse_likes (
        discourse_id,
        liked_by_user_id
      )
      VALUES (?, ?)
    `,
		args: [
			discourseId,
			userId,
		],
	});
	if (likeResult.rowsAffected) await db.execute({
		sql: `UPDATE discourse SET total_likes = total_likes + 1 WHERE id = ?`,
		args: [discourseId],
	});

	const countResult =
		await db.execute({
			sql: `
		SELECT total_likes AS total
		FROM discourse
		WHERE id = ?
      `,
			args: [discourseId],
		});

	return jsonResponse({
		data: {
			discourse_id:
			discourseId,

			liked_by_user_id:
			userId,

			liked: true,

			total_likes: Number(
				countResult.rows[0]
					?.total ?? 0,
			),
		},
	});
}

/**
 * PUT /discourse-audios/:id/like
 *
 * Body:
 *
 * {
 *   "user_id": "123"
 * }
 */

async function likeDiscourseAudio(
	audioId: string,
	request: Request,
): Promise<Response> {
	const payload: any =
		await request
			.json()
			.catch(() => null);

	const userId =
		payload?.user_id?.trim();

	if (!userId) {
		return jsonResponse(
			{
				error:
					"user_id is required.",
			},
			400,
		);
	}

	/**
	 * Make sure audio exists.
	 */
	const audio =
		await db.execute({
			sql: `
        SELECT id
        FROM discourse_audio
        WHERE id = ?
        LIMIT 1
      `,
			args: [audioId],
		});

	if (!audio.rows.length) {
		return jsonResponse(
			{
				error:
					"Discourse audio not found.",
			},
			404,
		);
	}

	const likeResult = await db.execute({
		sql: `
      INSERT OR IGNORE INTO discourse_audio_likes (
        discourse_audio_id,
        liked_by_user_id
      )
      VALUES (?, ?)
    `,
		args: [
			audioId,
			userId,
		],
	});
	if (likeResult.rowsAffected) {
		await db.batch([
			{
				sql: `UPDATE discourse_audio SET total_likes = total_likes + 1 WHERE id = ?`,
				args: [audioId],
			},
			{
				sql: `UPDATE discourse SET total_likes = total_likes + 1 WHERE id = (SELECT discourse_id FROM discourse_audio WHERE id = ?)`,
				args: [audioId],
			},
		], "write");
	}

	const countResult =
		await db.execute({
			sql: `
		SELECT total_likes AS total
		FROM discourse_audio
		WHERE id = ?
      `,
			args: [audioId],
		});

	return jsonResponse({
		data: {
			discourse_audio_id:
			audioId,

			liked_by_user_id:
			userId,

			liked: true,

			total_likes: Number(
				countResult.rows[0]
					?.total ?? 0,
			),
		},
	});
}

/**
 * Router
 */

BunnySDK.net.http.serve(
	async (
		request: Request,
	): Promise<Response> => {
		try {
			const url = new URL(
				request.url,
			);

			const parts =
				url.pathname
					.split("/")
					.filter(Boolean);

			const resource = parts[0];
			const id = parts[1];
			const action = parts[2];

			/**
			 * Health / API index
			 */
			if (!resource) {
				return jsonResponse({
					message:
						"Osho Audio API",

					endpoints: {
						discourses:
							"/discourses",

						discourse_audios:
							"/discourse-audios",
					},
				});
			}

			/**
			 * --------------------------------
			 * DISCOURSES
			 * --------------------------------
			 */

			if (
				resource ===
				"discourses"
			) {
				/**
				 * GET /discourses
				 */
				if (
					request.method ===
					"GET" &&
					!id
				) {
					return getDiscourses(
						url,
					);
				}

				/**
				 * PUT
				 * /discourses/:id/like
				 */
				if (
					request.method ===
					"PUT" &&
					id &&
					action === "like"
				) {
					return likeDiscourse(
						id,
						request,
					);
				}

				return jsonResponse(
					{
						error:
							"Not found",
					},
					404,
				);
			}

			/**
			 * --------------------------------
			 * DISCOURSE AUDIO
			 * --------------------------------
			 */

			if (
				resource ===
				"discourse-audios"
			) {
				/**
				 * GET /discourse-audios
				 *
				 * Also handles:
				 *
				 * GET
				 * /discourse-audios?discourse_id=...
				 */
				if (
					request.method ===
					"GET" &&
					!id
				) {
					return getDiscourseAudios(
						url,
					);
				}

				/**
				 * PUT
				 * /discourse-audios/:id/like
				 */
				if (
					request.method ===
					"PUT" &&
					id &&
					action === "like"
				) {
					return likeDiscourseAudio(
						id,
						request,
					);
				}

				return jsonResponse(
					{
						error:
							"Not found",
					},
					404,
				);
			}

			return jsonResponse(
				{
					error:
						"Not found",
				},
				404,
			);
		} catch (error) {
			console.error(error);

			return jsonResponse(
				{
					error:
						"Internal server error",
				},
				500,
			);
		}
	},
);
