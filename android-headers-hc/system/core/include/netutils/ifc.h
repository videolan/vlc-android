/*
 * Copyright 2008, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *
 *     http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

#ifndef _NETUTILS_IFC_H_
#define _NETUTILS_IFC_H_

#include <sys/cdefs.h>
#include <arpa/inet.h>

__BEGIN_DECLS

extern int ifc_init(void);
extern void ifc_close(void);

extern int ifc_get_ifindex(const char *name, int *if_indexp);
extern int ifc_get_hwaddr(const char *name, void *ptr);

extern int ifc_up(const char *name);
extern int ifc_down(const char *name);

extern int ifc_enable(const char *ifname);
extern int ifc_disable(const char *ifname);

extern int ifc_reset_connections(const char *ifname, const int reset_mask);

extern int ifc_get_addr(const char *name, in_addr_t *addr);
extern int ifc_set_addr(const char *name, in_addr_t addr);
extern int ifc_add_address(const char *name, const char *address,
                           int prefixlen);
extern int ifc_del_address(const char *name, const char *address,
                           int prefixlen);
extern int ifc_set_prefixLength(const char *name, int prefixLength);
extern int ifc_set_hwaddr(const char *name, const void *ptr);
extern int ifc_clear_addresses(const char *name);

/* This function is deprecated. Use ifc_add_route instead. */
extern int ifc_add_host_route(const char *name, in_addr_t addr);
extern int ifc_remove_host_routes(const char *name);
extern int ifc_get_default_route(const char *ifname);
/* This function is deprecated. Use ifc_add_route instead */
extern int ifc_set_default_route(const char *ifname, in_addr_t gateway);
/* This function is deprecated. Use ifc_add_route instead */
extern int ifc_create_default_route(const char *name, in_addr_t addr);
extern int ifc_remove_default_route(const char *ifname);
extern int ifc_add_route(const char *name, const char *addr, int prefix_length,
                         const char *gw);
extern int ifc_remove_route(const char *ifname, const char *dst,
                            int prefix_length, const char *gw);
extern int ifc_get_info(const char *name, in_addr_t *addr, int *prefixLength,
                        unsigned *flags);

extern int ifc_configure(const char *ifname, in_addr_t address,
                         uint32_t prefixLength, in_addr_t gateway,
                         in_addr_t dns1, in_addr_t dns2);

__END_DECLS

#endif /* _NETUTILS_IFC_H_ */
