/*****************************************************************************
 * libvlcjni-glrenderer.c
 *****************************************************************************
 * Copyright © 2018 VLC authors, VideoLAN and VideoLabs
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

#include <assert.h>
#include <pthread.h>

#include <GLES2/gl2.h>
#include <EGL/egl.h>

#include "libvlcjni-vlcobject.h"

#define THREAD_NAME "GLRenderer"
extern JNIEnv *jni_get_env(const char *name);

struct glrenderer
{
    pthread_mutex_t lock;

    EGLint egl_version;

    EGLDisplay display;
    EGLSurface surface;
    EGLContext context;

    unsigned width;
    unsigned height;
    GLuint texs[3];
    GLuint fbos[3];
    size_t idx_render;
    size_t idx_swap;
    size_t idx_display;
    bool updated;
    bool destroy_unused_egl;
};

static struct glrenderer *
GLRenderer_getInstance(JNIEnv *env, jobject thiz)
{
    struct glrenderer *glr = (struct glrenderer*)(intptr_t)
        (*env)->GetLongField(env, thiz, fields.GLRenderer.mInstanceID);
    if (!glr)
        throw_Exception(env, VLCJNI_EX_ILLEGAL_STATE,
                        "can't get GLRenderer instance");
    return glr;
}


static void
GLRenderer_setInstance(JNIEnv *env, jobject thiz, struct glrenderer *glr)
{
    (*env)->SetLongField(env, thiz, fields.GLRenderer.mInstanceID,
                         (jlong)(intptr_t)glr);
}

static inline void swap(size_t *a, size_t *b)
{
    size_t tmp = *a;
    *a = *b;
    *b = tmp;
}

static void
glrenderer_destroy_egl(struct glrenderer *glr)
{
    if (glr->display != EGL_NO_DISPLAY)
    {
        if (glr->context != EGL_NO_CONTEXT)
        {
            eglDestroyContext(glr->display, glr->context);
            glr->context = EGL_NO_CONTEXT;
        }
        if (glr->surface != EGL_NO_SURFACE)
        {
            eglDestroySurface(glr->display, glr->surface);
            glr->surface = EGL_NO_SURFACE;
        }
        glr->display = EGL_NO_DISPLAY;
    }
    glr->destroy_unused_egl = false;
}

static bool
gl_setup(void* opaque)
{
    struct glrenderer *glr = opaque;

    pthread_mutex_lock(&glr->lock);

    glr->width = glr->height = 0;
    glr->updated = false;
    glr->destroy_unused_egl = false;

    pthread_mutex_unlock(&glr->lock);
    return true;
}

static void
gl_cleanup_locked(struct glrenderer *glr)
{
    if (glr->width == 0 && glr->height == 0)
        return;

    glDeleteTextures(3, glr->texs);
    glDeleteFramebuffers(3, glr->fbos);
    glr->width = glr->height = 0;
}

static void
gl_resize_locked(struct glrenderer *glr, unsigned width, unsigned height)
{
    if (width != glr->width || height != glr->height)
        gl_cleanup_locked(glr);

    glGenTextures(3, glr->texs);
    glGenFramebuffers(3, glr->fbos);

    for (int i = 0; i < 3; i++)
    {
        glBindTexture(GL_TEXTURE_2D, glr->texs[i]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        glBindFramebuffer(GL_FRAMEBUFFER, glr->fbos[i]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, glr->texs[i], 0);
    }
    glBindTexture(GL_TEXTURE_2D, 0);

    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);

    if (status != GL_FRAMEBUFFER_COMPLETE)
        return;

    glr->width = width;
    glr->height = height;

    glBindFramebuffer(GL_FRAMEBUFFER, glr->fbos[glr->idx_render]);
}

static void
gl_cleanup(void* opaque)
{
    struct glrenderer *glr = opaque;

    pthread_mutex_lock(&glr->lock);

    if (glr->display)
    {
        EGLBoolean ret = eglMakeCurrent(glr->display, glr->surface,
                                        glr->surface, glr->context);
        if (ret)
        {
            gl_cleanup_locked(glr);
            eglMakeCurrent(glr->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        }
    }

    if (glr->destroy_unused_egl)
        glrenderer_destroy_egl(glr);

    glr->width = glr->height = 0;

    pthread_mutex_unlock(&glr->lock);
}

static void
gl_resize(void* opaque, unsigned width, unsigned height)
{
    struct glrenderer *glr = opaque;

    pthread_mutex_lock(&glr->lock);
    gl_resize_locked(glr, width, height);
    pthread_mutex_unlock(&glr->lock);
}

static void
gl_swap(void* opaque)
{
    struct glrenderer *glr = opaque;

    pthread_mutex_lock(&glr->lock);
    glr->updated = true;
    swap(&glr->idx_swap, &glr->idx_render);
    glBindFramebuffer(GL_FRAMEBUFFER, glr->fbos[glr->idx_render]);
    pthread_mutex_unlock(&glr->lock);
}

static bool
gl_makeCurrent(void* opaque, bool enter)
{
    struct glrenderer *glr = opaque;

    pthread_mutex_lock(&glr->lock);

    EGLBoolean ret;
    if (!glr->display)
        ret = EGL_FALSE;
    else if (enter)
        ret = eglMakeCurrent(glr->display, glr->surface, glr->surface, glr->context);
    else
        ret = eglMakeCurrent(glr->display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

    pthread_mutex_unlock(&glr->lock);
    return ret;
}

static void*
gl_getProcAddress(void* opaque, const char* fct_name)
{
    (void) opaque;
    return eglGetProcAddress(fct_name);
}

void
Java_org_videolan_libvlc_GLRenderer_nativeInit(JNIEnv *env, jobject thiz,
                                               jobject thizmp,
                                               jint egl_version)
{
    if (egl_version != 2 && egl_version != 3)
    {
        throw_Exception(env, VLCJNI_EX_ILLEGAL_ARGUMENT,
                        "Invalid EGLVersion (should be 2 or 3)");
    }

    vlcjni_object *mpobj = VLCJniObject_getInstance(env, thizmp);
    if (!mpobj)
        return;
    if (!mpobj->u.p_mp)
    {
        throw_Exception(env, VLCJNI_EX_ILLEGAL_STATE,
                        "MediaPlayer instance invalid");
        return;
    }

    struct glrenderer *glr = malloc(sizeof(*glr));
    if (!glr)
    {
        throw_Exception(env, VLCJNI_EX_OUT_OF_MEMORY, "GLRenderer");
        return;
    }

    glr->egl_version = egl_version;

    glr->display = EGL_NO_DISPLAY;
    glr->surface = EGL_NO_SURFACE;
    glr->context = EGL_NO_CONTEXT;

    glr->width = glr->height = 0;

    glr->idx_render = 0;
    glr->idx_swap = 1;
    glr->idx_display = 2;

    glr->updated = false;

    pthread_mutex_init(&glr->lock, NULL);

    libvlc_video_set_opengl_callbacks(mpobj->u.p_mp,
                                      libvlc_gl_engine_gles2,
                                      gl_setup,
                                      gl_cleanup,
                                      gl_resize,
                                      gl_swap,
                                      gl_makeCurrent,
                                      gl_getProcAddress,
                                      glr);

    GLRenderer_setInstance(env, thiz, glr);
}

void
Java_org_videolan_libvlc_GLRenderer_nativeRelease(JNIEnv *env, jobject thiz)
{
    struct glrenderer *glr = GLRenderer_getInstance(env, thiz);
    if (!glr)
        return;

    pthread_mutex_destroy(&glr->lock);

    glrenderer_destroy_egl(glr);
    free(glr);

    GLRenderer_setInstance(env, thiz, NULL);
}

void
Java_org_videolan_libvlc_GLRenderer_nativeOnSurfaceCreated(JNIEnv *env, jobject thiz)
{
    struct glrenderer *glr = GLRenderer_getInstance(env, thiz);
    if (!glr)
        return;

    pthread_mutex_lock(&glr->lock);

    glrenderer_destroy_egl(glr);

    const EGLint config_attr[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
        EGL_RED_SIZE,   8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE,  8,
        EGL_ALPHA_SIZE,  8,
        EGL_NONE
    };

    const EGLint surface_attr[] = {
        EGL_WIDTH, 2,
        EGL_HEIGHT, 2,
        EGL_NONE
    };

    const EGLint ctx_attr[] = {
        EGL_CONTEXT_CLIENT_VERSION, glr->egl_version,
        EGL_NONE
    };

    EGLConfig config;
    EGLint num_configs;

    glr->display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (glr->display == EGL_NO_DISPLAY || eglGetError() != EGL_SUCCESS)
    {
        throw_Exception(env, VLCJNI_EX_RUNTIME,
                        "eglGetCurrentDisplay error: %x", eglGetError());
        goto error;
    }

    if (!eglInitialize(glr->display, NULL, NULL))
    {
        throw_Exception(env, VLCJNI_EX_RUNTIME,
                        "eglInitialize() error: %x", eglGetError());
        goto error;
    }

    if (!eglChooseConfig(glr->display, config_attr, &config, 1, &num_configs)
       || eglGetError() != EGL_SUCCESS)
    {
        throw_Exception(env, VLCJNI_EX_RUNTIME,
                        "eglGetConfigAttrib() error: %x", eglGetError());
        goto error;
    }

    EGLContext current_ctx = eglGetCurrentContext();
    if (eglGetError() != EGL_SUCCESS)
    {
        throw_Exception(env, VLCJNI_EX_RUNTIME,
                        "eglGetCurrentContext() error %x", eglGetError());
        goto error;
    }

    glr->surface = eglCreatePbufferSurface(glr->display, config, surface_attr);
    if (glr->surface == EGL_NO_SURFACE || eglGetError() != EGL_SUCCESS)
    {
        throw_Exception(env, VLCJNI_EX_RUNTIME,
                        "eglCreatePbufferSurface() error %x", eglGetError());
        goto error;
    }


    glr->context = eglCreateContext(glr->display, config,  current_ctx, ctx_attr);
    if (glr->context == EGL_NO_CONTEXT || eglGetError() != EGL_SUCCESS)
    {
        throw_Exception(env, VLCJNI_EX_RUNTIME,
                        "eglCreateContext() error: %x", eglGetError());
        goto error;
    }

    pthread_mutex_unlock(&glr->lock);

    return;
error:
    glrenderer_destroy_egl(glr);
    pthread_mutex_unlock(&glr->lock);
}

void
Java_org_videolan_libvlc_GLRenderer_nativeOnSurfaceDestroyed(JNIEnv *env, jobject thiz)
{
    struct glrenderer *glr = GLRenderer_getInstance(env, thiz);
    if (!glr)
        return;

    pthread_mutex_lock(&glr->lock);

    if (glr->width == 0 && glr->height == 0)
        glrenderer_destroy_egl(glr);
    else
        glr->destroy_unused_egl = true;

    pthread_mutex_unlock(&glr->lock);
}

jint
Java_org_videolan_libvlc_GLRenderer_nativeGetVideoTexture(JNIEnv *env, jobject thiz,
                                                          jobject point)
{
    struct glrenderer *glr = GLRenderer_getInstance(env, thiz);
    if (!glr)
        return 0;

    pthread_mutex_lock(&glr->lock);

    jint tex_id;
    if (glr->width == 0 || glr->height == 0)
        tex_id = 0;
    else
    {
        if (glr->updated)
        {
            swap(&glr->idx_swap, &glr->idx_display);
            glr->updated = false;
        }
        tex_id = glr->texs[glr->idx_display];
        if (point != NULL)
            (*env)->CallVoidMethod(env, point, fields.Point.setID,
                                  glr->width, glr->height);
    }

    pthread_mutex_unlock(&glr->lock);

    return tex_id;
}
