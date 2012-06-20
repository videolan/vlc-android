/*****************************************************************************
 * VLCCallbackTask.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
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
package org.videolan.vlc;

/**
 * A small callback helper class to make running callbacks in threads easier
 */
public class VLCCallbackTask implements Runnable {
    private final CallbackListener callback;
    private final Object user;

    /**
     * The callback interface. Implement callback() to run as the main thread,
     * and implement callback_object() to run when the main thread completes.
     */
    public interface CallbackListener {
        public abstract void callback();
        public abstract void callback_object(Object o);
    }

    /**
     * @param _callback The CallbackListener as described above
     * @param _user Any user object you want to pass
     */
    public VLCCallbackTask(CallbackListener _callback, Object _user) {
      this.callback = _callback;
      this.user = _user;
    }

    /**
     * A version of VLCCallbackTask if you are not using the user parameter
     *
     * @param _callback The CallbackListener as described above
     */
    public VLCCallbackTask(CallbackListener _callback) {
        this.callback = _callback;
        this.user = null;
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {
      callback.callback();
      callback.callback_object(user);
    }
}
