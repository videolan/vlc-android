/*****************************************************************************
 * libvlcjni.c
 *****************************************************************************
 * Copyright © 2012 Rafaël Carré
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

#include <sys/linux-syscalls.h>
#include <errno.h>

int eventfd(unsigned int initval, int flags)
{
    int ret;
    int syscall_nr = __NR_eventfd2;

    asm(
    "mov    r0, %[initval]      \n\t"
    "mov    r1, %[flags]        \n\t"
    "mov    r7, %[nr]           \n\t"
    "svc    #0                  \n\t"
    "mov    %[ret], r0          \n\t"
    : [ret] "=r" (ret)
    : [initval] "r" (initval), [flags] "r" (flags), [nr] "r" (syscall_nr)
    : "r7"
    );

    if (ret < 0) {
        errno = -ret;
        return -1;
    }

    return ret;
}
