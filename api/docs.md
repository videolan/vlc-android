https://oshoapi-0i8tq.bunny.run/discourses/?page=1

GET /discourses
GET /discourses?page=2
GET /discourses?search=dhyan
GET /discourses?is_audio_cleaned=true
GET /discourses?search=dhyan&is_audio_cleaned=true&page=1

GET /discourse-audios
GET /discourse-audios?page=2
GET /discourse-audios?search=upanishad
GET /discourse-audios?discourse_name=Adhyatam%20Upanishad
GET /discourse-audios?language=hindi
GET /discourse-audios?search=01&language=hindi&page=1

# No pagination:
GET /discourse-audios?discourse_id=UUID

PUT /discourses/:id/like
BODY: { "user_id": "USER_UUID" }

PUT /discourse-audios/:id/like
BODY: { "user_id": "USER_UUID" }


# Seed
// https://seeddata-pdiw5.bunny.run/seed
POST /seed
