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

#ifndef ANDROID_AUDIO_NBAIO_H
#define ANDROID_AUDIO_NBAIO_H

// Non-blocking audio I/O interface
//
// This header file has the abstract interfaces only.  Concrete implementation classes are declared
// elsewhere.  Implementations _should_ be non-blocking for all methods, especially read() and
// write(), but this is not enforced.  In general, implementations do not need to be multi-thread
// safe, and any exceptions are noted in the particular implementation.

#include <limits.h>
#include <stdlib.h>
#include <utils/Errors.h>
#include <utils/RefBase.h>
#include <media/AudioTimestamp.h>
#include <system/audio.h>

namespace android {

// In addition to the usual status_t
enum {
    NEGOTIATE    = 0x80000010,  // Must (re-)negotiate format.  For negotiate() only, the offeree
                                // doesn't accept offers, and proposes counter-offers
    OVERRUN      = 0x80000011,  // availableToRead(), read(), or readVia() detected lost input due
                                // to overrun; an event is counted and the caller should re-try
    UNDERRUN     = 0x80000012,  // availableToWrite(), write(), or writeVia() detected a gap in
                                // output due to underrun (not being called often enough, or with
                                // enough data); an event is counted and the caller should re-try
};

// Negotiation of format is based on the data provider and data sink, or the data consumer and
// data source, exchanging prioritized arrays of offers and counter-offers until a single offer is
// mutually agreed upon.  Each offer is an NBAIO_Format.  For simplicity and performance,
// NBAIO_Format is a typedef that ties together the most important combinations of the various
// attributes, rather than a struct with separate fields for format, sample rate, channel count,
// interleave, packing, alignment, etc.  The reason is that NBAIO_Format tries to abstract out only
// the combinations that are actually needed within AudioFlinger.  If the list of combinations grows
// too large, then this decision should be re-visited.
// Sample rate and channel count are explicit, PCM interleaved 16-bit is assumed.
struct NBAIO_Format {
// FIXME make this a class, and change Format_... global methods to class methods
//private:
    unsigned    mSampleRate;
    unsigned    mChannelCount;
    audio_format_t  mFormat;
    size_t      mFrameSize;
};

extern const NBAIO_Format Format_Invalid;

// Return the frame size of an NBAIO_Format in bytes
size_t Format_frameSize(const NBAIO_Format& format);

// Convert a sample rate in Hz and channel count to an NBAIO_Format
// FIXME rename
NBAIO_Format Format_from_SR_C(unsigned sampleRate, unsigned channelCount, audio_format_t format);

// Return the sample rate in Hz of an NBAIO_Format
unsigned Format_sampleRate(const NBAIO_Format& format);

// Return the channel count of an NBAIO_Format
unsigned Format_channelCount(const NBAIO_Format& format);

// Callbacks used by NBAIO_Sink::writeVia() and NBAIO_Source::readVia() below.
typedef ssize_t (*writeVia_t)(void *user, void *buffer, size_t count);
typedef ssize_t (*readVia_t)(void *user, const void *buffer,
                             size_t count, int64_t readPTS);

// Check whether an NBAIO_Format is valid
bool Format_isValid(const NBAIO_Format& format);

// Compare two NBAIO_Format values
bool Format_isEqual(const NBAIO_Format& format1, const NBAIO_Format& format2);

// Abstract class (interface) representing a data port.
class NBAIO_Port : public RefBase {

public:

