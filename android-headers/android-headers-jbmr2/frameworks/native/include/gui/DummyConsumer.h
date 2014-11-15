/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef ANDROID_GUI_DUMMYCONSUMER_H
#define ANDROID_GUI_DUMMYCONSUMER_H

#include <gui/BufferQueue.h>

namespace android {
// ----------------------------------------------------------------------------


// The DummyConsumer does not keep a reference to BufferQueue
// unlike GLConsumer.  This prevents a circular reference from
// forming without having to use a ProxyConsumerListener
class DummyConsumer : public BufferQueue::ConsumerListener {
public:
    DummyConsumer();
    virtual ~DummyConsumer();
protected:

    // Implementation of the BufferQueue::ConsumerListener interface.  These
    // calls are used to notify the GLConsumer of asynchronous events in the
    // BufferQueue.
    virtual void onFrameAvailable();
    virtual void onBuffersReleased();

};

// ----------------------------------------------------------------------------
}; // namespace android

#endif // ANDROID_GUI_DUMMYCONSUMER_H
