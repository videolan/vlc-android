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

#ifndef ANDROID_ACC_ACC_H
#define ANDROID_ACC_ACC_H

#include <stdint.h>
#include <sys/types.h>

typedef char                        ACCchar;
typedef int32_t                     ACCint;
typedef uint32_t                    ACCuint;
typedef ssize_t                     ACCsizei;
typedef unsigned int                ACCenum;
typedef void                        ACCvoid;
typedef struct ACCscript            ACCscript;

#define ACC_NO_ERROR                0x0000
#define ACC_INVALID_ENUM            0x0500
#define ACC_INVALID_OPERATION       0x0502
#define ACC_INVALID_VALUE           0x0501
#define ACC_OUT_OF_MEMORY           0x0505

#define ACC_COMPILE_STATUS          0x8B81
#define ACC_INFO_LOG_LENGTH         0x8B84


// ----------------------------------------------------------------------------

#ifdef __cplusplus
extern "C" {
#endif

ACCscript* accCreateScript();

void accDeleteScript(ACCscript* script);

typedef ACCvoid* (*ACCSymbolLookupFn)(ACCvoid* pContext, const ACCchar * name);

void accRegisterSymbolCallback(ACCscript* script, ACCSymbolLookupFn pFn,
                               ACCvoid* pContext);

ACCenum accGetError( ACCscript* script );

void accScriptSource(ACCscript* script,
    ACCsizei count,
    const ACCchar** string,
    const ACCint* length);

void accCompileScript(ACCscript* script);

void accGetScriptiv(ACCscript* script,
    ACCenum pname,
    ACCint* params);

void accGetScriptInfoLog(ACCscript* script,
    ACCsizei maxLength,
    ACCsizei* length,
    ACCchar* infoLog);

void accGetScriptLabel(ACCscript* script, const ACCchar * name,
                       ACCvoid** address);

void accGetPragmas(ACCscript* script, ACCsizei* actualStringCount,
                   ACCsizei maxStringCount, ACCchar** strings);

/* Used to implement disassembly */

void accGetProgramBinary(ACCscript* script,
    ACCvoid** base,
    ACCsizei* length);

#ifdef __cplusplus
};
#endif

// ----------------------------------------------------------------------------

#endif
