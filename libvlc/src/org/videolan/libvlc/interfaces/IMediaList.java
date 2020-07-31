package org.videolan.libvlc.interfaces;

import android.os.Handler;

public interface IMediaList extends IVLCObject<IMediaList.Event> {
    class Event extends AbstractVLCEvent {

        public static final int ItemAdded = 0x200;
        //public static final int WillAddItem            = 0x201;
        public static final int ItemDeleted = 0x202;
        //public static final int WillDeleteItem         = 0x203;
        public static final int EndReached = 0x204;

        /**
         * In case of ItemDeleted, the media will be already released. If it's released, cached
         * attributes are still available (like {@link IMedia#getUri()}}).
         */
        public final IMedia media;
        private final boolean retain;
        public final int index;

        public Event(int type, IMedia media, boolean retain, int index) {
            super(type);
            if (retain && (media == null || !media.retain()))
                throw new IllegalStateException("invalid media reference");
            this.media = media;
            this.retain = retain;
            this.index = index;
        }

        @Override
        public void release() {
            if (retain)
                media.release();
        }
    }

    interface EventListener extends AbstractVLCEvent.Listener<IMediaList.Event> {
    }

    void setEventListener(EventListener listener, Handler handler);

    /**
     * Get the number of Media.
     */
    int getCount();

    /**
     * Get a Media at specified index.
     *
     * @param index index of the media
     * @return Media hold by MediaList. This Media should be released with {@link #release()}.
     */
    IMedia getMediaAt(int index);

    boolean isLocked();
}
