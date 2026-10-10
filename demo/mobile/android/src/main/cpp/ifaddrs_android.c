// Copyright (c) 2015 The Android Open Source Project. All rights reserved.
// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: BSD-2-Clause
//
// This file has been modified by ByteDance Ltd. and/or its affiliates. on 2026-06-18
//
// Original file was released under BSD-2-Clause, with the full license text
// available at https://android.googlesource.com/platform/bionic/+/master/libc/bionic/ifaddrs.cpp
//
// This modified file is released under the same license.

/*
 * ifaddrs_android.c - getifaddrs/freeifaddrs 的 NETLINK 实现
 *
 * 仅在 __ANDROID_API__ < 24 时启用。
 * 参考：POSIX getifaddrs(3)、Linux rtnetlink(7)。
 */

#if defined(__ANDROID__) && (!defined(__ANDROID_API__) || __ANDROID_API__ < 24)

#include "ifaddrs_android.h"

#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/ioctl.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <net/if.h>
#include <netpacket/packet.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <linux/if_addr.h>
#include <linux/if_link.h>

/* 24 -> NLMSG_BUFSIZE，与内核推荐一致 */
#define NLMSG_BUF_SIZE 8192

struct ifaddrs_storage {
    struct ifaddrs ifa;
    union {
        struct sockaddr     sa;
        struct sockaddr_in  s4;
        struct sockaddr_in6 s6;
        struct sockaddr_ll  sll;
    } addr;
    union {
        struct sockaddr     sa;
        struct sockaddr_in  s4;
        struct sockaddr_in6 s6;
    } netmask;
    union {
        struct sockaddr     sa;
        struct sockaddr_in  s4;
        struct sockaddr_in6 s6;
    } ifu;
    char name[IFNAMSIZ + 1];
};

static int append_ifaddr_storage(struct ifaddrs_storage *st,
                                 struct ifaddrs **head,
                                 struct ifaddrs **tail) {
    if (!st) return -1;
    if (*head == NULL) *head = &st->ifa;
    else (*tail)->ifa_next = &st->ifa;
    *tail = &st->ifa;
    return 0;
}

static int netlink_send(int fd, int type) {
    struct {
        struct nlmsghdr  nlh;
        struct rtgenmsg  rtg;
    } req;
    memset(&req, 0, sizeof(req));
    req.nlh.nlmsg_len = NLMSG_LENGTH(sizeof(struct rtgenmsg));
    req.nlh.nlmsg_type = type;
    req.nlh.nlmsg_flags = NLM_F_DUMP | NLM_F_REQUEST;
    req.nlh.nlmsg_pid = 0;
    req.nlh.nlmsg_seq = type;
    req.rtg.rtgen_family = AF_UNSPEC;

    struct sockaddr_nl sa;
    memset(&sa, 0, sizeof(sa));
    sa.nl_family = AF_NETLINK;

    struct iovec iov = { &req, req.nlh.nlmsg_len };
    struct msghdr msg;
    memset(&msg, 0, sizeof(msg));
    msg.msg_name = &sa;
    msg.msg_namelen = sizeof(sa);
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    return sendmsg(fd, &msg, 0);
}

static void prefix_to_netmask(int family, unsigned int prefix_len, void *netmask) {
    unsigned char *p = (unsigned char *)netmask;
    int total = (family == AF_INET) ? 4 : 16;
    unsigned int i;
    for (i = 0; i < (unsigned)total; i++) {
        if (prefix_len >= 8) { p[i] = 0xff; prefix_len -= 8; }
        else if (prefix_len > 0) { p[i] = (unsigned char)(0xff << (8 - prefix_len)); prefix_len = 0; }
        else p[i] = 0;
    }
}

/* 根据 ifindex 在已生成的链表中查找对应 ifa_name */
static const char *lookup_ifname(struct ifaddrs *head, unsigned int idx, char ibuf[IFNAMSIZ]) {
    char *name = if_indextoname(idx, ibuf);
    return name ? name : "";
}

static int parse_newlink(struct nlmsghdr *hdr, struct ifaddrs **head, struct ifaddrs **tail) {
    struct ifinfomsg *ifi = (struct ifinfomsg *)NLMSG_DATA(hdr);
    int len = (int)(hdr->nlmsg_len - NLMSG_LENGTH(sizeof(*ifi)));
    struct rtattr *rta;

    struct ifaddrs_storage *st = (struct ifaddrs_storage *)calloc(1, sizeof(*st));
    if (!st) return -1;
    st->ifa.ifa_flags = ifi->ifi_flags;

    for (rta = IFLA_RTA(ifi); RTA_OK(rta, len); rta = RTA_NEXT(rta, len)) {
        switch (rta->rta_type) {
            case IFLA_IFNAME: {
                size_t n = RTA_PAYLOAD(rta);
                if (n > IFNAMSIZ) n = IFNAMSIZ;
                memcpy(st->name, RTA_DATA(rta), n);
                st->name[n] = 0;
                st->ifa.ifa_name = st->name;
                break;
            }
            case IFLA_ADDRESS: {
                size_t n = RTA_PAYLOAD(rta);
                struct sockaddr_ll *sll = &st->addr.sll;
                sll->sll_family = AF_PACKET;
                sll->sll_ifindex = ifi->ifi_index;
                sll->sll_halen = (unsigned char)(n > sizeof(sll->sll_addr) ? sizeof(sll->sll_addr) : n);
                memcpy(sll->sll_addr, RTA_DATA(rta), sll->sll_halen);
                st->ifa.ifa_addr = &st->addr.sa;
                break;
            }
            default: break;
        }
    }

    if (st->ifa.ifa_name == NULL) {
        if_indextoname((unsigned)ifi->ifi_index, st->name);
        st->ifa.ifa_name = st->name;
    }

    return append_ifaddr_storage(st, head, tail);
}