    // negotiate() must called first.  The purpose of negotiate() is to check compatibility of
    // formats, not to automatically adapt if they are incompatible.  It's the responsibility of
    // whoever sets up the graph connections to make sure formats are compatible, and this method
    // just verifies that.  The edges are "dumb" and don't attempt to adapt to bad connections.
    // How it works: offerer proposes an array of formats, in descending order of preference from
    // offers[0] to offers[numOffers - 1].  If offeree accepts one of these formats, it returns
    // the index of that offer.  Otherwise, offeree sets numCounterOffers to the number of
    // counter-offers (up to a maximumum of the entry value of numCounterOffers), fills in the
    // provided array counterOffers[] with its counter-offers, in descending order of preference
    // from counterOffers[0] to counterOffers[numCounterOffers - 1], and returns NEGOTIATE.
    // Note that since the offerer allocates space for counter-offers, but only the offeree knows
    // how many counter-offers it has, there may be insufficient space for all counter-offers.
    // In that case, the offeree sets numCounterOffers to the requested number of counter-offers
    // (which is greater than the entry value of numCounterOffers), fills in as many of the most
    // important counterOffers as will fit, and returns NEGOTIATE.  As this implies a re-allocation,
    // it should be used as a last resort.  It is preferable for the offerer to simply allocate a
    // larger space to begin with, and/or for the offeree to tolerate a smaller space than desired.
    // Alternatively, the offerer can pass NULL for offers and counterOffers, and zero for
    // numOffers. This indicates that it has not allocated space for any counter-offers yet.
    // In this case, the offerree should set numCounterOffers appropriately and return NEGOTIATE.
    // Then the offerer will allocate the correct amount of memory and retry.
    // Format_Invalid is not allowed as either an offer or counter-offer.
    // Returns:
    //  >= 0        Offer accepted.
    //  NEGOTIATE   No offer accepted, and counter-offer(s) optionally made. See above for details.
    virtual ssize_t negotiate(const NBAIO_Format offers[], size_t numOffers,
                              NBAIO_Format counterOffers[], size_t& numCounterOffers);

    // Return the current negotiated format, or Format_Invalid if negotiation has not been done,
    // or if re-negotiation is required.
    virtual NBAIO_Format format() const { return mNegotiated ? mFormat : Format_Invalid; }

protected:
    NBAIO_Port(const NBAIO_Format& format) : mNegotiated(false), mFormat(format),
                                             mFrameSize(Format_frameSize(format)) { }
    virtual ~NBAIO_Port() { }

    // Implementations are free to ignore these if they don't need them

    bool            mNegotiated;    // mNegotiated implies (mFormat != Format_Invalid)
    NBAIO_Format    mFormat;        // (mFormat != Format_Invalid) does not imply mNegotiated
    size_t          mFrameSize;     // assign in parallel with any assignment to mFormat
};

// Abstract class (interface) representing a non-blocking data sink, for use by a data provider.
class NBAIO_Sink : public NBAIO_Port {

public:

    // For the next two APIs:
    // 32 bits rolls over after 27 hours at 44.1 kHz; if that concerns you then poll periodically.

    // Return the number of frames written successfully since construction.
    virtual size_t framesWritten() const { return mFramesWritten; }

    // Number of frames lost due to underrun since construction.
    virtual size_t framesUnderrun() const { return 0; }

    // Number of underruns since construction, where a set of contiguous lost frames is one event.
    virtual size_t underruns() const { return 0; }

    // Estimate of number of frames that could be written successfully now without blocking.
    // When a write() is actually attempted, the implementation is permitted to return a smaller or
    // larger transfer count, however it will make a good faith effort to give an accurate estimate.
    // Errors:
    //  NEGOTIATE   (Re-)negotiation is needed.
    //  UNDERRUN    write() has not been called frequently enough, or with enough frames to keep up.
    //              An underrun event is counted, and the caller should re-try this operation.
    //  WOULD_BLOCK Determining how many frames can be written without blocking would itself block.
    virtual ssize_t availableToWrite() const { return SSIZE_MAX; }

    // Transfer data to sink from single input buffer.  Implies a copy.
    // Inputs:
    //  buffer  Non-NULL buffer owned by provider.
    //  count   Maximum number of frames to transfer.
    // Return value:
    //  > 0     Number of frames successfully transferred prior to first error.
    //  = 0     Count was zero.
    //  < 0     status_t error occurred prior to the first frame transfer.
    // Errors:
    //  NEGOTIATE   (Re-)negotiation is needed.
    //  WOULD_BLOCK No frames can be transferred without blocking.
    //  UNDERRUN    write() has not been called frequently enough, or with enough frames to keep up.
    //              An underrun event is counted, and the caller should re-try this operation.
    virtual ssize_t write(const void *buffer, size_t count) = 0;

