APP_PLATFORM := android-9
ifeq ($(NO_NEON),)
APP_ABI := armeabi-v7a
else
APP_ABI := armeabi
endif
