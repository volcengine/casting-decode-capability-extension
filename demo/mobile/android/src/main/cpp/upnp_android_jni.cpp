// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
// SPDX-License-Identifier: BSD-3-Clause

//
// upnp_android_jni.cpp
// JNI 层：封装 libupnp 控制点能力
// - 初始化 / 反初始化
// - 搜索 MediaRenderer 设备
// - 推送 URL（SetAVTransportURI）
// - 播放 / 暂停 / 停止
// - 设置音量 / 获取音量
//

#include <jni.h>
#include <android/log.h>
#include <string>
#include <map>
#include <mutex>
#include <pthread.h>
#include <cstring>
#include <cstdlib>
#include <vector>
#include <unistd.h>
#include <arpa/inet.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <cstdio>

#include "upnp.h"
#include "upnptools.h"
#include "UpnpString.h"
#include "UpnpDiscovery.h"
#include "UpnpActionComplete.h"
#include "ixml.h"
#include "ifaddrs_android.h"

#define LOG_TAG "UpnpJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// DLNA 服务类型
static const char *AV_TRANSPORT_TYPE = "urn:schemas-upnp-org:service:AVTransport:1";
static const char *RENDERING_CONTROL_TYPE = "urn:schemas-upnp-org:service:RenderingControl:1";
static const char *MEDIA_RENDERER_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1";

// 全局状态
static UpnpClient_Handle g_clientHandle = -1;
static JavaVM *g_jvm = nullptr;
static jobject g_callbackObj = nullptr;
static jmethodID g_onDeviceFoundMethod = nullptr;
static jmethodID g_onDeviceLostMethod = nullptr;
static jmethodID g_onActionResultMethod = nullptr;

// 设备信息
struct DeviceInfo {
    std::string udn;
    std::string friendlyName;
    std::string location;
    std::string avTransportControlURL;
    std::string renderingControlControlURL;
    std::string deviceAbilityRaw;
};

static std::map<std::string, DeviceInfo> g_deviceMap;
static std::mutex g_deviceMutex;

// ------------------ 工具函数 ------------------

static int ParseHmsToSeconds(const std::string &value) {
    int hh = 0;
    int mm = 0;
    int ss = 0;
    if (value.empty()) return 0;
    if (sscanf(value.c_str(), "%d:%d:%d", &hh, &mm, &ss) != 3) return 0;
    if (hh < 0 || mm < 0 || ss < 0) return 0;
    return hh * 3600 + mm * 60 + ss;
}

static std::string FormatSecondsToHms(int totalSeconds) {
    if (totalSeconds < 0) totalSeconds = 0;
    char buf[16];
    const int hh = totalSeconds / 3600;
    const int mm = (totalSeconds % 3600) / 60;
    const int ss = totalSeconds % 60;
    snprintf(buf, sizeof(buf), "%02d:%02d:%02d", hh, mm, ss);
    return std::string(buf);
}

static JNIEnv *GetEnv(bool *needDetach) {
    JNIEnv *env = nullptr;
    *needDetach = false;
    if (!g_jvm) return nullptr;
    int ret = g_jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
    if (ret == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) == 0) {
            *needDetach = true;
        } else {
            return nullptr;
        }
    }
    return env;
}

static void NotifyDeviceFound(const DeviceInfo &dev, int expiresSeconds) {
    bool needDetach = false;
    JNIEnv *env = GetEnv(&needDetach);
    if (!env || !g_callbackObj || !g_onDeviceFoundMethod) return;

    jstring jUdn = env->NewStringUTF(dev.udn.c_str());
    jstring jName = env->NewStringUTF(dev.friendlyName.c_str());
    jstring jLoc = env->NewStringUTF(dev.location.c_str());
    jstring jAvUrl = env->NewStringUTF(dev.avTransportControlURL.c_str());
    jstring jRcUrl = env->NewStringUTF(dev.renderingControlControlURL.c_str());
    jstring jDeviceAbility = env->NewStringUTF(dev.deviceAbilityRaw.c_str());

    env->CallVoidMethod(g_callbackObj, g_onDeviceFoundMethod,
                        jUdn, jName, jLoc, jAvUrl, jRcUrl, jDeviceAbility, (jint)expiresSeconds);

    env->DeleteLocalRef(jUdn);
    env->DeleteLocalRef(jName);
    env->DeleteLocalRef(jLoc);
    env->DeleteLocalRef(jAvUrl);
    env->DeleteLocalRef(jRcUrl);
    env->DeleteLocalRef(jDeviceAbility);

    if (needDetach) g_jvm->DetachCurrentThread();
}

