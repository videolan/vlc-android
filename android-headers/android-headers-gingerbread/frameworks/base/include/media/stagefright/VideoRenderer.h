/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef VIDEO_RENDERER_H_

#define VIDEO_RENDERER_H_

#include <sys/types.h>
#ifdef OMAP_ENHANCEMENT
#include "binder/IMemory.h"
#include <utils/Vector.h>
#endif

namespace android {

#ifdef OMAP_ENHANCEMENT
typedef void (*release_rendered_buffer_callback)(const sp<IMemory>& mem, void *cookie);
typedef struct {
    uint32_t decoded_width;
    uint32_t decoded_height;
    uint32_t buffercount;
    uint32_t display_width;
    uint32_t display_height;
    }render_resize_params;
#endif

class VideoRenderer {
public:
    virtual ~VideoRenderer() {}

    virtual void render(
            const void *data, size_t size, void *platformPrivate) = 0;

#ifdef OMAP_ENHANCEMENT
    virtual Vector< sp<IMemory> > getBuffers() = 0;
    virtual bool setCallback(release_rendered_buffer_callback cb, void *cookie) {return false;}
    virtual void set_s3d_frame_layout(uint32_t s3d_mode, uint32_t s3d_fmt, uint32_t s3d_order, uint32_t s3d_subsampling) {}
    virtual void resizeRenderer(void* resize_params) = 0;
    virtual void requestRendererClone(bool enable) = 0;
#endif

protected:
    VideoRenderer() {}

    VideoRenderer(const VideoRenderer &);
    VideoRenderer &operator=(const VideoRenderer &);
};

}  // namespace android

#endif  // VIDEO_RENDERER_H_
