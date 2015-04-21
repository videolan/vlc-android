/*****************************************************************************
 * std_logger.c
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

#include <stdbool.h>
#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>
#include <unistd.h>

#include <android/log.h>

#include "std_logger.h"

struct std_logger
{
    const char *TAG;
    int stop_pipe[2];
    int stdout_pipe[2];
    int stderr_pipe[2];
    int old_stdout, old_stderr;

    pthread_t thread;
};

static ssize_t
std_logger_Print(std_logger *sys, int fd, int prio)
{
    char buf[1024 + 1];
    ssize_t ret;

    ret = read(fd, buf, 1024);
    if (ret <= 0)
        return ret;

    buf[ret] = '\0';
    __android_log_print(prio, sys->TAG, "%s", buf);

    return ret;
}

static void *
std_logger_Thread(void *arg)
{
    std_logger *sys = arg;

    while (true)
    {
        fd_set rfds;
        int ret, nfds;

        FD_ZERO(&rfds);

        FD_SET(sys->stop_pipe[0], &rfds);
        nfds = sys->stop_pipe[0];

        FD_SET(sys->stdout_pipe[0], &rfds);
        if (sys->stdout_pipe[0] > nfds)
            nfds = sys->stdout_pipe[0];

        FD_SET(sys->stderr_pipe[0], &rfds);
        if (sys->stderr_pipe[0] > nfds)
            nfds = sys->stderr_pipe[0];

        ret = select(nfds + 1, &rfds, NULL, NULL, NULL);

        if (ret == -1)
            break;
        else if (ret == 0)
            continue;

        if (FD_ISSET(sys->stop_pipe[0], &rfds))
            break;

        if (FD_ISSET(sys->stdout_pipe[0], &rfds)
         && std_logger_Print(sys, sys->stdout_pipe[0], ANDROID_LOG_DEBUG) <= 0)
            break;

        if (FD_ISSET(sys->stderr_pipe[0], &rfds)
         && std_logger_Print(sys, sys->stderr_pipe[0], ANDROID_LOG_ERROR) <= 0)
            break;
    }
    return NULL;
}

static void
ClosePipe(int *pipe)
{
    if (pipe[0] != -1)
    {
        close(pipe[0]);
        pipe[0] = -1;
    }
    if (pipe[1] != -1)
    {
        close(pipe[1]);
        pipe[1] = -1;
    }
}

std_logger *
std_logger_Open(const char *TAG)
{
    std_logger *sys = NULL;

    sys = calloc(1, sizeof (std_logger));
    if (!sys)
        return NULL;

    sys->TAG = TAG;
    sys->stop_pipe[0] = sys->stop_pipe[1] =
    sys->stdout_pipe[0] = sys->stdout_pipe[1] =
    sys->old_stdout = sys->old_stderr = -1;

    /* save the old stdout/stderr fd to restore it when logged is closed */
    sys->old_stdout = dup(STDOUT_FILENO);
    sys->old_stderr = dup(STDERR_FILENO);
    if (sys->old_stdout == -1 || sys->old_stderr == -1)
        goto bailout;

    /* duplicate stdout */
    if (pipe(sys->stdout_pipe) == -1)
        goto bailout;
    if (dup2(sys->stdout_pipe[1], STDOUT_FILENO) == -1)
        goto bailout;

    /* duplicate stderr */
    if (pipe(sys->stderr_pipe) == -1)
        goto bailout;
    if (dup2(sys->stderr_pipe[1], STDERR_FILENO) == -1)
        goto bailout;

    /* pipe to signal the thread to stop */
    if (pipe(sys->stop_pipe) == -1)
        goto bailout;

    if (pthread_create(&sys->thread, NULL, std_logger_Thread, sys))
    {
        ClosePipe(sys->stop_pipe);
        goto bailout;
    }

    return sys;
bailout:
    std_logger_Close(sys);
    return NULL;
}

void
std_logger_Close(std_logger *sys)
{
    if (sys->stop_pipe[1] != -1)
    {
        write(sys->stop_pipe[1], '\0', 1);
        close(sys->stop_pipe[1]);
        sys->stop_pipe[1] = -1;
        pthread_join(sys->thread, NULL);
    }
    ClosePipe(sys->stop_pipe);
    ClosePipe(sys->stdout_pipe);
    ClosePipe(sys->stderr_pipe);

    if (sys->old_stdout != -1 && sys->old_stderr != -1) {
        dup2(sys->old_stdout, STDOUT_FILENO);
        dup2(sys->old_stderr, STDERR_FILENO);
        close(sys->old_stdout);
        close(sys->old_stderr);
        sys->old_stdout = sys->old_stderr = -1;
    }
    free(sys);
}
