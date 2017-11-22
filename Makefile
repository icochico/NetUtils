OUT_DIR=obj/local/armeabi
LIB_DIR=libs
NATIVE_LIB_DIR=libs/armeabi

all: debug

prebuild:
	if [ ! -e local.properties ]; then echo "local.properties not found - updating"; android update project --name NetUtils --target android-15 --path .; fi
	make -C ../../../aci/cpp/grpMgr/jni/android
	make -C ../../../mockets/cpp/jni/android
	if [ ! -d $(OUT_DIR) ]; then mkdir -p $(OUT_DIR); fi;
	cp -Rv ../../../aci/cpp/grpMgr/jni/android/libs/armeabi/*.so $(OUT_DIR)
	cp -Rv ../../../mockets/cpp/jni/android/libs/armeabi/*.so $(OUT_DIR)
	cp -Rv ../../externals/android-logging-log4j-1.0.3.jar $(LIB_DIR)
	cp -Rv ../../externals/androidasync-1.3.7.jar $(LIB_DIR)
	cp -Rv ../../externals/log4j-1.2.17.jar $(LIB_DIR)
	cp -Rv ../../externals/gson-2.2.4.jar $(LIB_DIR)
	cp -Rv ../../externals/commons-lang3-3.1.jar $(LIB_DIR)

build: prebuild
	ndk-build
	ant -f ../../../aci/build/build.xml grpMgr
	cp -Rv ../../../aci/lib/grpMgr.jar $(LIB_DIR)
	ant -f ../../../misc/build/build.xml netutilsjar
	cp -Rv ../../../misc/lib/netutils.jar $(LIB_DIR) 
	cp -Rv $(OUT_DIR)/*.so $(NATIVE_LIB_DIR)

debug: build
	ant debug

release: build
	ant release
	jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore ../../externals/keystore/nomads.keystore bin/NetUtils-release-unsigned.apk nomads
	jarsigner -verify -verbose -certs bin/NetUtils-release-unsigned.apk
	../../externals/keystore/zipalign -v 4 bin/NetUtils-release-unsigned.apk bin/NetUtils.apk

install: 
	ant debug install
clean:
	ant clean
	rm -rf $(LIB_DIR)/grpMgr.jar
	rm -rf $(LIB_DIR)/netutils.jar
	rm -rf obj

cleanall: clean
	make -C ../../../aci/cpp/grpMgr/jni/android cleanall
	make -C ../../../mockets/cpp/jni/android cleanall
