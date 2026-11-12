VERSION ?= 1.0.0
VERSION_CODE ?= $(shell echo '$(VERSION)' | awk -F. '{ printf "%d", $$1 * 10000 + $$2 * 100 + $$3 }')

BUILD ?= $(shell git rev-list --count HEAD 2>/dev/null || echo 0)

GRADLE := sh ./gradlew --console=plain
GRADLE_PROPERTIES := -PagbaVersionName=$(VERSION) -PagbaVersionCode=$(VERSION_CODE) -PagbaBuild=$(BUILD)

RELEASE_APK := app/build/outputs/apk/release/app-release.apk
RELEASE_AAB := app/build/outputs/bundle/release/app-release.aab
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

.PHONY: apk aab debug install clean help

apk:
	$(GRADLE) :app:assembleRelease $(GRADLE_PROPERTIES)
	mkdir -p dist
	cp $(RELEASE_APK) dist/aGBA-$(VERSION).apk
	@echo "Built dist/aGBA-$(VERSION).apk"

aab:
	$(GRADLE) :app:bundleRelease $(GRADLE_PROPERTIES)
	mkdir -p dist
	cp $(RELEASE_AAB) dist/aGBA-$(VERSION).aab
	@echo "Built dist/aGBA-$(VERSION).aab"

debug:
	$(GRADLE) :app:assembleDebug $(GRADLE_PROPERTIES)
	mkdir -p dist
	cp $(DEBUG_APK) dist/aGBA-$(VERSION)-debug.apk
	@echo "Built dist/aGBA-$(VERSION)-debug.apk"

install: apk
	adb install -r dist/aGBA-$(VERSION).apk

clean:
	$(GRADLE) clean
	rm -rf dist

help:
	@echo "make apk      Build the release APK into dist/"
	@echo "make aab      Build the release AAB (Android App Bundle) into dist/"
	@echo "make debug    Build the debug APK into dist/"
	@echo "make install  Build the release APK and install it with adb"
	@echo "make clean    Remove build output"
	@echo "Variables: VERSION (default $(VERSION)), VERSION_CODE (derived from VERSION)"
