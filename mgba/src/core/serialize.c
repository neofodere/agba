/* Copyright (c) 2013-2016 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#include <mgba/core/serialize.h>

#include <mgba/core/core.h>
#include <mgba/core/cheats.h>
#include <mgba/core/interface.h>
#include <mgba/core/version.h>
#include <mgba-util/memory.h>
#include <mgba-util/vfs.h>


mLOG_DEFINE_CATEGORY(SAVESTATE, "Savestate", "core.serialize");

struct mBundledState {
	size_t stateSize;
	void* state;
	struct mStateExtdata* extdata;
};

struct mStateExtdataHeader {
	uint32_t tag;
	int32_t size;
	int64_t offset;
};

void mStateExtdataInit(struct mStateExtdata* extdata) {
	memset(extdata->data, 0, sizeof(extdata->data));
}

void mStateExtdataDeinit(struct mStateExtdata* extdata) {
	size_t i;
	for (i = 1; i < EXTDATA_MAX; ++i) {
		if (extdata->data[i].data && extdata->data[i].clean) {
			extdata->data[i].clean(extdata->data[i].data);
		}
	}
	memset(extdata->data, 0, sizeof(extdata->data));
}

void mStateExtdataPut(struct mStateExtdata* extdata, enum mStateExtdataTag tag, struct mStateExtdataItem* item) {
	if (tag == EXTDATA_NONE || tag >= EXTDATA_MAX) {
		return;
	}

	if (extdata->data[tag].data && extdata->data[tag].clean) {
		extdata->data[tag].clean(extdata->data[tag].data);
	}
	extdata->data[tag] = *item;
}

bool mStateExtdataGet(const struct mStateExtdata* extdata, enum mStateExtdataTag tag, struct mStateExtdataItem* item) {
	if (tag == EXTDATA_NONE || tag >= EXTDATA_MAX) {
		return false;
	}

	*item = extdata->data[tag];
	return true;
}

bool mStateExtdataSerialize(struct mStateExtdata* extdata, struct VFile* vf) {
	ssize_t position = vf->seek(vf, 0, SEEK_CUR);
	ssize_t size = sizeof(struct mStateExtdataHeader);
	size_t i = 0;
	for (i = 1; i < EXTDATA_MAX; ++i) {
		if (extdata->data[i].data) {
			size += sizeof(struct mStateExtdataHeader);
		}
	}
	if (size == sizeof(struct mStateExtdataHeader)) {
		return true;
	}
	struct mStateExtdataHeader* header = malloc(size);
	position += size;

	size_t j;
	for (i = 1, j = 0; i < EXTDATA_MAX; ++i) {
		if (extdata->data[i].data) {
			STORE_32LE(i, offsetof(struct mStateExtdataHeader, tag), &header[j]);
			STORE_32LE(extdata->data[i].size, offsetof(struct mStateExtdataHeader, size), &header[j]);
			STORE_64LE(position, offsetof(struct mStateExtdataHeader, offset), &header[j]);
			position += extdata->data[i].size;
			++j;
		}
	}
	header[j].tag = 0;
	header[j].size = 0;
	header[j].offset = 0;

	if (vf->write(vf, header, size) != size) {
		free(header);
		return false;
	}
	free(header);

	for (i = 1; i < EXTDATA_MAX; ++i) {
		if (extdata->data[i].data) {
			if (vf->write(vf, extdata->data[i].data, extdata->data[i].size) != extdata->data[i].size) {
				return false;
			}
		}
	}
	return true;
}

bool mStateExtdataDeserialize(struct mStateExtdata* extdata, struct VFile* vf) {
	while (true) {
		struct mStateExtdataHeader buffer, header;
		if (vf->read(vf, &buffer, sizeof(buffer)) != sizeof(buffer)) {
			return false;
		}
		LOAD_32LE(header.tag, 0, &buffer.tag);
		LOAD_32LE(header.size, 0, &buffer.size);
		LOAD_64LE(header.offset, 0, &buffer.offset);

		if (header.tag == EXTDATA_NONE) {
			break;
		}
		if (header.tag >= EXTDATA_MAX) {
			continue;
		}
		ssize_t position = vf->seek(vf, 0, SEEK_CUR);
		if (vf->seek(vf, header.offset, SEEK_SET) < 0) {
			return false;
		}
		if (header.size <= 0) {
			continue;
		}
		struct mStateExtdataItem item = {
			.data = malloc(header.size),
			.size = header.size,
			.clean = free
		};
		if (!item.data) {
			continue;
		}
		if (vf->read(vf, item.data, header.size) != header.size) {
			free(item.data);
			continue;
		}
		mStateExtdataPut(extdata, header.tag, &item);
		vf->seek(vf, position, SEEK_SET);
	};
	return true;
}


bool mCoreSaveStateNamed(struct mCore* core, struct VFile* vf, int flags) {
	struct mStateExtdata extdata;
	mStateExtdataInit(&extdata);
	size_t stateSize = core->stateSize(core);

	core->saveExtraState(core, &extdata);
	if (flags & SAVESTATE_METADATA) {
		uint64_t* creationUsec = malloc(sizeof(*creationUsec));
		if (creationUsec) {
			struct timeval tv;
			if (!gettimeofday(&tv, 0)) {
				uint64_t usec = tv.tv_usec;
				usec += tv.tv_sec * 1000000LL;
				STORE_64LE(usec, 0, creationUsec);
			}
			else {
				free(creationUsec);
				creationUsec = 0;
			}
		}

		if (creationUsec) {
			struct mStateExtdataItem item = {
				.size = sizeof(*creationUsec),
				.data = creationUsec,
				.clean = free
			};
			mStateExtdataPut(&extdata, EXTDATA_META_TIME, &item);
		}

		char creator[256];
		snprintf(creator, sizeof(creator), "%s %s", projectName, projectVersion);
		struct mStateExtdataItem item = {
			.size = strlen(creator) + 1,
			.data = strdup(creator),
			.clean = free
		};
		mStateExtdataPut(&extdata, EXTDATA_META_CREATOR, &item);
	}

	if (flags & SAVESTATE_SAVEDATA) {
		void* sram = NULL;
		size_t size = core->savedataClone(core, &sram);
		if (size) {
			struct mStateExtdataItem item = {
				.size = size,
				.data = sram,
				.clean = free
			};
			mStateExtdataPut(&extdata, EXTDATA_SAVEDATA, &item);
		}
	}
	struct VFile* cheatVf = 0;
	struct mCheatDevice* device;
	if (flags & SAVESTATE_CHEATS && (device = core->cheatDevice(core))) {
		cheatVf = VFileMemChunk(0, 0);
		if (cheatVf) {
			mCheatSaveFile(device, cheatVf);
			struct mStateExtdataItem item = {
				.size = cheatVf->size(cheatVf),
				.data = cheatVf->map(cheatVf, cheatVf->size(cheatVf), MAP_READ),
				.clean = 0
			};
			mStateExtdataPut(&extdata, EXTDATA_CHEATS, &item);
		}
	}
	if (flags & SAVESTATE_RTC) {
		struct mStateExtdataItem item;
		if (core->rtc.d.serialize) {
			core->rtc.d.serialize(&core->rtc.d, &item);
			mStateExtdataPut(&extdata, EXTDATA_RTC, &item);
		}
	}
		vf->truncate(vf, stateSize);
		void* state = vf->map(vf, stateSize, MAP_WRITE);
		if (!state) {
			mStateExtdataDeinit(&extdata);
			if (cheatVf) {
				cheatVf->close(cheatVf);
			}
			return false;
		}
		core->saveState(core, state);
		vf->unmap(vf, state, stateSize);
		vf->seek(vf, stateSize, SEEK_SET);
		mStateExtdataSerialize(&extdata, vf);
		mStateExtdataDeinit(&extdata);
		if (cheatVf) {
			cheatVf->close(cheatVf);
		}
		return true;
	mStateExtdataDeinit(&extdata);
	return false;
}

void* mCoreExtractState(struct mCore* core, struct VFile* vf, struct mStateExtdata* extdata) {
	ssize_t stateSize = core->stateSize(core);
	void* state = anonymousMemoryMap(stateSize);
	vf->seek(vf, 0, SEEK_SET);
	if (vf->read(vf, state, stateSize) != stateSize) {
		mappedMemoryFree(state, stateSize);
		return 0;
	}
	if (extdata) {
		mStateExtdataDeserialize(extdata, vf);
	}
	return state;
}

bool mCoreExtractExtdata(struct mCore* core, struct VFile* vf, struct mStateExtdata* extdata) {
	if (!core) {
		return false;
	}
	ssize_t stateSize = core->stateSize(core);
	vf->seek(vf, stateSize, SEEK_SET);
	return mStateExtdataDeserialize(extdata, vf);
}

bool mCoreLoadStateNamed(struct mCore* core, struct VFile* vf, int flags) {
	struct mStateExtdata extdata;
	mStateExtdataInit(&extdata);
	void* state = mCoreExtractState(core, vf, &extdata);
	if (!state) {
		return false;
	}
	bool success = core->loadState(core, state);
	mappedMemoryFree(state, core->stateSize(core));

	core->loadExtraState(core, &extdata);

	unsigned width, height;
	core->currentVideoSize(core, &width, &height);

	struct mStateExtdataItem item;
	if (flags & SAVESTATE_SCREENSHOT && mStateExtdataGet(&extdata, EXTDATA_SCREENSHOT, &item)) {
		mLOG(SAVESTATE, INFO, "Loading screenshot");
		if (item.size >= (int) (width * height) * 4) {
			core->putPixels(core, item.data, width);
		} else {
			mLOG(SAVESTATE, WARN, "Savestate includes invalid screenshot");
		}
	}
	if (mStateExtdataGet(&extdata, EXTDATA_SAVEDATA, &item)) {
		mLOG(SAVESTATE, INFO, "Loading savedata");
		if (item.data) {
			if (!core->savedataRestore(core, item.data, item.size, flags & SAVESTATE_SAVEDATA)) {
				mLOG(SAVESTATE, WARN, "Failed to load savedata from savestate");
			}
		}
	}
	struct mCheatDevice* device;
	if (flags & SAVESTATE_CHEATS && (device = core->cheatDevice(core)) && mStateExtdataGet(&extdata, EXTDATA_CHEATS, &item)) {
		mLOG(SAVESTATE, INFO, "Loading cheats");
		if (item.size) {
			struct VFile* svf = VFileFromMemory(item.data, item.size);
			if (svf) {
				mCheatDeviceClear(device);
				mCheatParseFile(device, svf);
				svf->close(svf);
			}
		}
	}
	if (flags & SAVESTATE_RTC && mStateExtdataGet(&extdata, EXTDATA_RTC, &item)) {
		mLOG(SAVESTATE, INFO, "Loading RTC");
		if (core->rtc.d.deserialize) {
			core->rtc.d.deserialize(&core->rtc.d, &item);
		}
	}
	mStateExtdataDeinit(&extdata);
	return success;
}

