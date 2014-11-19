/*
 * Copyright (C) 2007 The Android Open Source Project
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

/**
 * Very simple unit testing framework. 
 */

#ifndef __CUTILS_TEST_H
#define __CUTILS_TEST_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Adds a test to the test suite.
 */
#define addTest(test) addNamedTest(#test, &test)
   
/**
 * Asserts that a condition is true. The test fails if it isn't.
 */
#define assertTrue(value, message) assertTrueWithSource(value, __FILE__, __LINE__, message);

/**
 * Asserts that a condition is false. The test fails if the value is true.
 */
#define assertFalse(value, message) assertTrueWithSource(!value, __FILE__, __LINE__, message);

/** Fails a test with the given message. */
#define fail(message) assertTrueWithSource(0, __FILE__, __LINE__, message);

/**
 * Asserts that two values are ==.
 */
#define assertSame(a, b) assertTrueWithSource(a == b, __FILE__, __LINE__, "Expected same value.");
    
/**
 * Asserts that two values are !=.
 */
#define assertNotSame(a, b) assertTrueWithSource(a != b, __FILE__, __LINE__,\
        "Expected different values");
    
/**
 * Runs a test suite.
 */
void runTests(void);

// Do not call these functions directly. Use macros above instead.
void addNamedTest(const char* name, void (*test)(void));
void assertTrueWithSource(int value, const char* file, int line, char* message);
    
#ifdef __cplusplus
}
#endif

#endif /* __CUTILS_TEST_H */ 
