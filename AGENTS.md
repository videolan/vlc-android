# Osho Discourse Android context

This repo is a fork/rebrand of VLC Android. The product name is **Osho Discourse**.

Primary goal: keep VLC's local media playback base, but present it as Osho Discourse with a music-first UX and an added **Audio Mixer** feature that can play a selected local audio file in the background alongside the main player.

## Product direction

- App name and user-facing branding should be **Osho Discourse**, not VLC or VideoLAN.
- Official website should point to `sandalbar.online`.
- Source code URL should point to `https://github.com/curiouscosmos/mix-wave-android`.
- Dark theme is the default.
- Music is the home screen.
- The old home/browser screen was moved out of the first bottom-nav position.
- Bottom navigation should not show Browse.
- The video tab label should be **Videos**.
- Music screen should only show these top tabs:
  - Tracks
  - Playlists
  - Audio Mixer

## Current implemented changes to preserve

- Replaced VLC launcher/onboarding/header icons with Osho Discourse assets.
- Header icon uses 8dp rounded corners. Avoid `clipToOutline` in XML because min API is 26.
- Added bottom mini-player on Music, fixed near the bottom like Spotify.
- Removed Music random/shuffle floating button.
- Removed file-menu options:
  - Go to album
  - Go to artist
  - Create a launcher shortcut
  - Browse parent
- Added file-menu option:
  - Add to Audio Mixer
- Only files explicitly added through **Add to Audio Mixer** should appear in the Audio Mixer tab.
- Audio Mixer behavior:
  - Selected mixer file is highlighted above the list.
  - Mixer has separate volume.
  - Mixer On/Off is a solid bordered toggle button with icon.
  - Loop is a solid bordered toggle button with icon and defaults on.
  - Mixer playback follows main playback: starts/resumes with main play, pauses/stops with main pause/stop.
  - Mixer loop must restart when its track reaches end.
  - Mixer volume must not reset to 100% when main playback changes.

## Osho discourse API

The non-UI API integration lives in `application/vlc-android/src/org/videolan/vlc/discourse/`. Backend reference files are in `api/`; read `api/docs.md`, `api/types/discourse.type.ts`, and `api/edge.ts` when changing the contract. Do not integrate the `/seed` route into the Android app.

The base URL is exposed as `BuildConfig.OSHO_API_URL` from `application/vlc-android/build.gradle`. Production currently uses `https://oshoapi-0i8tq.bunny.run/`.

### Routes

- `GET /`
  - Health/API index. Call `DiscourseRepository.apiIndex()` only for diagnostics; content loading does not depend on it.
- `GET /discourses`
  - Call `getDiscourses(page, search, isAudioCleaned)`.
  - Supports `page`, `search`, and `is_audio_cleaned` query parameters.
  - Returns `PageResponse<Discourse>` with 16 records per backend page.
- `GET /discourse-audios`
  - Call the paginated `getDiscourseAudios(page, search, discourseName, language)` overload.
  - Supports `page`, `search`, `discourse_name`, and `language`. Known languages are `hindi` and `english`.
  - Returns `PageResponse<DiscourseAudio>`.
- `GET /discourse-audios?discourse_id=UUID`
  - Call `getDiscourseAudios(discourseId)`.
  - Returns every track for one discourse ordered by track number and title. This response is not paginated and uses `DiscourseAudiosResponse` with only `meta.total_records`.
- `PUT /discourses/:id/like`
  - Call `likeDiscourse(id)`; the repository supplies the persisted anonymous user ID.
- `PUT /discourse-audios/:id/like`
  - Call `likeDiscourseAudio(id)`; the repository supplies the persisted anonymous user ID.

The backend has no unlike, authentication, user registration, initial like-count, or read-user-likes route. Treat likes as one-way. Successful likes are cached locally in `likedDiscourses` and `likedAudios`; reinstalling the app creates a new anonymous identity and loses that local acknowledgement.

### Integration classes and usage

- `DiscourseModels.kt`
  - API request/response models. Keep snake_case mappings explicit with Moshi `@Json` annotations.
- `DiscourseApi.kt`
  - Retrofit contract and singleton client. It uses the shared connectivity interceptor, a 5-second connection timeout, and a 15-second read timeout.
