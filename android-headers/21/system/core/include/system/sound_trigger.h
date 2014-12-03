/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROID_SOUND_TRIGGER_H
#define ANDROID_SOUND_TRIGGER_H

#include <stdbool.h>
#include <system/audio.h>

#define SOUND_TRIGGER_MAX_STRING_LEN 64 /* max length of strings in properties or
                                           descriptor structs */
#define SOUND_TRIGGER_MAX_LOCALE_LEN 6  /* max length of locale string. e.g en_US */
#define SOUND_TRIGGER_MAX_USERS 10      /* max number of concurrent users */
#define SOUND_TRIGGER_MAX_PHRASES 10    /* max number of concurrent phrases */

typedef enum {
    SOUND_TRIGGER_STATE_NO_INIT = -1,   /* The sound trigger service is not initialized */
    SOUND_TRIGGER_STATE_ENABLED = 0,    /* The sound trigger service is enabled */
    SOUND_TRIGGER_STATE_DISABLED = 1    /* The sound trigger service is disabled */
} sound_trigger_service_state_t;

#define RECOGNITION_MODE_VOICE_TRIGGER 0x1       /* simple voice trigger */
#define RECOGNITION_MODE_USER_IDENTIFICATION 0x2 /* trigger only if one user in model identified */
#define RECOGNITION_MODE_USER_AUTHENTICATION 0x4 /* trigger only if one user in mode
                                                    authenticated */
#define RECOGNITION_STATUS_SUCCESS 0
#define RECOGNITION_STATUS_ABORT 1
#define RECOGNITION_STATUS_FAILURE 2

#define SOUND_MODEL_STATUS_UPDATED 0

typedef enum {
    SOUND_MODEL_TYPE_UNKNOWN = -1,    /* use for unspecified sound model type */
    SOUND_MODEL_TYPE_KEYPHRASE = 0    /* use for key phrase sound models */
} sound_trigger_sound_model_type_t;

typedef struct sound_trigger_uuid_s {
    unsigned int   timeLow;
    unsigned short timeMid;
    unsigned short timeHiAndVersion;
    unsigned short clockSeq;
    unsigned char  node[6];
} sound_trigger_uuid_t;

/*
 * sound trigger implementation descriptor read by the framework via get_properties().
 * Used by SoundTrigger service to report to applications and manage concurrency and policy.
 */
struct sound_trigger_properties {
    char                 implementor[SOUND_TRIGGER_MAX_STRING_LEN]; /* implementor name */
    char                 description[SOUND_TRIGGER_MAX_STRING_LEN]; /* implementation description */
    unsigned int         version;               /* implementation version */
    sound_trigger_uuid_t uuid;                  /* unique implementation ID.
                                                   Must change with version each version */
    unsigned int         max_sound_models;      /* maximum number of concurrent sound models
                                                   loaded */
    unsigned int         max_key_phrases;       /* maximum number of key phrases */
    unsigned int         max_users;             /* maximum number of concurrent users detected */
    unsigned int         recognition_modes;     /* all supported modes.
                                                   e.g RECOGNITION_MODE_VOICE_TRIGGER */
    bool                 capture_transition;    /* supports seamless transition from detection
                                                   to capture */
    unsigned int         max_buffer_ms;         /* maximum buffering capacity in ms if
                                                   capture_transition is true*/
    bool                 concurrent_capture;    /* supports capture by other use cases while
                                                   detection is active */
    bool                 trigger_in_event;      /* returns the trigger capture in event */
    unsigned int         power_consumption_mw;  /* Rated power consumption when detection is active
                                                   with TDB silence/sound/speech ratio */
};

typedef int sound_trigger_module_handle_t;

struct sound_trigger_module_descriptor {
    sound_trigger_module_handle_t   handle;
    struct sound_trigger_properties properties;
};

typedef int sound_model_handle_t;

/*
 * Generic sound model descriptor. This struct is the header of a larger block passed to
 * load_sound_model() and containing the binary data of the sound model.
 * Proprietary representation of users in binary data must match information indicated
 * by users field
 */
struct sound_trigger_sound_model {
    sound_trigger_sound_model_type_t type;        /* model type. e.g. SOUND_MODEL_TYPE_KEYPHRASE */
    sound_trigger_uuid_t             uuid;        /* unique sound model ID. */
    sound_trigger_uuid_t             vendor_uuid; /* unique vendor ID. Identifies the engine the
                                                  sound model was build for */
    unsigned int                     data_size;   /* size of opaque model data */
    unsigned int                     data_offset; /* offset of opaque data start from head of struct
                                                    (e.g sizeof struct sound_trigger_sound_model) */
};

