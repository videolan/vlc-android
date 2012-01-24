/*
 * Copyright (C) 2006 The Android Open Source Project
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

#ifndef ANDROID_GGL_CONTEXT_H
#define ANDROID_GGL_CONTEXT_H

#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <sys/types.h>
#include <endian.h>

#include <pixelflinger/pixelflinger.h>
#include <private/pixelflinger/ggl_fixed.h>

namespace android {

// ----------------------------------------------------------------------------

#if BYTE_ORDER == LITTLE_ENDIAN

inline uint32_t GGL_RGBA_TO_HOST(uint32_t v) {
    return v;
}
inline uint32_t GGL_HOST_TO_RGBA(uint32_t v) {
    return v;
}

#else

inline uint32_t GGL_RGBA_TO_HOST(uint32_t v) {
    return (v<<24) | (v>>24) | ((v<<8)&0xff0000) | ((v>>8)&0xff00);
}
inline uint32_t GGL_HOST_TO_RGBA(uint32_t v) {
    return (v<<24) | (v>>24) | ((v<<8)&0xff0000) | ((v>>8)&0xff00);
}

#endif

// ----------------------------------------------------------------------------

const int GGL_DITHER_BITS = 6;  // dither weights stored on 6 bits
const int GGL_DITHER_ORDER_SHIFT= 3;
const int GGL_DITHER_ORDER      = (1<<GGL_DITHER_ORDER_SHIFT);
const int GGL_DITHER_SIZE       = GGL_DITHER_ORDER * GGL_DITHER_ORDER;
const int GGL_DITHER_MASK       = GGL_DITHER_ORDER-1;

// ----------------------------------------------------------------------------

const int GGL_SUBPIXEL_BITS = 4;

// TRI_FRACTION_BITS defines the number of bits we want to use
// for the sub-pixel coordinates during the edge stepping, the
// value shouldn't be more than 7, or bad things are going to
// happen when drawing large triangles (8 doesn't work because
// 32 bit muls will loose the sign bit)

#define  TRI_FRACTION_BITS  (GGL_SUBPIXEL_BITS)
#define  TRI_ONE            (1 << TRI_FRACTION_BITS)
#define  TRI_HALF           (1 << (TRI_FRACTION_BITS-1))
#define  TRI_FROM_INT(x)    ((x) << TRI_FRACTION_BITS)
#define  TRI_FRAC(x)        ((x)                 &  (TRI_ONE-1))
#define  TRI_FLOOR(x)       ((x)                 & ~(TRI_ONE-1))
#define  TRI_CEIL(x)        (((x) + (TRI_ONE-1)) & ~(TRI_ONE-1))
#define  TRI_ROUND(x)       (((x) +  TRI_HALF  ) & ~(TRI_ONE-1))

#define  TRI_ROUDNING       (1 << (16 - TRI_FRACTION_BITS - 1))
#define  TRI_FROM_FIXED(x)  (((x)+TRI_ROUDNING) >> (16-TRI_FRACTION_BITS))

#define  TRI_SNAP_NEXT_HALF(x)   (TRI_CEIL((x)+TRI_HALF) - TRI_HALF)
#define  TRI_SNAP_PREV_HALF(x)   (TRI_CEIL((x)-TRI_HALF) - TRI_HALF)

// ----------------------------------------------------------------------------

const int GGL_COLOR_BITS = 24;

// To maintain 8-bits color chanels, with a maximum GGLSurface
// size of 4096 and GGL_SUBPIXEL_BITS=4, we need 8 + 12 + 4 = 24 bits
// for encoding the color iterators

inline GGLcolor gglFixedToIteratedColor(GGLfixed c) {
    return (c << 8) - c;
}

// ----------------------------------------------------------------------------

template<bool> struct CTA;
template<> struct CTA<true> { };

#define GGL_CONTEXT(con, c)         context_t *con = static_cast<context_t *>(c)
#define GGL_OFFSETOF(field)         int(&(((context_t*)0)->field))
#define GGL_INIT_PROC(p, f)         p.f = ggl_ ## f;
#define GGL_BETWEEN(x, L, H)        (uint32_t((x)-(L)) <= ((H)-(L)))

#define ggl_likely(x)	__builtin_expect(!!(x), 1)
#define ggl_unlikely(x)	__builtin_expect(!!(x), 0)

const int GGL_TEXTURE_UNIT_COUNT    = 2;
const int GGL_TMU_STATE             = 0x00000001;
const int GGL_CB_STATE              = 0x00000002;
const int GGL_PIXEL_PIPELINE_STATE  = 0x00000004;

// ----------------------------------------------------------------------------

#define GGL_RESERVE_NEEDS(name, l, s)                               \
    const uint32_t  GGL_NEEDS_##name##_MASK = (((1LU<<(s))-1)<<l);  \
    const uint32_t  GGL_NEEDS_##name##_SHIFT = (l);

#define GGL_BUILD_NEEDS(val, name)                                  \
    (((val)<<(GGL_NEEDS_##name##_SHIFT)) & GGL_NEEDS_##name##_MASK)

#define GGL_READ_NEEDS(name, n)                                     \
    (uint32_t(n & GGL_NEEDS_##name##_MASK) >> GGL_NEEDS_##name##_SHIFT)

#define GGL_NEED_MASK(name)     (uint32_t(GGL_NEEDS_##name##_MASK))
#define GGL_NEED(name, val)     GGL_BUILD_NEEDS(val, name)

GGL_RESERVE_NEEDS( CB_FORMAT,       0, 6 )
GGL_RESERVE_NEEDS( SHADE,           6, 1 )
GGL_RESERVE_NEEDS( W,               7, 1 )
GGL_RESERVE_NEEDS( BLEND_SRC,       8, 4 )
GGL_RESERVE_NEEDS( BLEND_DST,      12, 4 )
GGL_RESERVE_NEEDS( BLEND_SRCA,     16, 4 )
GGL_RESERVE_NEEDS( BLEND_DSTA,     20, 4 )
GGL_RESERVE_NEEDS( LOGIC_OP,       24, 4 )
GGL_RESERVE_NEEDS( MASK_ARGB,      28, 4 )

GGL_RESERVE_NEEDS( P_ALPHA_TEST,    0, 3 )
GGL_RESERVE_NEEDS( P_AA,            3, 1 )
GGL_RESERVE_NEEDS( P_DEPTH_TEST,    4, 3 )
GGL_RESERVE_NEEDS( P_MASK_Z,        7, 1 )
GGL_RESERVE_NEEDS( P_DITHER,        8, 1 )
GGL_RESERVE_NEEDS( P_FOG,           9, 1 )
GGL_RESERVE_NEEDS( P_RESERVED1,    10,22 )

GGL_RESERVE_NEEDS( T_FORMAT,        0, 6 )
GGL_RESERVE_NEEDS( T_RESERVED0,     6, 1 )
GGL_RESERVE_NEEDS( T_POT,           7, 1 )
GGL_RESERVE_NEEDS( T_S_WRAP,        8, 2 )
GGL_RESERVE_NEEDS( T_T_WRAP,       10, 2 )
GGL_RESERVE_NEEDS( T_ENV,          12, 3 )
GGL_RESERVE_NEEDS( T_LINEAR,       15, 1 )

const int GGL_NEEDS_WRAP_CLAMP_TO_EDGE  = 0;
const int GGL_NEEDS_WRAP_REPEAT         = 1;
const int GGL_NEEDS_WRAP_11             = 2;

inline uint32_t ggl_wrap_to_needs(uint32_t e) {
    switch (e) {
    case GGL_CLAMP:         return GGL_NEEDS_WRAP_CLAMP_TO_EDGE;
    case GGL_REPEAT:        return GGL_NEEDS_WRAP_REPEAT;
    }
    return 0;
}

inline uint32_t ggl_blendfactor_to_needs(uint32_t b) {
    if (b <= 1) return b;
    return (b & 0xF)+2;
}

inline uint32_t ggl_needs_to_blendfactor(uint32_t n) {
    if (n <= 1) return n;
    return (n - 2) + 0x300;
}

inline uint32_t ggl_env_to_needs(uint32_t e) {
    switch (e) {
    case GGL_REPLACE:   return 0;
    case GGL_MODULATE:  return 1;
    case GGL_DECAL:     return 2;
    case GGL_BLEND:     return 3;
    case GGL_ADD:       return 4;
    }
    return 0;
}

inline uint32_t ggl_needs_to_env(uint32_t n) {
    const uint32_t envs[] = { GGL_REPLACE, GGL_MODULATE, 
            GGL_DECAL, GGL_BLEND, GGL_ADD };
    return envs[n];

}

// ----------------------------------------------------------------------------

enum {
    GGL_ENABLE_BLENDING     = 0x00000001,
    GGL_ENABLE_SMOOTH       = 0x00000002,
    GGL_ENABLE_AA           = 0x00000004,
    GGL_ENABLE_LOGIC_OP     = 0x00000008,
    GGL_ENABLE_ALPHA_TEST   = 0x00000010,
    GGL_ENABLE_SCISSOR_TEST = 0x00000020,
    GGL_ENABLE_TMUS         = 0x00000040,
    GGL_ENABLE_DEPTH_TEST   = 0x00000080,
    GGL_ENABLE_STENCIL_TEST = 0x00000100,
    GGL_ENABLE_W            = 0x00000200,
    GGL_ENABLE_DITHER       = 0x00000400,
    GGL_ENABLE_FOG          = 0x00000800,
    GGL_ENABLE_POINT_AA_NICE= 0x00001000
};

// ----------------------------------------------------------------------------

class needs_filter_t;
struct needs_t {
    inline int match(const needs_filter_t& filter);
    inline bool operator == (const needs_t& rhs) const {
        return  (n==rhs.n) &&
                (p==rhs.p) &&
                (t[0]==rhs.t[0]) &&
                (t[1]==rhs.t[1]);
    }
    inline bool operator != (const needs_t& rhs) const {
        return !operator == (rhs);
    }
    uint32_t    n;
    uint32_t    p;
    uint32_t    t[GGL_TEXTURE_UNIT_COUNT];
};

inline int compare_type(const needs_t& lhs, const needs_t& rhs) {
    return memcmp(&lhs, &rhs, sizeof(needs_t));
}

struct needs_filter_t {
    needs_t     value;
    needs_t     mask;
};

int needs_t::match(const needs_filter_t& filter) {
    uint32_t result = 
        ((filter.value.n ^ n)       & filter.mask.n)    |
        ((filter.value.p ^ p)       & filter.mask.p)    |
        ((filter.value.t[0] ^ t[0]) & filter.mask.t[0]) |
        ((filter.value.t[1] ^ t[1]) & filter.mask.t[1]);
    return (result == 0);
}

// ----------------------------------------------------------------------------

struct context_t;
class Assembly;

struct blend_state_t {
	uint32_t			src;
	uint32_t			dst;
	uint32_t			src_alpha;
	uint32_t			dst_alpha;
	uint8_t				reserved;
	uint8_t				alpha_separate;
	uint8_t				operation;
	uint8_t				equation;
};

struct mask_state_t {
    uint8_t             color;
    uint8_t             depth;
    uint32_t            stencil;
};

struct clear_state_t {
    GGLclampx           r;
    GGLclampx           g;
    GGLclampx           b;
    GGLclampx           a;
    GGLclampx           depth;
    GGLint              stencil;
    uint32_t            colorPacked;
    uint32_t            depthPacked;
    uint32_t            stencilPacked;
    uint32_t            dirty;
};

struct fog_state_t {
    uint8_t     color[4];
};

struct logic_op_state_t {
    uint16_t            opcode;
};

struct alpha_test_state_t {
    uint16_t            func;
    GGLcolor            ref;
};

struct depth_test_state_t {
    uint16_t            func;
    GGLclampx           clearValue;
};

struct scissor_t {
    uint32_t            user_left;
    uint32_t            user_right;
    uint32_t            user_top;
    uint32_t            user_bottom;
    uint32_t            left;
    uint32_t            right;
    uint32_t            top;
    uint32_t            bottom;
};

struct pixel_t {
    uint32_t    c[4];
    uint8_t     s[4];
};

struct surface_t {
    union {
        GGLSurface      s;
        struct {
        uint32_t            reserved;
        uint32_t			width;
        uint32_t			height;
        int32_t             stride;
        uint8_t*			data;	
        uint8_t				format;
        uint8_t				dirty;
        uint8_t				pad[2];
        };
    };
    void                (*read) (const surface_t* s, context_t* c,
                                uint32_t x, uint32_t y, pixel_t* pixel);
    void                (*write)(const surface_t* s, context_t* c,
                                uint32_t x, uint32_t y, const pixel_t* pixel);
};

// ----------------------------------------------------------------------------

struct texture_shade_t {
    union {
        struct {
            int32_t             is0;
            int32_t             idsdx;
            int32_t             idsdy;
            int                 sscale;
            int32_t             it0;
            int32_t             idtdx;
            int32_t             idtdy;
            int                 tscale;
        };
        struct {
            int32_t             v;
            int32_t             dx;
            int32_t             dy;
            int                 scale;
        } st[2];
    };
};

struct texture_iterators_t {
    // these are not encoded in the same way than in the
    // texture_shade_t structure
    union {
        struct {
            GGLfixed			ydsdy;
            GGLfixed            dsdx;
            GGLfixed            dsdy;
            int                 sscale;
            GGLfixed			ydtdy;
            GGLfixed            dtdx;
            GGLfixed            dtdy;
            int                 tscale;
        };
        struct {
            GGLfixed			ydvdy;
            GGLfixed            dvdx;
            GGLfixed            dvdy;
            int                 scale;
        } st[2];
    };
};

struct texture_t {
	surface_t			surface;
	texture_iterators_t	iterators;
    texture_shade_t     shade;
	uint32_t			s_coord;
	uint32_t            t_coord;
	uint16_t			s_wrap;
	uint16_t            t_wrap;
	uint16_t            min_filter;
	uint16_t            mag_filter;
    uint16_t            env;
    uint8_t             env_color[4];
	uint8_t				enable;
	uint8_t				dirty;
};

struct raster_t {
    GGLfixed            x;
    GGLfixed            y;
};

struct framebuffer_t {
    surface_t           color;
    surface_t           read;
	surface_t			depth;
	surface_t			stencil;
    int16_t             *coverage;
    size_t              coverageBufferSize;
};

// ----------------------------------------------------------------------------

struct iterators_t {
	int32_t             xl;
	int32_t             xr;
    int32_t             y;
	GGLcolor			ydady;
	GGLcolor			ydrdy;
	GGLcolor			ydgdy;
	GGLcolor			ydbdy;
	GGLfixed			ydzdy;
	GGLfixed			ydwdy;
	GGLfixed			ydfdy;
};

struct shade_t {
	GGLcolor			a0;
    GGLcolor            dadx;
    GGLcolor            dady;
	GGLcolor			r0;
    GGLcolor            drdx;
    GGLcolor            drdy;
	GGLcolor			g0;
    GGLcolor            dgdx;
    GGLcolor            dgdy;
	GGLcolor			b0;
    GGLcolor            dbdx;
    GGLcolor            dbdy;
	uint32_t            z0;
    GGLfixed32          dzdx;
    GGLfixed32          dzdy;
	GGLfixed            w0;
    GGLfixed            dwdx;
    GGLfixed            dwdy;
	uint32_t			f0;
    GGLfixed            dfdx;
    GGLfixed            dfdy;
};

// these are used in the generated code
// we use this mirror structure to improve
// data locality in the pixel pipeline
struct generated_tex_vars_t {
    uint32_t    width;
    uint32_t    height;
    uint32_t    stride;
    int32_t     data;
    int32_t     dsdx;
    int32_t     dtdx;
    int32_t     spill[2];
};

struct generated_vars_t {
    struct {
        int32_t c;
        int32_t dx;
    } argb[4];
    int32_t     aref;
    int32_t     dzdx;
    int32_t     zbase;
    int32_t     f;
    int32_t     dfdx;
    int32_t     spill[3];
    generated_tex_vars_t    texture[GGL_TEXTURE_UNIT_COUNT];
    int32_t     rt;
    int32_t     lb;
};

// ----------------------------------------------------------------------------

struct state_t {
	framebuffer_t		buffers;
	texture_t			texture[GGL_TEXTURE_UNIT_COUNT];
    scissor_t           scissor;
    raster_t            raster;
	blend_state_t		blend;
    alpha_test_state_t  alpha_test;
    depth_test_state_t  depth_test;
    mask_state_t        mask;
    clear_state_t       clear;
    fog_state_t         fog;
    logic_op_state_t    logic_op;
    uint32_t            enables;
    uint32_t            enabled_tmu;
    needs_t             needs;
};

// ----------------------------------------------------------------------------

struct context_t {
	GGLContext          procs;
	state_t             state;
    shade_t             shade;
	iterators_t         iterators;
    generated_vars_t    generated_vars                __attribute__((aligned(32)));
    uint8_t             ditherMatrix[GGL_DITHER_SIZE] __attribute__((aligned(32)));
    uint32_t            packed;
    uint32_t            packed8888;
    const GGLFormat*    formats;
    uint32_t            dirty;
    texture_t*          activeTMU;
    uint32_t            activeTMUIndex;

    void                (*init_y)(context_t* c, int32_t y);
	void                (*step_y)(context_t* c);
	void                (*scanline)(context_t* c);
    void                (*span)(context_t* c);
    void                (*rect)(context_t* c, size_t yc);
    
    void*               base;
    Assembly*           scanline_as;
    GGLenum             error;
};

// ----------------------------------------------------------------------------

void ggl_init_context(context_t* context);
void ggl_uninit_context(context_t* context);
void ggl_error(context_t* c, GGLenum error);
int64_t ggl_system_time();

// ----------------------------------------------------------------------------

};

#endif // ANDROID_GGL_CONTEXT_H