static void NotifyDeviceLost(const std::string &udn) {
    bool needDetach = false;
    JNIEnv *env = GetEnv(&needDetach);
    if (!env || !g_callbackObj || !g_onDeviceLostMethod) return;

    jstring jUdn = env->NewStringUTF(udn.c_str());
    env->CallVoidMethod(g_callbackObj, g_onDeviceLostMethod, jUdn);
    env->DeleteLocalRef(jUdn);

    if (needDetach) g_jvm->DetachCurrentThread();
}

static void NotifyActionResult(const std::string &action, int errCode, const std::string &message) {
    bool needDetach = false;
    JNIEnv *env = GetEnv(&needDetach);
    if (!env || !g_callbackObj || !g_onActionResultMethod) return;

    jstring jAction = env->NewStringUTF(action.c_str());
    jstring jMsg = env->NewStringUTF(message.c_str());
    env->CallVoidMethod(g_callbackObj, g_onActionResultMethod,
                        jAction, (jint)errCode, jMsg);
    env->DeleteLocalRef(jAction);
    env->DeleteLocalRef(jMsg);

    if (needDetach) g_jvm->DetachCurrentThread();
}

// 从 XML Document 提取第一个匹配的标签内容
static std::string GetElementText(IXML_Document *doc, const char *tagName) {
    if (!doc) return "";
    IXML_NodeList *list = ixmlDocument_getElementsByTagName(doc, (char *)tagName);
    if (!list) return "";
    std::string result;
    IXML_Node *node = ixmlNodeList_item(list, 0);
    if (node) {
        IXML_Node *text = ixmlNode_getFirstChild(node);
        if (text) {
            const char *val = ixmlNode_getNodeValue(text);
            if (val) result = val;
        }
    }
    ixmlNodeList_free(list);
    return result;
}

static std::string GetElementInnerXml(IXML_Document *doc, const char *tagName) {
    if (!doc) return "";
    IXML_NodeList *list = ixmlDocument_getElementsByTagName(doc, (char *)tagName);
    if (!list) return "";
    std::string result;
    IXML_Node *node = ixmlNodeList_item(list, 0);
    if (node) {
        for (IXML_Node *child = ixmlNode_getFirstChild(node);
             child != nullptr;
             child = ixmlNode_getNextSibling(child)) {
            DOMString rawXml = ixmlPrintNode(child);
            if (rawXml) {
                result += rawXml;
                ixmlFreeDOMString(rawXml);
            }
        }
    }
    ixmlNodeList_free(list);
    return result;
}

static std::string ResolveUrl(const std::string &base, const std::string &rel) {
    if (rel.empty()) return "";
    if (rel.find("http://") == 0 || rel.find("https://") == 0) return rel;

    char *absUrl = nullptr;
    int rc = UpnpResolveURL2(base.c_str(), rel.c_str(), &absUrl);
    if (rc == UPNP_E_SUCCESS && absUrl) {
        std::string out(absUrl);
        free(absUrl);
        return out;
    }
    return rel;
}

static std::string GetDescriptionBaseUrl(IXML_Document *descDoc, const std::string &location) {
    std::string urlBase = GetElementText(descDoc, "URLBase");
    if (!urlBase.empty()) {
        return urlBase;
    }
    return location;
}

struct InitIfCandidate {
    std::string name;
    int score;
};

static bool IsPreferredAndroidLanIf(const char *ifName) {
    if (!ifName || !ifName[0]) return false;
    return strncmp(ifName, "wlan", 4) == 0 ||
           strncmp(ifName, "wifi", 4) == 0 ||
           strncmp(ifName, "eth", 3) == 0 ||
           strncmp(ifName, "en", 2) == 0;
}