/* key phrase descriptor */
struct sound_trigger_phrase {
    unsigned int id;                /* keyphrase ID */
    unsigned int recognition_mode;  /* recognition modes supported by this key phrase */
    unsigned int num_users;         /* number of users in the key phrase */
    unsigned int users[SOUND_TRIGGER_MAX_USERS]; /* users ids: (not uid_t but sound trigger
                                                 specific IDs */
    char         locale[SOUND_TRIGGER_MAX_LOCALE_LEN]; /* locale - JAVA Locale style (e.g. en_US) */
    char         text[SOUND_TRIGGER_MAX_STRING_LEN];   /* phrase text in UTF-8 format. */
};

/*
 * Specialized sound model for key phrase detection.
 * Proprietary representation of key phrases in binary data must match information indicated
 * by phrases field
 */
struct sound_trigger_phrase_sound_model {
    struct sound_trigger_sound_model common;
    unsigned int                     num_phrases;   /* number of key phrases in model */
    struct sound_trigger_phrase      phrases[SOUND_TRIGGER_MAX_PHRASES];
};


/*
 * Generic recognition event sent via recognition callback
 */
struct sound_trigger_recognition_event {
    int                              status;            /* recognition status e.g.
                                                           RECOGNITION_STATUS_SUCCESS */
    sound_trigger_sound_model_type_t type;              /* event type, same as sound model type.
                                                           e.g. SOUND_MODEL_TYPE_KEYPHRASE */
    sound_model_handle_t             model;             /* loaded sound model that triggered the
                                                           event */
    bool                             capture_available; /* it is possible to capture audio from this
                                                           utterance buffered by the
                                                           implementation */
    int                              capture_session;   /* audio session ID. framework use */
    int                              capture_delay_ms;  /* delay in ms between end of model
                                                           detection and start of audio available
                                                           for capture. A negative value is possible
                                                           (e.g. if key phrase is also available for
                                                           capture */
    int                              capture_preamble_ms; /* duration in ms of audio captured
                                                            before the start of the trigger.
                                                            0 if none. */
    bool                             trigger_in_data; /* the opaque data is the capture of
                                                            the trigger sound */
    audio_config_t                   audio_config;        /* audio format of either the trigger in
                                                             event data or to use for capture of the
                                                             rest of the utterance */
    unsigned int                     data_size;         /* size of opaque event data */
    unsigned int                     data_offset;       /* offset of opaque data start from start of
                                                          this struct (e.g sizeof struct
                                                          sound_trigger_phrase_recognition_event) */
};

/*
 * Confidence level for each user in struct sound_trigger_phrase_recognition_extra
 */
struct sound_trigger_confidence_level {
    unsigned int user_id;   /* user ID */
    unsigned int level;     /* confidence level in percent (0 - 100).
                               - min level for recognition configuration
                               - detected level for recognition event */
};

/*
 * Specialized recognition event for key phrase detection
 */
struct sound_trigger_phrase_recognition_extra {
    unsigned int id;                /* keyphrase ID */
    unsigned int recognition_modes; /* recognition modes used for this keyphrase */
    unsigned int confidence_level;  /* confidence level for mode RECOGNITION_MODE_VOICE_TRIGGER */
    unsigned int num_levels;        /* number of user confidence levels */
    struct sound_trigger_confidence_level levels[SOUND_TRIGGER_MAX_USERS];
};

struct sound_trigger_phrase_recognition_event {
    struct sound_trigger_recognition_event common;
    unsigned int                           num_phrases;
    struct sound_trigger_phrase_recognition_extra phrase_extras[SOUND_TRIGGER_MAX_PHRASES];
};

/*
 * configuration for sound trigger capture session passed to start_recognition()
 */
struct sound_trigger_recognition_config {
    audio_io_handle_t    capture_handle;    /* IO handle that will be used for capture.
                                            N/A if capture_requested is false */
    audio_devices_t      capture_device;    /* input device requested for detection capture */
    bool                 capture_requested; /* capture and buffer audio for this recognition
                                            instance */
    unsigned int         num_phrases;   /* number of key phrases recognition extras */
    struct sound_trigger_phrase_recognition_extra phrases[SOUND_TRIGGER_MAX_PHRASES];
                                           /* configuration for each key phrase */
    unsigned int        data_size;         /* size of opaque capture configuration data */
    unsigned int        data_offset;       /* offset of opaque data start from start of this struct
                                           (e.g sizeof struct sound_trigger_recognition_config) */
};

/*
 * Event sent via load sound model callback
 */
struct sound_trigger_model_event {
    int                  status;      /* sound model status e.g. SOUND_MODEL_STATUS_UPDATED */
    sound_model_handle_t model;       /* loaded sound model that triggered the event */
    unsigned int         data_size;   /* size of event data if any. Size of updated sound model if
                                       status is SOUND_MODEL_STATUS_UPDATED */
    unsigned int         data_offset; /* offset of data start from start of this struct
                                       (e.g sizeof struct sound_trigger_model_event) */
};


#endif  // ANDROID_SOUND_TRIGGER_H
