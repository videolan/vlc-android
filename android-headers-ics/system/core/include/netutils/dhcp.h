/*
 * Copyright 2010, The Android Open Source Project
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

#ifndef _NETUTILS_DHCP_H_
#define _NETUTILS_DHCP_H_

#include <sys/cdefs.h>
#include <arpa/inet.h>

__BEGIN_DECLS

extern int do_dhcp(char *iname);
extern int dhcp_do_request(const char *ifname,
                          char *ipaddr,
                          char *gateway,
                          uint32_t *prefixLength,
                          char *dns1,
                          char *dns2,
                          char *server,
                          uint32_t  *lease);
extern int dhcp_stop(const char *ifname);
extern int dhcp_release_lease(const char *ifname);
extern char *dhcp_get_errmsg();

__END_DECLS

#endif /* _NETUTILS_DHCP_H_ */