static int parse_newaddr(struct nlmsghdr *hdr, struct ifaddrs **head, struct ifaddrs **tail) {
    struct ifaddrmsg *ifa = (struct ifaddrmsg *)NLMSG_DATA(hdr);
    int len = (int)(hdr->nlmsg_len - NLMSG_LENGTH(sizeof(*ifa)));
    struct rtattr *rta;

    if (ifa->ifa_family != AF_INET && ifa->ifa_family != AF_INET6) return 0;

    struct ifaddrs_storage *st = (struct ifaddrs_storage *)calloc(1, sizeof(*st));
    if (!st) return -1;
    st->ifa.ifa_flags = ifa->ifa_flags;

    /* 先用 ifindex 查 name */
    if_indextoname((unsigned)ifa->ifa_index, st->name);
    st->ifa.ifa_name = st->name;
    {
        int s = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
        if (s >= 0) {
            struct ifreq ifr;
            memset(&ifr, 0, sizeof(ifr));
            strncpy(ifr.ifr_name, st->name, IFNAMSIZ - 1);
            if (ioctl(s, SIOCGIFFLAGS, &ifr) == 0) {
                st->ifa.ifa_flags = (unsigned int)ifr.ifr_flags;
            }
            close(s);
        }
    }

    void *addr_ptr = NULL;
    void *local_ptr = NULL;
    void *broad_ptr = NULL;

    for (rta = IFA_RTA(ifa); RTA_OK(rta, len); rta = RTA_NEXT(rta, len)) {
        switch (rta->rta_type) {
            case IFA_LABEL: {
                size_t n = RTA_PAYLOAD(rta);
                if (n > IFNAMSIZ) n = IFNAMSIZ;
                memcpy(st->name, RTA_DATA(rta), n);
                st->name[n] = 0;
                break;
            }
            case IFA_ADDRESS:  addr_ptr  = RTA_DATA(rta); break;
            case IFA_LOCAL:    local_ptr = RTA_DATA(rta); break;
            case IFA_BROADCAST: broad_ptr = RTA_DATA(rta); break;
            default: break;
        }
    }

    /* 对 P2P 接口 (如 ppp0)，IFA_LOCAL 才是本地地址，IFA_ADDRESS 是对端 */
    void *final_addr = local_ptr ? local_ptr : addr_ptr;

    if (ifa->ifa_family == AF_INET) {
        st->addr.s4.sin_family = AF_INET;
        if (final_addr) memcpy(&st->addr.s4.sin_addr, final_addr, 4);
        st->ifa.ifa_addr = &st->addr.sa;

        st->netmask.s4.sin_family = AF_INET;
        prefix_to_netmask(AF_INET, ifa->ifa_prefixlen, &st->netmask.s4.sin_addr);
        st->ifa.ifa_netmask = &st->netmask.sa;

        if (broad_ptr) {
            st->ifu.s4.sin_family = AF_INET;
            memcpy(&st->ifu.s4.sin_addr, broad_ptr, 4);
            st->ifa.ifa_ifu.ifu_broadaddr = &st->ifu.sa;
        }
    } else { /* AF_INET6 */
        st->addr.s6.sin6_family = AF_INET6;
        if (final_addr) memcpy(&st->addr.s6.sin6_addr, final_addr, 16);
        st->addr.s6.sin6_scope_id = (ifa->ifa_scope == 0) ? 0 : ifa->ifa_index;
        st->ifa.ifa_addr = &st->addr.sa;

        st->netmask.s6.sin6_family = AF_INET6;
        prefix_to_netmask(AF_INET6, ifa->ifa_prefixlen, &st->netmask.s6.sin6_addr);
        st->ifa.ifa_netmask = &st->netmask.sa;
    }

    return append_ifaddr_storage(st, head, tail);
}

