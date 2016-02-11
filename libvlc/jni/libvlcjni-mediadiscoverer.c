/*****************************************************************************
 * libvlcjni-mediadiscoverer.c
 *****************************************************************************
 * Copyright © 2015 VLC authors, VideoLAN and VideoLabs
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

#include "libvlcjni-vlcobject.h"

void
Java_org_videolan_libvlc_MediaDiscoverer_nativeNew(JNIEnv *env,
                                                   jobject thiz, jobject libVlc,
                                                   jstring jname)
{
    vlcjni_object *p_obj;
    const char* p_name;

    if (!jname || !(p_name = (*env)->GetStringUTFChars(env, jname, 0)))
    {
        throw_IllegalArgumentException(env, "jname invalid");
        return;
    }

    p_obj = VLCJniObject_newFromJavaLibVlc(env, thiz, libVlc);
    if (!p_obj)
    {
        (*env)->ReleaseStringUTFChars(env, jname, p_name);
        return;
    }

    p_obj->u.p_md = libvlc_media_discoverer_new(p_obj->p_libvlc, p_name);

    (*env)->ReleaseStringUTFChars(env, jname, p_name);

    if (!p_obj->u.p_md)
    {
        VLCJniObject_release(env, thiz, p_obj);
        throw_IllegalStateException(env, "can't create MediaDiscoverer instance");
        return;
    }
}

void
Java_org_videolan_libvlc_MediaDiscoverer_nativeRelease(JNIEnv *env,
                                                       jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_discoverer_release(p_obj->u.p_md);

    VLCJniObject_release(env, thiz, p_obj);
}

jboolean
Java_org_videolan_libvlc_MediaDiscoverer_nativeStart(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    return libvlc_media_discoverer_start(p_obj->u.p_md) == 0 ? true : false;
}

void
Java_org_videolan_libvlc_MediaDiscoverer_nativeStop(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_media_discoverer_stop(p_obj->u.p_md);
}


static jobject
service_to_object(JNIEnv *env, libvlc_media_discoverer_description *p_service)
{
    jstring jname = NULL;
    jstring jlongName = NULL;

    jname = (*env)->NewStringUTF(env, p_service->psz_name);
    jlongName = (*env)->NewStringUTF(env, p_service->psz_longname);

    return (*env)->CallStaticObjectMethod(env, fields.MediaDiscoverer.clazz,
                        fields.MediaDiscoverer.createDescriptionFromNativeID,
                        jname, jlongName, p_service->i_cat);
}

jobject
Java_org_videolan_libvlc_MediaDiscoverer_nativeList(JNIEnv *env, jobject thiz,
                                                    jobject libVlc,
                                                    jint i_category)
{
    vlcjni_object *p_lib_obj = VLCJniObject_getInstance(env, libVlc);
    libvlc_instance_t *p_libvlc = p_lib_obj->u.p_libvlc;
    libvlc_media_discoverer_description **pp_services = NULL;
    unsigned int i_nb_services = 0;
    jobjectArray array;

    if (!p_lib_obj)
        return NULL;

    i_nb_services =
        libvlc_media_discoverer_list_get( p_libvlc, i_category,
                                              &pp_services);
    if (i_nb_services == 0)
        return NULL;

    array = (*env)->NewObjectArray(env, i_nb_services,
                                   fields.MediaDiscoverer.Description.clazz,
                                   NULL);
    if (!array)
        goto error;

    for (int i = 0; i < i_nb_services; ++i)
    {
        jobject jservice = service_to_object(env, pp_services[i]);

        (*env)->SetObjectArrayElement(env, array, i, jservice);
    }

error:
    if (pp_services)
        libvlc_media_discoverer_list_release(pp_services, i_nb_services);
    return array;
}
