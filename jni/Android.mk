## Android makefile
## written by Enrico Casini

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := util
LOCAL_SRC_FILES := ../obj/local/armeabi/libutil.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../../util/cpp
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := grpmgr
LOCAL_SRC_FILES := ../obj/local/armeabi/libgrpmgr.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../../../aci/cpp/grpMgr
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := grpmgrjavawrapper
LOCAL_SRC_FILES := ../obj/local/armeabi/libgrpmgrjavawrapper.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)../../../../aci/cpp/grpMgr/jni
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
## LOCAL_SRC_FILES := \ ##
LOCAL_MODULE    := netutils
LOCAL_CPPFLAGS	:= -DUNIX -DLINUX -DANDROID -DLITTLE_ENDIAN_SYSTEM
LOCAL_LDLIBS := -lz 

LOCAL_C_INCLUDES += \
    $(LOCAL_PATH)/../ \
    $(LOCAL_PATH)/../../../util/cpp \
    $(LOCAL_PATH)/../../../aci/cpp/grpMgr \
    $(LOCAL_PATH)/../../../util/cpp/grpMgr/jni \

LOCAL_SHARED_LIBRARIES := util \
    grpmgr \
    grpmgrjavawrapper
	
include $(BUILD_SHARED_LIBRARY)
#include $(BUILD_STATIC_LIBRARY)
