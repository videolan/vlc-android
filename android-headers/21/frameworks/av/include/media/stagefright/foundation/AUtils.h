/*
 * Copyright 2014 The Android Open Source Project
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

#ifndef A_UTILS_H_

#define A_UTILS_H_

/* ============================ math templates ============================ */

/* T must be integer type, den must not be 0 */
template<class T>
inline static const T divRound(const T &nom, const T &den) {
    if ((nom >= 0) ^ (den >= 0)) {
        return (nom - den / 2) / den;
    } else {
        return (nom + den / 2) / den;
    }
}

/* == ceil(nom / den). T must be integer type, den must not be 0 */
template<class T>
inline static const T divUp(const T &nom, const T &den) {
    if (den < 0) {
        return (nom < 0 ? nom + den + 1 : nom) / den;
    } else {
        return (nom < 0 ? nom : nom + den - 1) / den;
    }
}

template<class T>
inline static T abs(const T &a) {
    return a < 0 ? -a : a;
}

template<class T>
inline static const T &min(const T &a, const T &b) {
    return a < b ? a : b;
}

template<class T>
inline static const T &max(const T &a, const T &b) {
    return a > b ? a : b;
}

/* T must be integer type, period must be positive */
template<class T>
inline static T periodicError(const T &val, const T &period) {
    T err = abs(val) % period;
    return (err < (period / 2)) ? err : (period - err);
}

#endif  // A_UTILS_H_
