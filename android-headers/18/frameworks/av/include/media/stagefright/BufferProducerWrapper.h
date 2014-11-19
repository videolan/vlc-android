/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef BUFFER_PRODUCER_WRAPPER_H_

#define BUFFER_PRODUCER_WRAPPER_H_

#include <gui/IGraphicBufferProducer.h>

namespace android {

// Can't use static_cast to cast a RefBase back to an IGraphicBufferProducer,
// because IGBP's parent (IInterface) uses virtual inheritance.  This class
// wraps IGBP while we pass it through AMessage.

struct BufferProducerWrapper : RefBase {
    BufferProducerWrapper(
            const sp<IGraphicBufferProducer>& bufferProducer) :
        mBufferProducer(bufferProducer) { }

    sp<IGraphicBufferProducer> getBufferProducer() const {
        return mBufferProducer;
    }

private:
    const sp<IGraphicBufferProducer> mBufferProducer;

    DISALLOW_EVIL_CONSTRUCTORS(BufferProducerWrapper);
};

}  // namespace android

#endif  // BUFFER_PRODUCER_WRAPPER_H_