    // Transfer data to sink using a series of callbacks.  More suitable for zero-fill, synthesis,
    // and non-contiguous transfers (e.g. circular buffer or writev).
    // Inputs:
    //  via     Callback function that the sink will call as many times as needed to consume data.
    //  total   Estimate of the number of frames the provider has available.  This is an estimate,
    //          and it can provide a different number of frames during the series of callbacks.
    //  user    Arbitrary void * reserved for data provider.
    //  block   Number of frames per block, that is a suggested value for 'count' in each callback.
    //          Zero means no preference.  This parameter is a hint only, and may be ignored.
    // Return value:
    //  > 0     Total number of frames successfully transferred prior to first error.
    //  = 0     Count was zero.
    //  < 0     status_t error occurred prior to the first frame transfer.
    // Errors:
    //  NEGOTIATE   (Re-)negotiation is needed.
    //  WOULD_BLOCK No frames can be transferred without blocking.
    //  UNDERRUN    write() has not been called frequently enough, or with enough frames to keep up.
    //              An underrun event is counted, and the caller should re-try this operation.
    //
    // The 'via' callback is called by the data sink as follows:
    // Inputs:
    //  user    Arbitrary void * reserved for data provider.
    //  buffer  Non-NULL buffer owned by sink that callback should fill in with data,
    //          up to a maximum of 'count' frames.
    //  count   Maximum number of frames to transfer during this callback.
    // Return value:
    //  > 0     Number of frames successfully transferred during this callback prior to first error.
    //  = 0     Count was zero.
    //  < 0     status_t error occurred prior to the first frame transfer during this callback.
    virtual ssize_t writeVia(writeVia_t via, size_t total, void *user, size_t block = 0);

    // Get the time (on the LocalTime timeline) at which the first frame of audio of the next write
    // operation to this sink will be eventually rendered by the HAL.
    // Inputs:
    //  ts      A pointer pointing to the int64_t which will hold the result.
    // Return value:
    //  OK      Everything went well, *ts holds the time at which the first audio frame of the next
    //          write operation will be rendered, or AudioBufferProvider::kInvalidPTS if this sink
    //          does not know the answer for some reason.  Sinks which eventually lead to a HAL
    //          which implements get_next_write_timestamp may return Invalid temporarily if the DMA
    //          output of the audio driver has not started yet.  Sinks which lead to a HAL which
    //          does not implement get_next_write_timestamp, or which don't lead to a HAL at all,
    //          will always return kInvalidPTS.
    //  <other> Something unexpected happened internally.  Check the logs and start debugging.
    virtual status_t getNextWriteTimestamp(int64_t *ts) { return INVALID_OPERATION; }

    // Returns NO_ERROR if a timestamp is available.  The timestamp includes the total number
    // of frames presented to an external observer, together with the value of CLOCK_MONOTONIC
    // as of this presentation count.  The timestamp parameter is undefined if error is returned.
    virtual status_t getTimestamp(AudioTimestamp& timestamp) { return INVALID_OPERATION; }

protected:
    NBAIO_Sink(const NBAIO_Format& format = Format_Invalid) : NBAIO_Port(format), mFramesWritten(0) { }
    virtual ~NBAIO_Sink() { }

    // Implementations are free to ignore these if they don't need them
    size_t  mFramesWritten;
};

// Abstract class (interface) representing a non-blocking data source, for use by a data consumer.
class NBAIO_Source : public NBAIO_Port {

public:

    // For the next two APIs:
    // 32 bits rolls over after 27 hours at 44.1 kHz; if that concerns you then poll periodically.

    // Number of frames read successfully since construction.
    virtual size_t framesRead() const { return mFramesRead; }

    // Number of frames lost due to overrun since construction.
    // Not const because implementations may need to do I/O.
    virtual size_t framesOverrun() /*const*/ { return 0; }