static int ioctl_collect_ipv4(struct ifaddrs **head, struct ifaddrs **tail) {
    int s = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (s < 0) return -1;

    char buf[16384];
    struct ifconf ifc;
    memset(&ifc, 0, sizeof(ifc));
    ifc.ifc_len = (int)sizeof(buf);
    ifc.ifc_buf = buf;
    if (ioctl(s, SIOCGIFCONF, &ifc) < 0) {
        close(s);
        return -1;
    }

    const char *ptr = buf;
    const char *end = buf + ifc.ifc_len;
    int count = 0;
    while (ptr + (int)sizeof(struct ifreq) <= end) {
        const struct ifreq *ifr = (const struct ifreq *)ptr;
        ptr += sizeof(struct ifreq);

        if (ifr->ifr_addr.sa_family != AF_INET) {
            continue;
        }

        struct ifaddrs_storage *st = (struct ifaddrs_storage *)calloc(1, sizeof(*st));
        if (!st) {
            close(s);
            return -1;
        }

        strncpy(st->name, ifr->ifr_name, IFNAMSIZ);
        st->name[IFNAMSIZ] = '\0';
        st->ifa.ifa_name = st->name;

        struct ifreq req;
        memset(&req, 0, sizeof(req));
        strncpy(req.ifr_name, st->name, IFNAMSIZ - 1);

        memcpy(&st->addr.s4, &ifr->ifr_addr, sizeof(struct sockaddr_in));
        st->addr.s4.sin_family = AF_INET;
        st->ifa.ifa_addr = &st->addr.sa;

        if (ioctl(s, SIOCGIFFLAGS, &req) == 0) {
            st->ifa.ifa_flags = (unsigned int)req.ifr_flags;
        }

        memset(&req, 0, sizeof(req));
        strncpy(req.ifr_name, st->name, IFNAMSIZ - 1);
        if (ioctl(s, SIOCGIFNETMASK, &req) == 0) {
            memcpy(&st->netmask.s4, &req.ifr_netmask, sizeof(struct sockaddr_in));
            st->netmask.s4.sin_family = AF_INET;
            st->ifa.ifa_netmask = &st->netmask.sa;
        }

        if (st->ifa.ifa_flags & IFF_BROADCAST) {
            memset(&req, 0, sizeof(req));
            strncpy(req.ifr_name, st->name, IFNAMSIZ - 1);
            if (ioctl(s, SIOCGIFBRDADDR, &req) == 0) {
                memcpy(&st->ifu.s4, &req.ifr_broadaddr, sizeof(struct sockaddr_in));
                st->ifu.s4.sin_family = AF_INET;
                st->ifa.ifa_ifu.ifu_broadaddr = &st->ifu.sa;
            }
        }

        if (append_ifaddr_storage(st, head, tail) < 0) {
            free(st);
            close(s);
            return -1;
        }
        ++count;
    }

    close(s);
    return 0;
}

static int netlink_collect(int fd, int type, struct ifaddrs **head, struct ifaddrs **tail) {
    if (netlink_send(fd, type) < 0) return -1;

    char buf[NLMSG_BUF_SIZE];
    while (1) {
        ssize_t n = recv(fd, buf, sizeof(buf), 0);
        if (n < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (n == 0) return 0;

        struct nlmsghdr *nh = (struct nlmsghdr *)buf;
        for (; NLMSG_OK(nh, (size_t)n); nh = NLMSG_NEXT(nh, n)) {
            if (nh->nlmsg_type == NLMSG_DONE) return 0;
            if (nh->nlmsg_type == NLMSG_ERROR) return -1;
            if (type == RTM_GETLINK && nh->nlmsg_type == RTM_NEWLINK) {
                if (parse_newlink(nh, head, tail) < 0) return -1;
            } else if (type == RTM_GETADDR && nh->nlmsg_type == RTM_NEWADDR) {
                if (parse_newaddr(nh, head, tail) < 0) return -1;
            }
        }
    }
}

int getifaddrs(struct ifaddrs **result) {
    if (!result) { errno = EINVAL; return -1; }
    *result = NULL;
    struct ifaddrs *fallbackTail = NULL;

    int fd = socket(AF_NETLINK, SOCK_RAW | SOCK_CLOEXEC, NETLINK_ROUTE);
    if (fd < 0) return -1;

    struct sockaddr_nl sa;
    memset(&sa, 0, sizeof(sa));
    sa.nl_family = AF_NETLINK;
    if (bind(fd, (struct sockaddr *)&sa, sizeof(sa)) < 0) {
        close(fd);
        return ioctl_collect_ipv4(result, &fallbackTail);
    }

    struct ifaddrs *head = NULL, *tail = NULL;
    if (netlink_collect(fd, RTM_GETLINK, &head, &tail) < 0) goto err;
    if (netlink_collect(fd, RTM_GETADDR, &head, &tail) < 0) goto err;

    close(fd);
    *result = head;
    return 0;

err:
    close(fd);
    freeifaddrs(head);
    *result = NULL;
    fallbackTail = NULL;
    return ioctl_collect_ipv4(result, &fallbackTail);
}

void freeifaddrs(struct ifaddrs *ifa) {
    while (ifa) {
        struct ifaddrs *next = ifa->ifa_next;
        /* 本实现里 ifa 就是 ifaddrs_storage 的首字段，直接 free 包头即可 */
        free(ifa);
        ifa = next;
    }
}

/* 消除 "empty translation unit" 警告 */
typedef int ifaddrs_android_tu_not_empty;

#else

/* API >= 24：本文件不生成任何符号，避免重复定义 */
typedef int ifaddrs_android_stub_tu;

#endif /* __ANDROID_API__ < 24 */