- `DiscourseRepository.kt`
  - Entry point for ViewModels and other consumers. Its suspend functions run through Retrofit and throw network/HTTP/parsing failures to the caller; handle those failures at the calling boundary.
  - Trims query strings and omits blank filters.
  - Stores `osho_api_user_id`, `osho_api_liked_discourses`, and `osho_api_liked_audios` in the existing app settings.
- `DiscoursePlayback.kt`
  - `DiscourseAudio.toMediaWrapper()` maps a remote API track into VLC metadata: `audio_url` to the stream URI, seconds to milliseconds, discourse name to album, thumbnail to artwork, track number to queue metadata, and API UUID to `MediaWrapper.tag`.
  - Use `context.playDiscourseAudio(audio)` for one track and `context.playDiscourseAudios(audios, position)` for a queue. These route through `MediaUtils`, so the existing playback service, notification, sticky player, and Audio Mixer synchronization continue to apply.

Retrofit, OkHttp, Moshi, Kotlin coroutines, and Kotlin reflection are already available through the application modules. The project pins Moshi 1.8, whose code generator is incompatible with the current Kotlin compiler; use `KotlinJsonAdapterFactory` rather than adding `@JsonClass(generateAdapter = true)` or Moshi annotation processing.

UI/UX for catalogue browsing, search, filters, discourse details, and like controls has not been implemented. Build future UI against `DiscourseRepository`; keep API records separate from local medialibrary providers until they are converted for playback.

## Important files

- `application/vlc-android/src/org/videolan/vlc/PlaybackService.kt`
  - Main playback service and Audio Mixer playback/event handling.
- `application/vlc-android/res/layout/audio_mixer.xml`
  - Audio Mixer tab UI.
- `application/vlc-android/src/org/videolan/vlc/gui/audio/AudioBrowserFragment.kt`
  - Music tabs, Audio Mixer tab binding, shuffle FAB hiding.
- `application/vlc-android/src/org/videolan/vlc/viewmodels/mobile/AudioBrowserViewModel.kt`
  - Provides Tracks, Playlists, and Audio Mixer providers.
- `application/vlc-android/src/org/videolan/vlc/providers/medialibrary/AudioMixerProvider.kt`
  - Stores/loads mixer file URIs using `KEY_AUDIO_MIXER_FILES = "audio_mixer_files"`.
- `application/vlc-android/src/org/videolan/vlc/gui/dialogs/ContextSheet.kt`
  - Central context-menu filtering and Audio Mixer menu item.
- `application/vlc-android/src/org/videolan/vlc/util/ContextOption.kt`
  - Context option enum and default menu flags.
- `application/vlc-android/res/menu/bottom_navigation.xml`
  - Bottom nav. Browse must stay removed.
- `application/resources/src/main/res/values/ids.xml`
  - Contains standalone `nav_directories` id so old shortcuts/routes compile even though Browse is not in bottom nav.
- `application/vlc-android/src/org/videolan/vlc/StartActivity.kt`
  - Startup shortcut routing. Browser shortcut should use `R.id.nav_directories`, not any generated bottom-nav menu item.
- `application/vlc-android/src/org/videolan/vlc/discourse/`
  - Osho discourse API models, client, repository, persisted like state, and VLC playback mapping.

## Build / verification

There is no repo-local `./gradlew` in this checkout. Use:

```bash
/Users/damanmehta/.gradle/wrapper/dists/gradle-9.3.1-bin/23ovyewtku6u96viwx3xl3oks/gradle-9.3.1/bin/gradle :application:vlc-android:compileDebugKotlin
```

Useful resource check:

```bash
/Users/damanmehta/.gradle/wrapper/dists/gradle-9.3.1-bin/23ovyewtku6u96viwx3xl3oks/gradle-9.3.1/bin/gradle :application:vlc-android:packageDebugResources
```

The shell may print an `npm_config_prefix` / `nvm` warning. It is unrelated to the Android build.

## Repo hygiene

- Prefer minimal diffs. Reuse existing VLC patterns instead of adding new abstractions.
- Use `rg` for searches.
- Use `apply_patch` for edits.
- Do not remove generated/signed APK artifacts unless explicitly asked.
- `.ai/` is local/untracked context; do not edit it unless explicitly asked.
- Build output under `application/vlc-android/build/` may change during verification; treat it as generated.
- After each change, commit the change to master branch with proper commit message and push.