    // Number of overruns since construction, where a set of contiguous lost frames is one event.
    // Not const because implementations may need to do I/O.
    virtual size_t overruns() /*const*/ { return 0; }

    // Estimate of number of frames that could be read successfully now.
    // When a read() is actually attempted, the implementation is permitted to return a smaller or
    // larger transfer count, however it will make a good faith effort to give an accurate estimate.
    // Errors:
    //  NEGOTIATE   (Re-)negotiation is needed.
    //  OVERRUN     One or more frames were lost due to overrun, try again to read more recent data.
    //  WOULD_BLOCK Determining how many frames can be read without blocking would itself block.
    virtual ssize_t availableToRead() { return SSIZE_MAX; }

    // Transfer data from source into single destination buffer.  Implies a copy.
    // Inputs:
    //  buffer  Non-NULL destination buffer owned by consumer.
    //  count   Maximum number of frames to transfer.
    //  readPTS The presentation time (on the LocalTime timeline) for which data
    //          is being requested, or kInvalidPTS if not known.
    // Return value:
    //  > 0     Number of frames successfully transferred prior to first error.
    //  = 0     Count was zero.
    //  < 0     status_t error occurred prior to the first frame transfer.
    // Errors:
    //  NEGOTIATE   (Re-)negotiation is needed.
    //  WOULD_BLOCK No frames can be transferred without blocking.
    //  OVERRUN     read() has not been called frequently enough, or with enough frames to keep up.
    //              One or more frames were lost due to overrun, try again to read more recent data.
    virtual ssize_t read(void *buffer, size_t count, int64_t readPTS) = 0;

    // Transfer data from source using a series of callbacks.  More suitable for zero-fill,
    // synthesis, and non-contiguous transfers (e.g. circular buffer or readv).
    // Inputs:
    //  via     Callback function that the source will call as many times as needed to provide data.
    //  total   Estimate of the number of frames the consumer desires.  This is an estimate,
    //          and it can consume a different number of frames during the series of callbacks.
    //  user    Arbitrary void * reserved for data consumer.
    //  readPTS The presentation time (on the LocalTime timeline) for which data
    //          is being requested, or kInvalidPTS if not known.
    //  block   Number of frames per block, that is a suggested value for 'count' in each callback.
    //          Zero means no preference.  This parameter is a hint only, and may be ignored.
    // Return value:
    //  > 0     Total number of frames successfully transferred prior to first error.
    //  = 0     Count was zero.
    //  < 0     status_t error occurred prior to the first frame transfer.
    // Errors:
    //  NEGOTIATE   (Re-)negotiation is needed.
    //  WOULD_BLOCK No frames can be transferred without blocking.
    //  OVERRUN     read() has not been called frequently enough, or with enough frames to keep up.
    //              One or more frames were lost due to overrun, try again to read more recent data.
    //
    // The 'via' callback is called by the data source as follows:
    // Inputs:
    //  user    Arbitrary void * reserved for data consumer.
    //  dest    Non-NULL buffer owned by source that callback should consume data from,
    //          up to a maximum of 'count' frames.
    //  count   Maximum number of frames to transfer during this callback.
    // Return value:
    //  > 0     Number of frames successfully transferred during this callback prior to first error.
    //  = 0     Count was zero.
    //  < 0     status_t error occurred prior to the first frame transfer during this callback.
    virtual ssize_t readVia(readVia_t via, size_t total, void *user,
                            int64_t readPTS, size_t block = 0);

    // Invoked asynchronously by corresponding sink when a new timestamp is available.
    // Default implementation ignores the timestamp.
    virtual void    onTimestamp(const AudioTimestamp& timestamp) { }

protected:
    NBAIO_Source(const NBAIO_Format& format = Format_Invalid) : NBAIO_Port(format), mFramesRead(0) { }
    virtual ~NBAIO_Source() { }

    // Implementations are free to ignore these if they don't need them
    size_t  mFramesRead;
};

}   // namespace android

#endif  // ANDROID_AUDIO_NBAIO_H
