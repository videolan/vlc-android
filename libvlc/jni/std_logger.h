/*****************************************************************************
 * std_logger.h
 *****************************************************************************
 * Copyright © 2015 VLC authors, VideoLAN and VideoLabs
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

#ifndef STD_LOGGER_H
#define STD_LOGGER_H

/* By default, stdout/stderr are deactivated, i.e. they are opened on
 * /dev/null. std_logger_Open will duplicate stdout and stderr fd on a write
 * end of a pipe. A thread will be created and will print the read end of the
 * pipe. */

typedef struct std_logger std_logger;

std_logger *std_logger_Open(const char *TAG);
void        std_logger_Close(std_logger *sys);

#endif // STD_LOGGER_H
