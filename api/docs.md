https://oshoapi-0i8tq.bunny.run/discourses/?page=1

All routes require the `x-api-key` header matching the configured API key.

GET /discourses
GET /discourses?page=2
GET /discourses?search=dhyan
GET /discourses?is_audio_cleaned=true
GET /discourses?language=hindi
GET /discourses?search=dhyan&is_audio_cleaned=true&page=1
GET /discourses?language=english&sort=most_liked&page=1
GET /discourses?sort=most_liked&page=1

GET /discourse-audios
GET /discourse-audios?page=2
GET /discourse-audios?search=upanishad
GET /discourse-audios?discourse_name=Adhyatam%20Upanishad
GET /discourse-audios?language=hindi
GET /discourse-audios?search=01&language=hindi&page=1
GET /discourse-audios?sort=most_liked&page=1

Add `sort=most_liked` to either paginated list to order by descending `total_likes`. Ties retain the route's default title/name and track ordering. Omitting `sort` preserves the default order.
The paginated `/discourses` route also accepts `language=hindi` or `language=english`; omitting `language` returns all languages.

# No pagination:
GET /discourse-audios?discourse_id=UUID

PUT /discourses/:id/like
BODY: { "user_id": "USER_UUID" }
Adds one to the discourse `total_likes` on the first like from this user. Repeated likes are idempotent.

PUT /discourse-audios/:id/like
BODY: { "user_id": "USER_UUID" }
Adds one to the audio and parent discourse `total_likes` on the first like from this user. Repeated likes are idempotent.

# Listening stats

POST /stats
BODY: `{ "user_id": "USER_UUID", "discourse_id": "DISCOURSE_UUID", "discourse_audio_id": "AUDIO_UUID" }`
Both IDs are required and must refer to existing records. If the same user has recorded either discourse or audio ID within the prior 24 hours, the entire event is suppressed. New events return `201` with `{ "data": { "recorded": true } }`; suppressed repeats return `200` with `{ "data": { "recorded": false, "reason": "duplicate_within_24_hours" } }`.

GET /stats?by=discourse&time=7_days
GET /stats?by=discourse_audio&time=24_hours
`by` accepts `discourse` or `discourse_audio`; `time` accepts `7_days` or `24_hours`. Returns up to 16 full discourse or audio objects, each with an additional `plays` count, ordered by plays descending and ID ascending. Records are fetched separately after ranking IDs; no SQL join is used.


# Seed
// https://seeddata-pdiw5.bunny.run/seed
POST /seed
