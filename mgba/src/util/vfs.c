/* Copyright (c) 2013-2015 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
#include <mgba-util/vfs.h>

#include <mgba-util/string.h>


struct VFile* VFileOpen(const char* path, int flags) {
	return VFileOpenFD(path, flags);
}


ssize_t VFileReadline(struct VFile* vf, char* buffer, size_t size) {
	size_t bytesRead = 0;
	while (bytesRead < size - 1) {
		ssize_t newRead = vf->read(vf, &buffer[bytesRead], 1);
		if (newRead <= 0) {
			break;
		}
		bytesRead += newRead;
		if (buffer[bytesRead - newRead] == '\n') {
			break;
		}
	}
	buffer[bytesRead] = '\0';
	return bytesRead;
}

ssize_t VFileWrite32LE(struct VFile* vf, int32_t word) {
	uint32_t leword;
	STORE_32LE(word, 0, &leword);
	return vf->write(vf, &leword, 4);
}

ssize_t VFileWrite16LE(struct VFile* vf, int16_t hword) {
	uint16_t lehword;
	STORE_16LE(hword, 0, &lehword);
	return vf->write(vf, &lehword, 2);
}

ssize_t VFileRead32LE(struct VFile* vf, void* word) {
	uint32_t leword;
	ssize_t r = vf->read(vf, &leword, 4);
	if (r == 4) {
		STORE_32LE(leword, 0, word);
	}
	return r;
}

ssize_t VFileRead16LE(struct VFile* vf, void* hword) {
	uint16_t lehword;
	ssize_t r = vf->read(vf, &lehword, 2);
	if (r == 2) {
		STORE_16LE(lehword, 0, hword);
	}
	return r;
}

void separatePath(const char* path, char* dirname, char* basename, char* extension) {
	if (!path) {
		return;
	}
	const char* dotPoint = strrchr(path, '.');
	const char* separatorPoint = strnrstr(path, PATH_SEP, strlen(path));
	if (separatorPoint) {
		if (dirname) {
			ptrdiff_t len = separatorPoint - path;
			if (PATH_MAX <= len) {
				len = PATH_MAX - 1;
			} else if (!len) {
				len = 1;
			}
			strncpy(dirname, path, len);
			dirname[len] = '\0';
		}
		path = separatorPoint + 1;
	} else if (dirname) {
		strcpy(dirname, ".");
	}
	if (basename) {
		size_t len;
		if (dotPoint) {
			len = dotPoint - path;
		} else {
			len = strlen(path);
		}
		if (PATH_MAX <= len) {
			len = PATH_MAX - 1;
		}
		strncpy(basename, path, len);
		basename[len] = '\0';
	}
	if (extension) {
		if (dotPoint) {
			++dotPoint;
			size_t len = strlen(dotPoint);
			if (PATH_MAX <= len) {
				len = PATH_MAX - 1;
			}
			strncpy(extension, dotPoint, PATH_MAX - 1);
			extension[len] = '\0';
		} else {
			extension[0] = '\0';
		}
	}
}

bool isAbsolute(const char* path) {
	// XXX: Is this robust?
	return path[0] == '/';
}

void makeAbsolute(const char* path, const char* base, char* out) {
	if (isAbsolute(path)) {
		strncpy(out, path, PATH_MAX);
		return;
	}

	char buf[PATH_MAX];
	snprintf(buf, sizeof(buf), "%s" PATH_SEP "%s", base, path);
#ifdef HAVE_REALPATH
	if (realpath(buf, out)) {
		return;
	}
#endif
	strncpy(out, buf, PATH_MAX);
}

bool upDirectory(char* path) {

	while (true) {
		char* end = strnrstr(path, PATH_SEP, strlen(path));
		if (!end) {
			return false;
		}
		if (!end[1]) {
			end[0] = '\0';
			// Trailing slash
			continue;
		}
		end[0] = '\0';
		return true;
	}
}