static std::string SelectInitInterface() {
    struct ifaddrs *ifap = nullptr;
    std::vector<InitIfCandidate> candidates;
    std::string bestIfName;
    int bestScore = -1;

    if (getifaddrs(&ifap) != 0 || !ifap) {
        return "";
    }

    for (struct ifaddrs *ifa = ifap; ifa != nullptr; ifa = ifa->ifa_next) {
        if (!ifa->ifa_name || !ifa->ifa_addr || ifa->ifa_addr->sa_family != AF_INET) {
            continue;
        }
        if ((ifa->ifa_flags & IFF_LOOPBACK) ||
            !(ifa->ifa_flags & IFF_UP) ||
            !(ifa->ifa_flags & IFF_MULTICAST)) {
            continue;
        }

        char ipBuf[INET_ADDRSTRLEN] = {0};
        const sockaddr_in *sa = reinterpret_cast<const sockaddr_in *>(ifa->ifa_addr);
        if (!inet_ntop(AF_INET, &sa->sin_addr, ipBuf, sizeof(ipBuf))) {
            continue;
        }

        int score = 100;
        if (IsPreferredAndroidLanIf(ifa->ifa_name)) {
            score += 1000;
        }

        bool replaced = false;
        for (InitIfCandidate &candidate : candidates) {
            if (candidate.name == ifa->ifa_name) {
                if (score > candidate.score) {
                    candidate.score = score;
                }
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            InitIfCandidate candidate;
            candidate.name = ifa->ifa_name;
            candidate.score = score;
            candidates.push_back(candidate);
        }
        if (score > bestScore) {
            bestScore = score;
            bestIfName = ifa->ifa_name;
        }
    }

    freeifaddrs(ifap);
    return bestIfName;
}

static std::string PickAndroidInitInterface() {
    std::string ifName = SelectInitInterface();
    if (!ifName.empty()) {
        return ifName;
    }

    static const char *kFallbackIfNames[] = {
            "wlan0",
            "eth0",
            "en0",
            "ap0"
    };
    for (const char *fallback : kFallbackIfNames) {
        if (if_nametoindex(fallback) != 0) {
            return fallback;
        }
    }
    return "";
}

// 从设备描述 XML 中解析服务 controlURL
static bool ParseServiceControlUrl(IXML_Document *descDoc, const char *serviceType,
                                   const std::string &baseUrl, std::string &controlURL) {
    IXML_NodeList *services = ixmlDocument_getElementsByTagName(descDoc, (char *)"service");
    if (!services) return false;

    bool found = false;
    unsigned long len = ixmlNodeList_length(services);
    for (unsigned long i = 0; i < len; i++) {
        IXML_Element *svc = (IXML_Element *)ixmlNodeList_item(services, i);
        if (!svc) continue;

        IXML_NodeList *types = ixmlElement_getElementsByTagName(svc, (char *)"serviceType");
        if (!types) continue;
        IXML_Node *typeNode = ixmlNodeList_item(types, 0);
        std::string type;
        if (typeNode) {
            IXML_Node *t = ixmlNode_getFirstChild(typeNode);
            if (t && ixmlNode_getNodeValue(t)) type = ixmlNode_getNodeValue(t);
        }
        ixmlNodeList_free(types);

        if (type == serviceType) {
            IXML_NodeList *ctrls = ixmlElement_getElementsByTagName(svc, (char *)"controlURL");
            if (ctrls) {
                IXML_Node *cNode = ixmlNodeList_item(ctrls, 0);
                if (cNode) {
                    IXML_Node *c = ixmlNode_getFirstChild(cNode);
                    if (c && ixmlNode_getNodeValue(c)) {
                        controlURL = ResolveUrl(baseUrl, ixmlNode_getNodeValue(c));
                        found = true;
                    }
                }
                ixmlNodeList_free(ctrls);
            }
            if (found) break;
        }
    }
    ixmlNodeList_free(services);
    return found;
}

static int SendActionSync(const char *controlURL, const char *serviceType,
                          const char *actionName,
                          const char **paramNames, const char **paramVals, int paramCount,
                          IXML_Document **respDoc) {
    if (respDoc) *respDoc = nullptr;
    if (g_clientHandle == -1 || !controlURL || !serviceType || !actionName || !respDoc) return -1;

    IXML_Document *actionDoc = nullptr;
    for (int i = 0; i < paramCount; i++) {
        int r = UpnpAddToAction(&actionDoc, actionName, serviceType,
                                paramNames[i], paramVals[i]);
        if (r != UPNP_E_SUCCESS) {
            LOGE("UpnpAddToAction failed: %d", r);
            if (actionDoc) ixmlDocument_free(actionDoc);
            return r;
        }
    }
    if (!actionDoc && paramCount == 0) {
        actionDoc = UpnpMakeAction(actionName, serviceType, 0, nullptr);
    }

    int rc = UpnpSendAction(g_clientHandle, controlURL, serviceType, nullptr, actionDoc, respDoc);
    if (actionDoc) ixmlDocument_free(actionDoc);
    return rc;
}

// 处理搜索到的设备
static void HandleDiscovery(const UpnpDiscovery *d_event) {
    const char *location = UpnpDiscovery_get_Location_cstr(d_event);
    const char *udn = UpnpDiscovery_get_DeviceID_cstr(d_event);
    const char *type = UpnpDiscovery_get_DeviceType_cstr(d_event);
    int expires = UpnpDiscovery_get_Expires(d_event);

    if (!location || !udn || !type) return;

    // 只关心 MediaRenderer
    if (strstr(type, "MediaRenderer") == nullptr) {
        return;
    }

    IXML_Document *descDoc = nullptr;
    int rc = UpnpDownloadXmlDoc(location, &descDoc);
    if (rc != UPNP_E_SUCCESS || !descDoc) {
        LOGE("Download desc failed: %d, loc=%s", rc, location);
        return;
    }

    DeviceInfo dev;
    dev.udn = udn;
    dev.location = location;
    dev.friendlyName = GetElementText(descDoc, "friendlyName");
    if (dev.friendlyName.empty()) {
        dev.friendlyName = udn;
    }
    dev.deviceAbilityRaw = GetElementInnerXml(descDoc, "DevCapExt");

    const std::string baseUrl = GetDescriptionBaseUrl(descDoc, location);

    ParseServiceControlUrl(descDoc, AV_TRANSPORT_TYPE, baseUrl, dev.avTransportControlURL);
    ParseServiceControlUrl(descDoc, RENDERING_CONTROL_TYPE, baseUrl, dev.renderingControlControlURL);

    ixmlDocument_free(descDoc);

    if (dev.avTransportControlURL.empty()) {
        LOGI("Device %s has no AVTransport, skip", dev.friendlyName.c_str());
        return;
    }

    {
        std::lock_guard<std::mutex> lk(g_deviceMutex);
        g_deviceMap[dev.udn] = dev;
    }
    LOGI("Device found: %s @ %s", dev.friendlyName.c_str(), dev.avTransportControlURL.c_str());
    NotifyDeviceFound(dev, expires);
}

// ------------------ UPnP 回调 ------------------

static int UpnpCallback(Upnp_EventType eventType, const void *event, void *cookie) {
    switch (eventType) {
        case UPNP_DISCOVERY_SEARCH_RESULT:
        case UPNP_DISCOVERY_ADVERTISEMENT_ALIVE: {
            HandleDiscovery((const UpnpDiscovery *)event);
            break;
        }
        case UPNP_DISCOVERY_ADVERTISEMENT_BYEBYE: {
            const UpnpDiscovery *d = (const UpnpDiscovery *)event;
            const char *udn = UpnpDiscovery_get_DeviceID_cstr(d);
            if (udn) {
                {
                    std::lock_guard<std::mutex> lk(g_deviceMutex);
                    g_deviceMap.erase(udn);
                }
                NotifyDeviceLost(udn);
            }
            break;
        }
        case UPNP_CONTROL_ACTION_COMPLETE: {
            const UpnpActionComplete *a = (const UpnpActionComplete *)event;
            int err = UpnpActionComplete_get_ErrCode(a);
            const char *actionName = (const char *)cookie;
            std::string name = actionName ? actionName : "Action";
            std::string msg = (err == UPNP_E_SUCCESS) ? "success" : "failed";
            NotifyActionResult(name, err, msg);
            if (actionName) free((void *)actionName);
            break;
        }
        default:
            break;
    }
    return 0;
}

// 适配 Upnp_FunPtr (void * event 签名)
static int UpnpCallbackAdapter(Upnp_EventType eventType, void *event, void *cookie) {
    return UpnpCallback(eventType, (const void *)event, cookie);
}

// ------------------ JNI 导出函数 ------------------

extern "C" {

JNIEXPORT jint JNICALL
Java_com_example_upnpdemo_UpnpNative_nativeInit(JNIEnv *env, jobject thiz, jobject callback) {
    env->GetJavaVM(&g_jvm);

    if (g_callbackObj) {
        env->DeleteGlobalRef(g_callbackObj);
        g_callbackObj = nullptr;
    }
    g_callbackObj = env->NewGlobalRef(callback);
    jclass cls = env->GetObjectClass(callback);
    g_onDeviceFoundMethod = env->GetMethodID(cls, "onDeviceFound",
                                             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V");
    g_onDeviceLostMethod = env->GetMethodID(cls, "onDeviceLost",
                                            "(Ljava/lang/String;)V");
    g_onActionResultMethod = env->GetMethodID(cls, "onActionResult",
                                              "(Ljava/lang/String;ILjava/lang/String;)V");

    const std::string preferredIfName = PickAndroidInitInterface();
    int rc = UpnpInit2(preferredIfName.empty() ? nullptr : preferredIfName.c_str(), 0);
    if (rc != UPNP_E_SUCCESS) {
        LOGE("UpnpInit2 failed: %d", rc);
        return rc;
    }
    UpnpSetMaxJobsTotal(200);

    rc = UpnpRegisterClient(UpnpCallbackAdapter, nullptr, &g_clientHandle);
    if (rc != UPNP_E_SUCCESS) {
        LOGE("UpnpRegisterClient failed: %d", rc);
        UpnpFinish();
        return rc;
    }
    LOGI("UPnP initialized, handle=%d, ifName=%s, IP=%s", g_clientHandle,
         preferredIfName.empty() ? "<auto>" : preferredIfName.c_str(),
         UpnpGetServerIpAddress() ? UpnpGetServerIpAddress() : "?");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_example_upnpdemo_UpnpNative_nativeFinish(JNIEnv *env, jobject thiz) {
    if (g_clientHandle != -1) {
        UpnpUnRegisterClient(g_clientHandle);
        g_clientHandle = -1;
    }
    UpnpFinish();

    if (g_callbackObj) {
        env->DeleteGlobalRef(g_callbackObj);
        g_callbackObj = nullptr;
    }
    {
        std::lock_guard<std::mutex> lk(g_deviceMutex);
        g_deviceMap.clear();
    }
    LOGI("UPnP finished");
}

JNIEXPORT jint JNICALL
Java_com_example_upnpdemo_UpnpNative_nativeSearch(JNIEnv *env, jobject thiz, jint timeoutSec) {
    if (g_clientHandle == -1) return -1;
    {
        std::lock_guard<std::mutex> lk(g_deviceMutex);
        g_deviceMap.clear();
    }
    int rc = UpnpSearchAsync(g_clientHandle, timeoutSec, MEDIA_RENDERER_TYPE, nullptr);
    LOGI("Search started, timeout=%d, rc=%d", timeoutSec, rc);
    return rc;
}

// 通用异步 Action 调用
static int SendAction(const char *controlURL, const char *serviceType,
                      const char *actionName,
                      const char **paramNames, const char **paramVals, int paramCount) {
    if (g_clientHandle == -1 || !controlURL) return -1;

    IXML_Document *actionDoc = nullptr;
    for (int i = 0; i < paramCount; i++) {
        int r = UpnpAddToAction(&actionDoc, actionName, serviceType,
                                paramNames[i], paramVals[i]);
        if (r != UPNP_E_SUCCESS) {
            LOGE("UpnpAddToAction failed: %d", r);
            if (actionDoc) ixmlDocument_free(actionDoc);
            return r;
        }
    }
    if (!actionDoc && paramCount == 0) {
        actionDoc = UpnpMakeAction(actionName, serviceType, 0, nullptr);
    }

    char *cookie = strdup(actionName);
    int rc = UpnpSendActionAsync(g_clientHandle, controlURL, serviceType,
                                 nullptr, actionDoc, UpnpCallbackAdapter, cookie);
    if (rc != UPNP_E_SUCCESS) {
        LOGE("UpnpSendActionAsync failed: %d", rc);
        free(cookie);
    }
    if (actionDoc) ixmlDocument_free(actionDoc);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_example_upnpdemo_UpnpNative_nativeSetAVTransportURI(JNIEnv *env, jobject thiz,
                                                             jstring jControlURL, jstring jUri) {
    const char *controlURL = env->GetStringUTFChars(jControlURL, nullptr);
    const char *uri = env->GetStringUTFChars(jUri, nullptr);

    const char *names[] = {"InstanceID", "CurrentURI", "CurrentURIMetaData"};
    const char *vals[] = {"0", uri, ""};
    int rc = SendAction(controlURL, AV_TRANSPORT_TYPE, "SetAVTransportURI",
                        names, vals, 3);

    env->ReleaseStringUTFChars(jControlURL, controlURL);
    env->ReleaseStringUTFChars(jUri, uri);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_example_upnpdemo_UpnpNative_nativePlay(JNIEnv *env, jobject thiz, jstring jControlURL) {
    const char *controlURL = env->GetStringUTFChars(jControlURL, nullptr);
    const char *names[] = {"InstanceID", "Speed"};
    const char *vals[] = {"0", "1"};
    int rc = SendAction(controlURL, AV_TRANSPORT_TYPE, "Play", names, vals, 2);
    env->ReleaseStringUTFChars(jControlURL, controlURL);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_example_upnpdemo_UpnpNative_nativePause(JNIEnv *env, jobject thiz, jstring jControlURL) {
    const char *controlURL = env->GetStringUTFChars(jControlURL, nullptr);
    const char *names[] = {"InstanceID"};
    const char *vals[] = {"0"};
    int rc = SendAction(controlURL, AV_TRANSPORT_TYPE, "Pause", names, vals, 1);
    env->ReleaseStringUTFChars(jControlURL, controlURL);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_example_upnpdemo_UpnpNative_nativeStop(JNIEnv *env, jobject thiz, jstring jControlURL) {
    const char *controlURL = env->GetStringUTFChars(jControlURL, nullptr);
    const char *names[] = {"InstanceID"};
    const char *vals[] = {"0"};
    int rc = SendAction(controlURL, AV_TRANSPORT_TYPE, "Stop", names, vals, 1);
    env->ReleaseStringUTFChars(jControlURL, controlURL);
    return rc;
}

JNIEXPORT jint JNICALL
Java_com_example_upnpdemo_UpnpNative_nativeSeek(JNIEnv *env, jobject thiz,
                                                jstring jControlURL, jint targetSec) {
    const char *controlURL = env->GetStringUTFChars(jControlURL, nullptr);
    const std::string target = FormatSecondsToHms((int)targetSec);
    const char *names[] = {"InstanceID", "Unit", "Target"};
    const char *vals[] = {"0", "REL_TIME", target.c_str()};
    int rc = SendAction(controlURL, AV_TRANSPORT_TYPE, "Seek", names, vals, 3);
    env->ReleaseStringUTFChars(jControlURL, controlURL);
    return rc;
}

JNIEXPORT jintArray JNICALL
Java_com_example_upnpdemo_UpnpNative_nativeGetPositionInfo(JNIEnv *env, jobject thiz,
                                                           jstring jControlURL) {
    jint values[3] = {-1, 0, 0};
    const char *controlURL = env->GetStringUTFChars(jControlURL, nullptr);

    const char *names[] = {"InstanceID"};
    const char *vals[] = {"0"};
    IXML_Document *respDoc = nullptr;
    int rc = SendActionSync(controlURL, AV_TRANSPORT_TYPE, "GetPositionInfo",
                            names, vals, 1, &respDoc);
    values[0] = rc;
    if (rc == UPNP_E_SUCCESS && respDoc) {
        values[1] = ParseHmsToSeconds(GetElementText(respDoc, "TrackDuration"));
        values[2] = ParseHmsToSeconds(GetElementText(respDoc, "RelTime"));
    }

    if (respDoc) ixmlDocument_free(respDoc);
    env->ReleaseStringUTFChars(jControlURL, controlURL);

    jintArray result = env->NewIntArray(3);
    if (!result) return nullptr;
    env->SetIntArrayRegion(result, 0, 3, values);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_upnpdemo_UpnpNative_nativeSetVolume(JNIEnv *env, jobject thiz,
                                                     jstring jControlURL, jint volume) {
    const char *controlURL = env->GetStringUTFChars(jControlURL, nullptr);
    char volStr[16];
    snprintf(volStr, sizeof(volStr), "%d", (int)volume);
    const char *names[] = {"InstanceID", "Channel", "DesiredVolume"};
    const char *vals[] = {"0", "Master", volStr};
    int rc = SendAction(controlURL, RENDERING_CONTROL_TYPE, "SetVolume", names, vals, 3);
    env->ReleaseStringUTFChars(jControlURL, controlURL);
    return rc;
}

JNIEXPORT jstring JNICALL
Java_com_example_upnpdemo_UpnpNative_nativeGetServerIp(JNIEnv *env, jobject thiz) {
    const char *ip = UpnpGetServerIpAddress();
    return env->NewStringUTF(ip ? ip : "");
}

} // extern "C"
