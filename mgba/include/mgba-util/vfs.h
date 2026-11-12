/* Copyright (c) 2013-2014 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#ifndef VFS_H
#define VFS_H

#include <mgba-util/common.h>

CXX_GUARD_START

#define PATH_SEP "/"

#ifndef PATH_MAX
#ifdef MAX_PATH
#define PATH_MAX MAX_PATH
#else
#define PATH_MAX 128
#endif
#endif

enum {
	MAP_READ = 1,
	MAP_WRITE = 2
};

enum VFSType {
	VFS_UNKNOWN = 0,
	VFS_FILE,
	VFS_DIRECTORY
};

struct VFile {
	bool (*close)(struct VFile* vf);
	off_t (*seek)(struct VFile* vf, off_t offset, int whence);
	ssize_t (*read)(struct VFile* vf, void* buffer, size_t size);
	ssize_t (*readline)(struct VFile* vf, char* buffer, size_t size);
	ssize_t (*write)(struct VFile* vf, const void* buffer, size_t size);
	void* (*map)(struct VFile* vf, size_t size, int flags);
	void (*unmap)(struct VFile* vf, void* memory, size_t size);
	void (*truncate)(struct VFile* vf, size_t size);
	ssize_t (*size)(struct VFile* vf);
	bool (*sync)(struct VFile* vf, void* buffer, size_t size);
};


struct VFile* VFileOpen(const char* path, int flags);

struct VFile* VFileOpenFD(const char* path, int flags);
struct VFile* VFileFromFD(int fd);


struct VFile* VFileFromMemory(void* mem, size_t size);
struct VFile* VFileFromConstMemory(const void* mem, size_t size);
struct VFile* VFileMemChunk(const void* mem, size_t size);

struct mCircleBuffer;
struct VFile* VFileFIFO(struct mCircleBuffer* backing);


void separatePath(const char* path, char* dirname, char* basename, char* extension);

bool isAbsolute(const char* path);
void makeAbsolute(const char* path, const char* base, char* out);
bool upDirectory(char* path);

ssize_t VFileReadline(struct VFile* vf, char* buffer, size_t size);

ssize_t VFileWrite32LE(struct VFile* vf, int32_t word);
ssize_t VFileWrite16LE(struct VFile* vf, int16_t hword);
ssize_t VFileRead32LE(struct VFile* vf, void* word);
ssize_t VFileRead16LE(struct VFile* vf, void* hword);

CXX_GUARD_END

#endif
