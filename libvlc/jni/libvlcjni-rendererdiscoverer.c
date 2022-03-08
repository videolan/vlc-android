/*****************************************************************************
 * libvlcjni-rendererdiscoverer.c
 *****************************************************************************
 * Copyright © 2017 VLC authors, VideoLAN and VideoLabs
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

static const libvlc_event_type_t rd_events[] = {
    libvlc_RendererDiscovererItemAdded,
    libvlc_RendererDiscovererItemDeleted,
    -1,
};

static bool
RendererDiscoverer_event_cb(vlcjni_object *p_obj, const libvlc_event_t *p_ev,
                     java_event *p_java_event)
{
    switch (p_ev->type)
    {
    case libvlc_RendererDiscovererItemAdded:
        p_java_event->arg1 = p_ev->u.renderer_discoverer_item_added.item;
        break;
    case libvlc_RendererDiscovererItemDeleted:
        p_java_event->arg1 = p_ev->u.renderer_discoverer_item_deleted.item;
        break;
    }
    p_java_event->type = p_ev->type;
    return true;
}

void
Java_org_videolan_libvlc_RendererDiscoverer_nativeNew(JNIEnv *env,
                                                      jobject thiz,
                                                      jobject libVlc,
                                                      jstring jname)
{
    vlcjni_object *p_obj;
    const char* p_name;

    if (!jname || !(p_name = (*env)->GetStringUTFChars(env, jname, 0)))
    {
        throw_Exception(env, VLCJNI_EX_ILLEGAL_ARGUMENT, "jname invalid");
        return;
    }

    p_obj = VLCJniObject_newFromJavaLibVlc(env, thiz, libVlc);
    if (!p_obj)
    {
        (*env)->ReleaseStringUTFChars(env, jname, p_name);
        return;
    }

    p_obj->u.p_rd = libvlc_renderer_discoverer_new(p_obj->p_libvlc, p_name);

    (*env)->ReleaseStringUTFChars(env, jname, p_name);

    if (!p_obj->u.p_rd)
    {
        VLCJniObject_release(env, thiz, p_obj);
        throw_Exception(env, VLCJNI_EX_OUT_OF_MEMORY, "RendererDiscoverer");
        return;
    }
    VLCJniObject_attachEvents(p_obj, RendererDiscoverer_event_cb,
                              libvlc_renderer_discoverer_event_manager(p_obj->u.p_rd),
                              rd_events);
}

void
Java_org_videolan_libvlc_RendererDiscoverer_nativeRelease(JNIEnv *env,
                                                       jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_renderer_discoverer_release(p_obj->u.p_rd);

    VLCJniObject_release(env, thiz, p_obj);
}

jboolean
Java_org_videolan_libvlc_RendererDiscoverer_nativeStart(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return false;

    return libvlc_renderer_discoverer_start(p_obj->u.p_rd) == 0;
}

void
Java_org_videolan_libvlc_RendererDiscoverer_nativeStop(JNIEnv *env, jobject thiz)
{
    vlcjni_object *p_obj = VLCJniObject_getInstance(env, thiz);

    if (!p_obj)
        return;

    libvlc_renderer_discoverer_stop(p_obj->u.p_rd);
}


static jobject
service_to_object(JNIEnv *env, libvlc_rd_description_t *p_service)
{
    jstring jname = NULL;
    jstring jlongName = NULL;

    jname = vlcNewStringUTF(env, p_service->psz_name);
    jlongName = vlcNewStringUTF(env, p_service->psz_longname);

    jobject jobj = (*env)->CallStaticObjectMethod(env, fields.RendererDiscoverer.clazz,
                        fields.RendererDiscoverer.createDescriptionFromNativeID,
                        jname, jlongName);

    (*env)->DeleteLocalRef(env, jname);
    (*env)->DeleteLocalRef(env, jlongName);
    return jobj;
}


static jobject
item_to_object(JNIEnv *env, libvlc_renderer_item_t *p_item)
{
    jstring jname = NULL;
    jstring jType = NULL;
    jstring jIconUri = NULL;
    jint jFlags = 0;

    jname = vlcNewStringUTF(env, libvlc_renderer_item_name(p_item));
    jType = vlcNewStringUTF(env, libvlc_renderer_item_type(p_item));
    jIconUri = vlcNewStringUTF(env, libvlc_renderer_item_icon_uri(p_item));
    jFlags = libvlc_renderer_item_flags(p_item);

    jobject jobj = (*env)->CallStaticObjectMethod(env, fields.RendererDiscoverer.clazz,
                        fields.RendererDiscoverer.createItemFromNativeID,
                        jname, jType, jIconUri, jFlags, (jlong) p_item);

    (*env)->DeleteLocalRef(env, jname);
    (*env)->DeleteLocalRef(env, jType);
    (*env)->DeleteLocalRef(env, jIconUri);
    return jobj;
}

jobject
Java_org_videolan_libvlc_RendererDiscoverer_nativeList(JNIEnv *env, jobject thiz,
                                                    jobject libVlc)
{
    vlcjni_object *p_lib_obj = VLCJniObject_getInstance(env, libVlc);
    libvlc_instance_t *p_libvlc = p_lib_obj->u.p_libvlc;
    libvlc_rd_description_t **pp_services = NULL;
    size_t i_nb_services = 0;
    jobjectArray array;

    if (!p_lib_obj)
        return NULL;

    i_nb_services =
        libvlc_renderer_discoverer_list_get( p_libvlc, &pp_services);
    if (i_nb_services == 0)
        return NULL;

    array = (*env)->NewObjectArray(env, i_nb_services,
                                   fields.RendererDiscoverer.Description.clazz,
                                   NULL);
    if (!array)
        goto error;

    for (size_t i = 0; i < i_nb_services; ++i)
    {
        jobject jservice = service_to_object(env, pp_services[i]);

        (*env)->SetObjectArrayElement(env, array, i, jservice);
    }

error:
    if (pp_services)
        libvlc_renderer_discoverer_list_release(pp_services, i_nb_services);
    return array;
}

jobject
Java_org_videolan_libvlc_RendererDiscoverer_nativeNewItem(JNIEnv *env, jobject thiz,
                                                    jlong ref)
{
    vlcjni_object *p_rd_obj = VLCJniObject_getInstance(env, thiz);
    vlcjni_object *p_obj;
    libvlc_renderer_item_t *item_ref = (libvlc_renderer_item_t *)(intptr_t)ref;

    if (!p_rd_obj)
        return NULL;

    jobject jitem = item_to_object(env, item_ref);

    p_obj = VLCJniObject_newFromLibVlc(env, jitem, p_rd_obj->p_libvlc);
    if (!p_obj)
        return NULL;

    p_obj->u.p_r = libvlc_renderer_item_hold(item_ref);

    return jitem;
}

void
Java_org_videolan_libvlc_RendererItem_nativeReleaseItem(JNIEnv *env, jobject thiz)
{
    libvlc_renderer_item_t *p_renderer = NULL;
    if (thiz)
    {
        vlcjni_object *p_r_obj = VLCJniObject_getInstance(env, thiz);
        if (p_r_obj)
            p_renderer = p_r_obj->u.p_r;
    }
    if (p_renderer)
        libvlc_renderer_item_release(p_renderer);

}
