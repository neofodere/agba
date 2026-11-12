/* Copyright (c) 2013-2016 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#include <mgba/core/core.h>

#include <mgba/core/cheats.h>
#include <mgba/core/log.h>
#include <mgba/core/serialize.h>
#include <mgba-util/vfs.h>
#include <mgba/internal/debugger/symbols.h>



#include <mgba/gb/core.h>
#include <mgba/gb/interface.h>
#include <mgba/gba/core.h>
#include <mgba/gba/interface.h>

static const struct mCoreFilter {
	bool (*filter)(struct VFile*);
	struct mCore* (*open)(void);
	enum mPlatform platform;
} _filters[] = {
	{ GBAIsROM, GBACoreCreate, mPLATFORM_GBA },
	{ GBIsROM, GBCoreCreate, mPLATFORM_GB },
	{ 0, 0, mPLATFORM_NONE }
};

struct mCore* mCoreFindVF(struct VFile* vf) {
	if (!vf) {
		return NULL;
	}
	const struct mCoreFilter* filter;
	for (filter = &_filters[0]; filter->filter; ++filter) {
		if (filter->filter(vf)) {
			break;
		}
	}
	if (filter->open) {
		return filter->open();
	}
	return NULL;
}

enum mPlatform mCoreIsCompatible(struct VFile* vf) {
	if (!vf) {
		return mPLATFORM_NONE;
	}
	const struct mCoreFilter* filter;
	for (filter = &_filters[0]; filter->filter; ++filter) {
		if (filter->filter(vf)) {
			return filter->platform;
		}
	}
	return mPLATFORM_NONE;
}

struct mCore* mCoreCreate(enum mPlatform platform) {
	const struct mCoreFilter* filter;
	for (filter = &_filters[0]; filter->filter; ++filter) {
		if (filter->platform == platform) {
			break;
		}
	}
	if (filter->open) {
		return filter->open();
	}
	return NULL;
}


struct mCore* mCoreFind(const char* path) {
	struct mCore* core = NULL;
	{
		struct VFile* vf = VFileOpen(path, O_RDONLY);
		if (!vf) {
			return NULL;
		}
		core = mCoreFindVF(vf);
		vf->close(vf);
	}
	if (core) {
		return core;
	}
	return NULL;
}

bool mCoreLoadFile(struct mCore* core, const char* path) {
	core->unloadROM(core);
	struct VFile* rom = VFileOpen(path, O_RDONLY);
	if (rom && !core->isROM(rom)) {
		rom->close(rom);
		rom = NULL;
	}
	if (!rom) {
		return false;
	}

	bool ret = core->loadROM(core, rom);
	if (!ret) {
		rom->close(rom);
	}
	return ret;
}

bool mCorePreloadVF(struct mCore* core, struct VFile* vf) {
	return mCorePreloadVFCB(core, vf, NULL, NULL);
}

bool mCorePreloadFile(struct mCore* core, const char* path) {
	return mCorePreloadFileCB(core, path, NULL, NULL);
}

bool mCorePreloadVFCB(struct mCore* core, struct VFile* vf, void (cb)(size_t, size_t, void*), void* context) {
	struct VFile* vfm;
	size_t size = vf->size(vf);

	vfm = VFileMemChunk(NULL, size);

	size_t chunkSize;
	uint8_t buffer[0x4000];
	chunkSize = sizeof(buffer);
	ssize_t read;
	size_t total = 0;
	vf->seek(vf, 0, SEEK_SET);
	while ((read = vf->read(vf, buffer, chunkSize)) > 0) {
		vfm->write(vfm, buffer, read);
		total += read;
		if (cb) {
			cb(total, size, context);
		}
	}
	vf->close(vf);
	if (read < 0) {
		vfm->close(vfm);
		return false;
	}
	bool ret = core->loadROM(core, vfm);
	if (!ret) {
		vfm->close(vfm);
	}
	return ret;
}

bool mCorePreloadFileCB(struct mCore* core, const char* path, void (cb)(size_t, size_t, void*), void* context) {
	struct VFile* rom = VFileOpen(path, O_RDONLY);
	if (rom && !core->isROM(rom)) {
		rom->close(rom);
		rom = NULL;
	}
	if (!rom) {
		return false;
	}

	bool ret = mCorePreloadVFCB(core, rom, cb, context);
	if (!ret) {
		rom->close(rom);
	}
	return ret;
}

bool mCoreLoadSaveFile(struct mCore* core, const char* path, bool temporary) {
	struct VFile* vf = VFileOpen(path, O_CREAT | O_RDWR);
	if (!vf) {
		return false;
	}
	if (temporary) {
		return core->loadTemporarySave(core, vf);
	} else {
		return core->loadSave(core, vf);
	}
}


bool mCoreTakeScreenshotVF(struct mCore* core, struct VFile* vf) {
	UNUSED(core);
	UNUSED(vf);
	return false;
}

void mCoreInitConfig(struct mCore* core, const char* port) {
	mCoreConfigInit(&core->config, port);
}

void mCoreLoadConfig(struct mCore* core) {
	mCoreConfigLoad(&core->config);
	mCoreLoadForeignConfig(core, &core->config);
}

void mCoreLoadForeignConfig(struct mCore* core, const struct mCoreConfig* config) {
	mCoreConfigMap(config, &core->opts);
	if (core->opts.audioBuffers) {
		core->setAudioBufferSize(core, core->opts.audioBuffers);
	}

	mCoreConfigCopyValue(&core->config, config, "cheatAutosave");
	mCoreConfigCopyValue(&core->config, config, "cheatAutoload");
	mCoreConfigCopyValue(&core->config, config, "savePlayerId");

	core->loadConfig(core, config);
}

void mCoreSetRTC(struct mCore* core, struct mRTCSource* rtc) {
	core->rtc.custom = rtc;
	core->rtc.override = RTC_CUSTOM_START;
}

void* mCoreGetMemoryBlock(struct mCore* core, uint32_t start, size_t* size) {
	return mCoreGetMemoryBlockMasked(core, start, size, mCORE_MEMORY_MAPPED);
}

void* mCoreGetMemoryBlockMasked(struct mCore* core, uint32_t start, size_t* size, uint32_t mask) {
	const struct mCoreMemoryBlock* block = mCoreGetMemoryBlockInfo(core, start);
	if (!block || !(block->flags & mask)) {
		return NULL;
	}
	uint8_t* out = core->getMemoryBlock(core, block->id, size);
	out += start - block->start;
	*size -= start - block->start;
	return out;
}

const struct mCoreMemoryBlock* mCoreGetMemoryBlockInfo(const struct mCore* core, uint32_t address) {
	const struct mCoreMemoryBlock* blocks;
	size_t nBlocks = core->listMemoryBlocks(core, &blocks);
	size_t i;
	for (i = 0; i < nBlocks; ++i) {
		if (!(blocks[i].flags & mCORE_MEMORY_MAPPED)) {
			continue;
		}
		if (address < blocks[i].start) {
			continue;
		}
		if (address >= blocks[i].start + blocks[i].size) {
			continue;
		}
		return &blocks[i];
	}
	return NULL;
}

const struct mCoreRegisterInfo* mCoreGetRegisterInfo(const struct mCore* core, const char* name) {
	const struct mCoreRegisterInfo* registers;
	size_t nRegisters = core->listRegisters(core, &registers);
	size_t i;
	for (i = 0; i < nRegisters; ++i) {
		if (strcmp(name, registers[i].name) == 0) {
			return &registers[i];
		}
	}
	return NULL;
}

double mCoreCalculateFramerateRatio(const struct mCore* core, double desiredFrameRate) {
	uint32_t clockRate = core->frequency(core);
	uint32_t frameCycles = core->frameCycles(core);
	return clockRate / (desiredFrameRate * frameCycles);
}

