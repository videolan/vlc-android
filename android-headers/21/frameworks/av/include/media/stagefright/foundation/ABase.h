/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef A_BASE_H_

#define A_BASE_H_

#define ARRAY_SIZE(a) (sizeof(a) / sizeof(*(a)))

#define DISALLOW_EVIL_CONSTRUCTORS(name) \
    name(const name &); \
    name &operator=(const name &)

/* Returns true if the size parameter is safe for new array allocation (32-bit)
 *
 * Example usage:
 *
 * if (!isSafeArraySize<uint32_t>(arraySize)) {
 *     return BAD_VALUE;
 * }
 * ...
 * uint32_t *myArray = new uint32_t[arraySize];
 *
 * There is a bug in gcc versions earlier than 4.8 where the new[] array allocation
 * will overflow in the internal 32 bit heap allocation, resulting in an
 * underallocated array. This is a security issue that allows potential overwriting
 * of other heap data.
 *
 * An alternative to checking is to create a safe new array template function which
 * either throws a std::bad_alloc exception or returns NULL/nullptr_t; NULL considered
 * safe since normal access of NULL throws an exception.
 *
 * https://securityblog.redhat.com/2012/10/31/array-allocation-in-cxx/
 */
template <typename T, typename S>
bool isSafeArraySize(S size) {
    return size >= 0                            // in case S is signed, ignored if not.
            && size <= 0xffffffff / sizeof(T);  // max-unsigned-32-bit-int / element-size.
}

#endif  // A_BASE_H_
