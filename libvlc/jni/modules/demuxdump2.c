/*****************************************************************************
 * demuxdump2.c : Pseudo demux module for vlc (with write status)
 *****************************************************************************
 * Copyright (C) 2016 VLC authors and VideoLAN
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

#ifdef HAVE_CONFIG_H
# include "config.h"
#endif

#include <vlc_common.h>
#include <vlc_plugin.h>
#include <vlc_demux.h>
#include <vlc_interrupt.h>
#include <vlc_input.h>
#include <vlc_fs.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

static int  Open(vlc_object_t *);
static void Close (vlc_object_t *);

vlc_module_begin ()
    set_shortname("Dump")
    set_category(CAT_INPUT)
    set_subcategory(SUBCAT_INPUT_DEMUX)
    set_capability("demux", 0)
        change_private()
    add_savefile("demuxdump-file", "stream-demux.dump", NULL, NULL, false)
        change_private()
    set_callbacks(Open, Close)
    add_shortcut("dump2")
vlc_module_end ()

#define READ_ONCE 32768

struct demux_sys_t
{
    void *p_buf;
    int i_out_fd;
    uint64_t i_size;
    uint64_t i_written;
    unsigned int i_level;
};

static void SendEventCache(demux_t *p_demux, float f_value)
{
    vlc_value_t val;
    val.f_float = f_value;
    var_Change(p_demux->p_input, "cache", VLC_VAR_SETVALUE, &val, NULL);
    var_SetInteger(p_demux->p_input, "intf-event", INPUT_EVENT_CACHE);
}

static int Demux(demux_t *p_demux)
{
    demux_sys_t *p_sys = p_demux->p_sys;

    ssize_t i_ret = stream_Read(p_demux->s, p_sys->p_buf, READ_ONCE);

    if (i_ret < 0)
        return -1;
    else if (i_ret == 0)
        return 0;

    size_t i_towrite = i_ret;
    size_t i_written = 0;

    while (i_written < i_towrite)
    {
        i_ret = vlc_write_i11e(p_sys->i_out_fd, p_sys->p_buf + i_written,
                               i_towrite - i_written);
        if (i_ret == -1)
            return -1;
        i_written += i_ret;
    }
    p_sys->i_written += i_written;

    if (p_sys->i_size > 0)
    {
        unsigned int i_level = p_sys->i_written / (p_sys->i_size / (double) 100);
        if (i_level != p_sys->i_level)
        {
            p_sys->i_level = i_level;
            SendEventCache(p_demux, i_level / (float) 100);
        }
    }

    return 1;
}

static int Control(demux_t *p_demux, int i_query, va_list args)
{
    return demux_vaControlHelper(p_demux->s, 0, -1, 0, 1, i_query, args);
}

static int Open(vlc_object_t * p_this)
{
    demux_t *p_demux = (demux_t*)p_this;

    /* Accept only if forced */
    if (!p_demux->b_force)
        return VLC_EGENERIC;

    char *psz_path = var_InheritString(p_demux, "demuxdump-file");
    if (psz_path == NULL)
    {
        msg_Err(p_demux, "no dump file name given");
        return VLC_EGENERIC;
    }

    int i_fd = vlc_open(psz_path, O_CREAT|O_WRONLY);
    free(psz_path);
    if (i_fd == -1)
        return VLC_EGENERIC;

    demux_sys_t *p_sys = malloc(sizeof(*p_sys));
    if (p_sys == NULL)
    {
        vlc_close(i_fd);
        return VLC_ENOMEM;
    }
    p_sys->p_buf = malloc(READ_ONCE);
    if (p_sys->p_buf == NULL)
    {
        vlc_close(i_fd);
        return VLC_ENOMEM;
    }
    if (stream_GetSize(p_demux->s, &p_sys->i_size) != VLC_SUCCESS)
        p_sys->i_size = 0;
    p_sys->i_written = 0;

    p_sys->i_out_fd = i_fd;

    p_demux->p_sys = p_sys;
    p_demux->pf_demux = Demux;
    p_demux->pf_control = Control;
    return VLC_SUCCESS;
}

static void Close(vlc_object_t *p_this)
{
    demux_t *p_demux = (demux_t*)p_this;
    demux_sys_t *p_sys = p_demux->p_sys;

    vlc_close(p_sys->i_out_fd);
    free(p_sys->p_buf);
    free(p_sys);
}
