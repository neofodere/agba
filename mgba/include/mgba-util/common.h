/* Copyright (c) 2013-2014 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#ifndef COMMON_H
#define COMMON_H

#ifdef __cplusplus
#define CXX_GUARD_START extern "C" {
#define CXX_GUARD_END }
#else
#define CXX_GUARD_START
#define CXX_GUARD_END
#endif

CXX_GUARD_START


#include <assert.h>
#include <ctype.h>
#include <fcntl.h>
#include <inttypes.h>
#include <limits.h>
#include <math.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#ifdef __cplusplus
#define restrict __restrict
#endif

#ifndef containerof
#define containerof(PTR, TYPE, MEMBER) ((TYPE*) ((uintptr_t) (PTR) - offsetof(TYPE, MEMBER)))
#endif

#include <strings.h>
#include <unistd.h>
#include <sys/time.h>


#include <mgba-util/dllexports.h>

#ifndef SSIZE_MAX
#define SSIZE_MAX ((ssize_t) (SIZE_MAX >> 1))
#endif

#ifndef UNUSED
#define UNUSED(V) (void)(V)
#endif

#ifndef M_PI
#define M_PI 3.141592654f
#endif

#if !defined(__cplusplus) && !defined(static_assert)
#define static_assert(X, C) _Static_assert((X), C)
#endif

#if (defined(__llvm__) || (__GNUC__ > 4) || (__GNUC__ == 4 && __GNUC_MINOR__ >= 7))
#define ATOMIC_STORE(DST, SRC) __atomic_store_n(&DST, SRC, __ATOMIC_RELEASE)
#define ATOMIC_LOAD(DST, SRC) DST = __atomic_load_n(&SRC, __ATOMIC_ACQUIRE)
#define ATOMIC_ADD(DST, OP) __atomic_add_fetch(&DST, OP, __ATOMIC_RELEASE)
#define ATOMIC_SUB(DST, OP) __atomic_sub_fetch(&DST, OP, __ATOMIC_RELEASE)
#define ATOMIC_OR(DST, OP) __atomic_or_fetch(&DST, OP, __ATOMIC_RELEASE)
#define ATOMIC_AND(DST, OP) __atomic_and_fetch(&DST, OP, __ATOMIC_RELEASE)
#define ATOMIC_CMPXCHG(DST, EXPECTED, SRC) __atomic_compare_exchange_n(&DST, &EXPECTED, SRC, true,__ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)
#define ATOMIC_STORE_PTR(DST, SRC) ATOMIC_STORE(DST, SRC)
#define ATOMIC_LOAD_PTR(DST, SRC) ATOMIC_LOAD(DST, SRC)
#else
// TODO
#define ATOMIC_STORE(DST, SRC) ((DST) = (SRC))
#define ATOMIC_LOAD(DST, SRC) ((DST) = (SRC))
#define ATOMIC_ADD(DST, OP) ((DST) += (OP))
#define ATOMIC_SUB(DST, OP) ((DST) -= (OP))
#define ATOMIC_OR(DST, OP) ((DST) |= (OP))
#define ATOMIC_AND(DST, OP) ((DST) &= (OP))
#define ATOMIC_CMPXCHG(DST, EXPECTED, OP) (((DST) == (EXPECTED)) ? (((DST) = (OP)), true) : false)
#define ATOMIC_STORE_PTR(DST, SRC) ATOMIC_STORE(DST, SRC)
#define ATOMIC_LOAD_PTR(DST, SRC) ATOMIC_LOAD(DST, SRC)
#endif

#define PRIz "z"

#define LOAD_64LE(DEST, ADDR, ARR) DEST = *(uint64_t*) ((uintptr_t) (ARR) + (size_t) (ADDR))
#define LOAD_32LE(DEST, ADDR, ARR) DEST = *(uint32_t*) ((uintptr_t) (ARR) + (size_t) (ADDR))
#define LOAD_16LE(DEST, ADDR, ARR) DEST = *(uint16_t*) ((uintptr_t) (ARR) + (size_t) (ADDR))
#define STORE_64LE(SRC, ADDR, ARR) *(uint64_t*) ((uintptr_t) (ARR) + (size_t) (ADDR)) = SRC
#define STORE_32LE(SRC, ADDR, ARR) *(uint32_t*) ((uintptr_t) (ARR) + (size_t) (ADDR)) = SRC
#define STORE_16LE(SRC, ADDR, ARR) *(uint16_t*) ((uintptr_t) (ARR) + (size_t) (ADDR)) = SRC
#define LOAD_64BE(DEST, ADDR, ARR) DEST = __builtin_bswap64(*(uint64_t*) ((uintptr_t) (ARR) + (size_t) (ADDR)))
#define LOAD_32BE(DEST, ADDR, ARR) DEST = __builtin_bswap32(*(uint32_t*) ((uintptr_t) (ARR) + (size_t) (ADDR)))
#define LOAD_16BE(DEST, ADDR, ARR) DEST = __builtin_bswap16(*(uint16_t*) ((uintptr_t) (ARR) + (size_t) (ADDR)))
#define STORE_64BE(SRC, ADDR, ARR) *(uint64_t*) ((uintptr_t) (ARR) + (size_t) (ADDR)) = __builtin_bswap64(SRC)
#define STORE_32BE(SRC, ADDR, ARR) *(uint32_t*) ((uintptr_t) (ARR) + (size_t) (ADDR)) = __builtin_bswap32(SRC)
#define STORE_16BE(SRC, ADDR, ARR) *(uint16_t*) ((uintptr_t) (ARR) + (size_t) (ADDR)) = __builtin_bswap16(SRC)

#define MAKE_MASK(START, END) (((1 << ((END) - (START))) - 1) << (START))
#define CHECK_BITS(SRC, START, END) ((SRC) & MAKE_MASK(START, END))
#define EXT_BITS(SRC, START, END) (((SRC) >> (START)) & ((1 << ((END) - (START))) - 1))
#define INS_BITS(SRC, START, END, BITS) (CLEAR_BITS(SRC, START, END) | (((BITS) << (START)) & MAKE_MASK(START, END)))
#define CLEAR_BITS(SRC, START, END) ((SRC) & ~MAKE_MASK(START, END))
#define FILL_BITS(SRC, START, END) ((SRC) | MAKE_MASK(START, END))
#define TEST_FILL_BITS(SRC, START, END, TEST) ((TEST) ? (FILL_BITS(SRC, START, END)) : (CLEAR_BITS(SRC, START, END)))

#define ATTRIBUTE_UNUSED __attribute__((unused))
#define ATTRIBUTE_FORMAT(X, Y, Z) __attribute__((format(X, Y, Z)))
#define ATTRIBUTE_NOINLINE __attribute__((noinline))
#if defined(__llvm__) || (__GNUC__ < 8)
#define ATTRIBUTE_NONSTRING
#else
#define ATTRIBUTE_NONSTRING __attribute__((nonstring))
#endif
#define CONSTRUCTOR(FN) static __attribute__((constructor)) void FN(void)

#define DECL_BITFIELD(NAME, TYPE) typedef TYPE NAME

#define DECL_BITS(TYPE, FIELD, START, SIZE) \
	ATTRIBUTE_UNUSED static inline TYPE TYPE ## Is ## FIELD (TYPE src) { \
		return CHECK_BITS(src, (START), (START) + (SIZE)); \
	} \
	ATTRIBUTE_UNUSED static inline TYPE TYPE ## Get ## FIELD (TYPE src) { \
		return EXT_BITS(src, (START), (START) + (SIZE)); \
	} \
	ATTRIBUTE_UNUSED static inline TYPE TYPE ## Clear ## FIELD (TYPE src) { \
		return CLEAR_BITS(src, (START), (START) + (SIZE)); \
	} \
	ATTRIBUTE_UNUSED static inline TYPE TYPE ## Fill ## FIELD (TYPE src) { \
		return FILL_BITS(src, (START), (START) + (SIZE)); \
	} \
	ATTRIBUTE_UNUSED static inline TYPE TYPE ## Set ## FIELD (TYPE src, TYPE bits) { \
		return INS_BITS(src, (START), (START) + (SIZE), bits); \
	} \
	ATTRIBUTE_UNUSED static inline TYPE TYPE ## TestFill ## FIELD (TYPE src, bool test) { \
		return TEST_FILL_BITS(src, (START), (START) + (SIZE), test); \
	}

#define DECL_BIT(TYPE, FIELD, BIT) DECL_BITS(TYPE, FIELD, BIT, 1)

#define LIKELY(X) __builtin_expect(!!(X), 1)
#define UNLIKELY(X) __builtin_expect(!!(X), 0)

#define ROR(I, ROTATE) ((((uint32_t) (I)) >> ROTATE) | ((uint32_t) (I) << ((-ROTATE) & 31)))

#define mASSERT(COND) \
	if (!(COND)) { \
		abort(); \
	}
#define mASSERT_DEBUG(COND) assert((COND))

#define mASSERT_LOG(CAT, COND, ...) \
	if (!(COND)) { \
		mLOG(CAT, FATAL, __VA_ARGS__); \
	}

#ifdef NDEBUG
#define mASSERT_DEBUG_LOG(...)
#else
#define mASSERT_DEBUG_LOG(CAT, COND, ...) \
	if (!(COND)) { \
		mLOG(CAT, FATAL, __VA_ARGS__); \
	}
#endif

CXX_GUARD_END

#endif
