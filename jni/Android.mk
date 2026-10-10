LOCAL_PATH := $(call my-dir)
UPNP_ROOT := $(LOCAL_PATH)/..

include $(CLEAR_VARS)

LOCAL_MODULE := libupnp
LOCAL_C_INCLUDES := \
    $(UPNP_ROOT)/ixml/inc \
    $(UPNP_ROOT)/ixml/src/inc \
    $(UPNP_ROOT)/upnp/inc
LOCAL_SRC_FILES := \
    ../ixml/src/ixml.c \
    ../ixml/src/ixmlparser.c \
    ../upnp/src/api/upnpapi.c \
    ../upnp/src/api/upnptools.c \
    ../upnp/src/genlib/client_table/client_table.c \
    ../upnp/src/genlib/net/http/httpreadwrite.c \
    ../upnp/src/genlib/net/http/httpparser.c \
    ../upnp/src/genlib/net/uri/uri.c \
    ../upnp/src/genlib/net/socket/socket.c \
    ../upnp/src/genlib/util/genutil.c \
    ../upnp/src/genlib/util/upnp_timeout.c \
    ../upnp/src/genlib/util/upnp_list.c \
    ../upnp/src/genlib/util/upnp_string.c \
    ../upnp/src/genlib/util/upnp_memory.c \
    ../upnp/src/genlib/util/upnp_thread.c \
    ../upnp/src/genlib/util/upnp_platform.c \
    ../upnp/src/upnp/device/device.c \
    ../upnp/src/upnp/device/service.c \
    ../upnp/src/upnp/device/statevar.c \
    ../upnp/src/upnp/control/action.c \
    ../upnp/src/upnp/control/control.c \
    ../upnp/src/upnp/control/subscription.c \
    ../upnp/src/upnp/event/event.c \
    ../upnp/src/upnp/event/notify.c \
    ../upnp/src/upnp/event/subscription.c \
    ../upnp/src/upnp/ssdp/ssdp.c \
    ../upnp/src/upnp/ssdp/ssdp_server.c \
    ../upnp/src/upnp/ssdp/ssdp_search.c \
    ../upnp/src/upnp/ssdp/ssdp_device.c \
    ../upnp/src/upnp/ssdp/ssdp_ctrlpt.c \
    ../upnp/src/upnp/soap/soap.c \
    ../upnp/src/upnp/soap/soap_device.c \
    ../upnp/src/upnp/soap/soap_ctrlpt.c \
    ../upnp/src/upnp/webserver/webserver.c \
    ../upnp/src/upnp/webserver/webserver_device.c \
    ../upnp/src/upnp/webserver/webserver_ctrlpt.c

include $(BUILD_SHARED_LIBRARY)
