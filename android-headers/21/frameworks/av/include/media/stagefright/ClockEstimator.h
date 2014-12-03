/*
**
** Copyright 2014, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#ifndef CLOCK_ESTIMATOR_H_

#define CLOCK_ESTIMATOR_H_


#include <utils/RefBase.h>
#include <utils/Vector.h>

namespace android {
// ---------------------------------------------------------------------------

struct ClockEstimator : RefBase {
    virtual double estimate(double x, double y) = 0;
    virtual void reset() = 0;
};

struct WindowedLinearFitEstimator : ClockEstimator {
    struct LinearFit {
        /**
         * Fit y = a * x + b, where each input has a weight
         */
        double mX;  // sum(w_i * x_i)
        double mXX; // sum(w_i * x_i^2)
        double mY;  // sum(w_i * y_i)
        double mYY; // sum(w_i * y_i^2)
        double mXY; // sum(w_i * x_i * y_i)
        double mW;  // sum(w_i)

        LinearFit();
        void reset();
        void combine(const LinearFit &lf);
        void add(double x, double y, double w);
        void scale(double w);
        double interpolate(double x);
        double size() const;

        DISALLOW_EVIL_CONSTRUCTORS(LinearFit);
    };

    /**
     * Estimator for f(x) = y' where input y' is noisy, but
     * theoretically linear:
     *
     *      y' =~ y = a * x + b
     *
     * It uses linear fit regression over a tapering rolling window
     * to get an estimate for y (from the current and past inputs
     * (x, y')).
     *
     *     ____________
     *    /|          |\
     *   / |          | \
     *  /  |          |  \   <--- new data (x, y')
     * /   |   main   |   \
     * <--><----------><-->
     * tail            head
     *
     * weight is 1 under the main window, tapers exponentially by
     * the factors given in the head and the tail.
     *
     * Assuming that x and y' are monotonic, that x is somewhat
     * evenly sampled, and that a =~ 1, the estimated y is also
     * going to be monotonic.
     */
    WindowedLinearFitEstimator(
            size_t headLength = 5, double headFactor = 0.5,
            size_t mainLength = 0, double tailFactor = 0.99);

    virtual void reset();

    // add a new sample (x -> y') and return an estimated value for the true y
    virtual double estimate(double x, double y);

private:
    Vector<double> mXHistory; // circular buffer
    Vector<double> mYHistory; // circular buffer
    LinearFit mHead;
    LinearFit mMain;
    LinearFit mTail;
    double mHeadFactorInv;
    double mTailFactor;
    double mFirstWeight;
    size_t mHistoryLength;
    size_t mHeadLength;
    size_t mNumSamples;
    size_t mSampleIx;

    DISALLOW_EVIL_CONSTRUCTORS(WindowedLinearFitEstimator);
};

}; // namespace android

#endif
