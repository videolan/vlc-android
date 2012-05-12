/*
 * Copyright (C) 2005 The Android Open Source Project
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

#ifndef ANDROID_GGL_FIXED_H
#define ANDROID_GGL_FIXED_H

#include <math.h>
#include <pixelflinger/pixelflinger.h>

// ----------------------------------------------------------------------------

#define CONST           __attribute__((const))
#define ALWAYS_INLINE   __attribute__((always_inline))

const GGLfixed FIXED_BITS = 16;
const GGLfixed FIXED_EPSILON  = 1;
const GGLfixed FIXED_ONE  = 1L<<FIXED_BITS;
const GGLfixed FIXED_HALF = 1L<<(FIXED_BITS-1);
const GGLfixed FIXED_MIN  = 0x80000000L;
const GGLfixed FIXED_MAX  = 0x7FFFFFFFL;

inline GGLfixed gglIntToFixed(GGLfixed i)       ALWAYS_INLINE ;
inline GGLfixed gglFixedToIntRound(GGLfixed f)  ALWAYS_INLINE ;
inline GGLfixed gglFixedToIntFloor(GGLfixed f)  ALWAYS_INLINE ;
inline GGLfixed gglFixedToIntCeil(GGLfixed f)   ALWAYS_INLINE ;
inline GGLfixed gglFracx(GGLfixed v)            ALWAYS_INLINE ;
inline GGLfixed gglFloorx(GGLfixed v)           ALWAYS_INLINE ;
inline GGLfixed gglCeilx(GGLfixed v)            ALWAYS_INLINE ;
inline GGLfixed gglCenterx(GGLfixed v)          ALWAYS_INLINE ;
inline GGLfixed gglRoundx(GGLfixed v)           ALWAYS_INLINE ;

GGLfixed gglIntToFixed(GGLfixed i) {
    return i<<FIXED_BITS;
}
GGLfixed gglFixedToIntRound(GGLfixed f) {
    return (f + FIXED_HALF)>>FIXED_BITS;
}
GGLfixed gglFixedToIntFloor(GGLfixed f) {
    return f>>FIXED_BITS;
}
GGLfixed gglFixedToIntCeil(GGLfixed f) {
    return (f + ((1<<FIXED_BITS) - 1))>>FIXED_BITS;
}

GGLfixed gglFracx(GGLfixed v) {
    return v & ((1<<FIXED_BITS)-1);
}
GGLfixed gglFloorx(GGLfixed v) {
    return gglFixedToIntFloor(v)<<FIXED_BITS;
}
GGLfixed gglCeilx(GGLfixed v) {
    return gglFixedToIntCeil(v)<<FIXED_BITS;
}
GGLfixed gglCenterx(GGLfixed v) {
    return gglFloorx(v + FIXED_HALF) | FIXED_HALF;
}
GGLfixed gglRoundx(GGLfixed v) {
    return gglFixedToIntRound(v)<<FIXED_BITS;
}

// conversion from (unsigned) int, short, byte to fixed...
#define GGL_B_TO_X(_x)      GGLfixed( ((int32_t(_x)+1)>>1)<<10 )
#define GGL_S_TO_X(_x)      GGLfixed( ((int32_t(_x)+1)>>1)<<2 )
#define GGL_I_TO_X(_x)      GGLfixed( ((int32_t(_x)>>1)+1)>>14 )
#define GGL_UB_TO_X(_x)     GGLfixed(   uint32_t(_x) +      \
                                        (uint32_t(_x)<<8) + \
                                        (uint32_t(_x)>>7) )
#define GGL_US_TO_X(_x)     GGLfixed( (_x) + ((_x)>>15) )
#define GGL_UI_TO_X(_x)     GGLfixed( (((_x)>>1)+1)>>15 )

// ----------------------------------------------------------------------------

GGLfixed gglPowx(GGLfixed x, GGLfixed y) CONST;
GGLfixed gglSqrtx(GGLfixed a) CONST;
GGLfixed gglSqrtRecipx(GGLfixed x) CONST;
GGLfixed gglFastDivx(GGLfixed n, GGLfixed d) CONST;
int32_t gglMulDivi(int32_t a, int32_t b, int32_t c);

int32_t gglRecipQNormalized(int32_t x, int* exponent);
int32_t gglRecipQ(GGLfixed x, int q) CONST;

inline GGLfixed gglRecip(GGLfixed x) CONST;
inline GGLfixed gglRecip(GGLfixed x) {
    return gglRecipQ(x, 16);
}

inline GGLfixed gglRecip28(GGLfixed x) CONST;
int32_t gglRecip28(GGLfixed x) {
    return gglRecipQ(x, 28);
}

// ----------------------------------------------------------------------------

#if defined(__arm__) && !defined(__thumb__)

// inline ARM implementations
inline GGLfixed gglMulx(GGLfixed x, GGLfixed y, int shift) CONST;
inline GGLfixed gglMulx(GGLfixed x, GGLfixed y, int shift) {
    GGLfixed result, t;
    if (__builtin_constant_p(shift)) {
    asm("smull  %[lo], %[hi], %[x], %[y]            \n"
        "movs   %[lo], %[lo], lsr %[rshift]         \n"
        "adc    %[lo], %[lo], %[hi], lsl %[lshift]  \n"
        : [lo]"=r"(result), [hi]"=r"(t), [x]"=r"(x)
        : "%[x]"(x), [y]"r"(y), [lshift] "I"(32-shift), [rshift] "I"(shift)
        : "cc"
        );
    } else {
    asm("smull  %[lo], %[hi], %[x], %[y]            \n"
        "movs   %[lo], %[lo], lsr %[rshift]         \n"
        "adc    %[lo], %[lo], %[hi], lsl %[lshift]  \n"
        : [lo]"=&r"(result), [hi]"=&r"(t), [x]"=&r"(x)
        : "%[x]"(x), [y]"r"(y), [lshift] "r"(32-shift), [rshift] "r"(shift)
        : "cc"
        );
    }
    return result;
}

inline GGLfixed gglMulAddx(GGLfixed x, GGLfixed y, GGLfixed a, int shift) CONST;
inline GGLfixed gglMulAddx(GGLfixed x, GGLfixed y, GGLfixed a, int shift) {
    GGLfixed result, t;
    if (__builtin_constant_p(shift)) {
    asm("smull  %[lo], %[hi], %[x], %[y]            \n"
        "add    %[lo], %[a],  %[lo], lsr %[rshift]  \n"
        "add    %[lo], %[lo], %[hi], lsl %[lshift]  \n"
        : [lo]"=&r"(result), [hi]"=&r"(t), [x]"=&r"(x)
        : "%[x]"(x), [y]"r"(y), [a]"r"(a), [lshift] "I"(32-shift), [rshift] "I"(shift)
        );
    } else {
    asm("smull  %[lo], %[hi], %[x], %[y]            \n"
        "add    %[lo], %[a],  %[lo], lsr %[rshift]  \n"
        "add    %[lo], %[lo], %[hi], lsl %[lshift]  \n"
        : [lo]"=&r"(result), [hi]"=&r"(t), [x]"=&r"(x)
        : "%[x]"(x), [y]"r"(y), [a]"r"(a), [lshift] "r"(32-shift), [rshift] "r"(shift)
        );
    }
    return result;
}

inline GGLfixed gglMulSubx(GGLfixed x, GGLfixed y, GGLfixed a, int shift) CONST;
inline GGLfixed gglMulSubx(GGLfixed x, GGLfixed y, GGLfixed a, int shift) {
    GGLfixed result, t;
    if (__builtin_constant_p(shift)) {
    asm("smull  %[lo], %[hi], %[x], %[y]            \n"
        "rsb    %[lo], %[a],  %[lo], lsr %[rshift]  \n"
        "add    %[lo], %[lo], %[hi], lsl %[lshift]  \n"
        : [lo]"=&r"(result), [hi]"=&r"(t), [x]"=&r"(x)
        : "%[x]"(x), [y]"r"(y), [a]"r"(a), [lshift] "I"(32-shift), [rshift] "I"(shift)
        );
    } else {
    asm("smull  %[lo], %[hi], %[x], %[y]            \n"
        "rsb    %[lo], %[a],  %[lo], lsr %[rshift]  \n"
        "add    %[lo], %[lo], %[hi], lsl %[lshift]  \n"
        : [lo]"=&r"(result), [hi]"=&r"(t), [x]"=&r"(x)
        : "%[x]"(x), [y]"r"(y), [a]"r"(a), [lshift] "r"(32-shift), [rshift] "r"(shift)
        );
    }
    return result;
}

inline int64_t gglMulii(int32_t x, int32_t y) CONST;
inline int64_t gglMulii(int32_t x, int32_t y)
{
    // 64-bits result: r0=low, r1=high
    union {
        struct {
            int32_t lo;
            int32_t hi;
        } s;
        int64_t res;
    };
    asm("smull %0, %1, %2, %3   \n"
        : "=r"(s.lo), "=&r"(s.hi)
        : "%r"(x), "r"(y)
        :
        );
    return res;
}

#else // ----------------------------------------------------------------------

inline GGLfixed gglMulx(GGLfixed a, GGLfixed b, int shift) CONST;
inline GGLfixed gglMulx(GGLfixed a, GGLfixed b, int shift) {
    return GGLfixed((int64_t(a)*b + (1<<(shift-1)))>>shift);
}
inline GGLfixed gglMulAddx(GGLfixed a, GGLfixed b, GGLfixed c, int shift) CONST;
inline GGLfixed gglMulAddx(GGLfixed a, GGLfixed b, GGLfixed c, int shift) {
    return GGLfixed((int64_t(a)*b)>>shift) + c;
}
inline GGLfixed gglMulSubx(GGLfixed a, GGLfixed b, GGLfixed c, int shift) CONST;
inline GGLfixed gglMulSubx(GGLfixed a, GGLfixed b, GGLfixed c, int shift) {
    return GGLfixed((int64_t(a)*b)>>shift) - c;
}
inline int64_t gglMulii(int32_t a, int32_t b) CONST;
inline int64_t gglMulii(int32_t a, int32_t b) {
    return int64_t(a)*b;
}

#endif

// ------------------------------------------------------------------------

inline GGLfixed gglMulx(GGLfixed a, GGLfixed b) CONST;
inline GGLfixed gglMulx(GGLfixed a, GGLfixed b) {
    return gglMulx(a, b, 16);
}
inline GGLfixed gglMulAddx(GGLfixed a, GGLfixed b, GGLfixed c) CONST;
inline GGLfixed gglMulAddx(GGLfixed a, GGLfixed b, GGLfixed c) {
    return gglMulAddx(a, b, c, 16);
}
inline GGLfixed gglMulSubx(GGLfixed a, GGLfixed b, GGLfixed c) CONST;
inline GGLfixed gglMulSubx(GGLfixed a, GGLfixed b, GGLfixed c) {
    return gglMulSubx(a, b, c, 16);
}

// ------------------------------------------------------------------------

inline int32_t gglClz(int32_t x) CONST;
inline int32_t gglClz(int32_t x)
{
#if defined(__arm__) && !defined(__thumb__)
    return __builtin_clz(x);
#else
    if (!x) return 32;
    int32_t exp = 31;
    if (x & 0xFFFF0000) { exp -=16; x >>= 16; }
    if (x & 0x0000ff00) { exp -= 8; x >>= 8; }
    if (x & 0x000000f0) { exp -= 4; x >>= 4; }
    if (x & 0x0000000c) { exp -= 2; x >>= 2; }
    if (x & 0x00000002) { exp -= 1; }
    return exp;
#endif
}

// ------------------------------------------------------------------------

int32_t gglDivQ(GGLfixed n, GGLfixed d, int32_t i) CONST;

inline int32_t gglDivQ16(GGLfixed n, GGLfixed d) CONST;
inline int32_t gglDivQ16(GGLfixed n, GGLfixed d) {
    return gglDivQ(n, d, 16);
}

inline int32_t gglDivx(GGLfixed n, GGLfixed d) CONST;
inline int32_t gglDivx(GGLfixed n, GGLfixed d) {
    return gglDivQ(n, d, 16);
}

// ------------------------------------------------------------------------

inline GGLfixed gglRecipFast(GGLfixed x) CONST;
inline GGLfixed gglRecipFast(GGLfixed x)
{
    // This is a really bad approximation of 1/x, but it's also
    // very fast. x must be strictly positive.
    // if x between [0.5, 1[ , then 1/x = 3-2*x
    // (we use 2.30 fixed-point)
    const int32_t lz = gglClz(x);
    return (0xC0000000 - (x << (lz - 1))) >> (30-lz);
}

// ------------------------------------------------------------------------

inline GGLfixed gglClampx(GGLfixed c) CONST;
inline GGLfixed gglClampx(GGLfixed c)
{
#if defined(__thumb__)
    // clamp without branches
    c &= ~(c>>31);  c = FIXED_ONE - c;
    c &= ~(c>>31);  c = FIXED_ONE - c;
#else
#if defined(__arm__)
    // I don't know why gcc thinks its smarter than me! The code below
    // clamps to zero in one instruction, but gcc won't generate it and
    // replace it by a cmp + movlt (it's quite amazing actually).
    asm("bic %0, %1, %1, asr #31\n" : "=r"(c) : "r"(c));
#else
    c &= ~(c>>31);
#endif
    if (c>FIXED_ONE)
        c = FIXED_ONE;
#endif
    return c;
}

// ------------------------------------------------------------------------

#endif // ANDROID_GGL_FIXED_H
