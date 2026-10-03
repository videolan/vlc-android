https://oshoapi-0i8tq.bunny.run/discourses/?page=1

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


# Seed
// https://seeddata-pdiw5.bunny.run/seed
POST /seed
