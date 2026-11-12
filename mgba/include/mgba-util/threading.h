/* Copyright (c) 2013-2015 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#ifndef THREADING_H
#define THREADING_H

#include <mgba-util/common.h>

CXX_GUARD_START


typedef void* Thread;
typedef void* Mutex;
typedef void* Condition;
typedef int ThreadLocal;

static inline int MutexInit(Mutex* mutex) {
	UNUSED(mutex);
	return 0;
}

static inline int MutexDeinit(Mutex* mutex) {
	UNUSED(mutex);
	return 0;
}

static inline int MutexLock(Mutex* mutex) {
	UNUSED(mutex);
	return 0;
}

static inline int MutexTryLock(Mutex* mutex) {
	UNUSED(mutex);
	return 0;
}

static inline int MutexUnlock(Mutex* mutex) {
	UNUSED(mutex);
	return 0;
}

static inline int ConditionInit(Condition* cond) {
	UNUSED(cond);
	return 0;
}

static inline int ConditionDeinit(Condition* cond) {
	UNUSED(cond);
	return 0;
}

static inline int ConditionWait(Condition* cond, Mutex* mutex) {
	UNUSED(cond);
	UNUSED(mutex);
	return 0;
}

static inline int ConditionWaitTimed(Condition* cond, Mutex* mutex, int32_t timeoutMs) {
	UNUSED(cond);
	UNUSED(mutex);
	UNUSED(timeoutMs);
	return 0;
}

static inline int ConditionWake(Condition* cond) {
	UNUSED(cond);
	return 0;
}

static inline void ThreadLocalInitKey(ThreadLocal* key) {
	UNUSED(key);
}

static inline void ThreadLocalSetKey(ThreadLocal key, void* value) {
	UNUSED(key);
	UNUSED(value);
}

static inline void* ThreadLocalGetValue(ThreadLocal key) {
	UNUSED(key);
	return NULL;
}

CXX_GUARD_END

#endif
