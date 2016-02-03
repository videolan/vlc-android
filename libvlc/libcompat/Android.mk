LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE    := libcompat.7
LOCAL_CFLAGS    := -std=gnu99
LOCAL_SRC_FILES += pthread-condattr.c pthread-rwlocks.c
LOCAL_SRC_FILES += pthread-once.c eventfd.c sem.c pipe2.c
LOCAL_SRC_FILES += localtime.c
LOCAL_SRC_FILES += wchar/wcpcpy.c
LOCAL_SRC_FILES += wchar/wcpncpy.c
LOCAL_SRC_FILES += wchar/wcscasecmp.c
LOCAL_SRC_FILES += wchar/wcscat.c
LOCAL_SRC_FILES += wchar/wcschr.c
LOCAL_SRC_FILES += wchar/wcscmp.c
LOCAL_SRC_FILES += wchar/wcscoll.c
LOCAL_SRC_FILES += wchar/wcscpy.c
LOCAL_SRC_FILES += wchar/wcscspn.c
LOCAL_SRC_FILES += wchar/wcsdup.c
LOCAL_SRC_FILES += wchar/wcslcat.c
LOCAL_SRC_FILES += wchar/wcslcpy.c
LOCAL_SRC_FILES += wchar/wcslen.c
LOCAL_SRC_FILES += wchar/wcsncasecmp.c
LOCAL_SRC_FILES += wchar/wcsncat.c
LOCAL_SRC_FILES += wchar/wcsncmp.c
LOCAL_SRC_FILES += wchar/wcsncpy.c
LOCAL_SRC_FILES += wchar/wcsnlen.c
LOCAL_SRC_FILES += wchar/wcspbrk.c
LOCAL_SRC_FILES += wchar/wcsrchr.c
LOCAL_SRC_FILES += wchar/wcsspn.c
LOCAL_SRC_FILES += wchar/wcsstr.c
LOCAL_SRC_FILES += wchar/wcstok.c
LOCAL_SRC_FILES += wchar/wcswidth.c
LOCAL_SRC_FILES += wchar/wcsxfrm.c
LOCAL_SRC_FILES += wchar/wmemchr.c
LOCAL_SRC_FILES += wchar/wmemcmp.c
LOCAL_SRC_FILES += wchar/wmemcpy.c
LOCAL_SRC_FILES += wchar/wmemmove.c
LOCAL_SRC_FILES += wchar/wmemset.c
include $(BUILD_SHARED_LIBRARY)
