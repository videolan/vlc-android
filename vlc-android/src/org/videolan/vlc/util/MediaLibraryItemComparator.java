package org.videolan.vlc.util;

import org.videolan.medialibrary.media.Album;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;

import java.util.Comparator;
import java.util.Locale;

public class MediaLibraryItemComparator implements Comparator<MediaLibraryItem> {

    public volatile int sortDirection;
    public volatile int sortBy;
    public final static int SORT_BY_TITLE = 0;
    public final static int SORT_BY_LENGTH = 1;
    public final static int SORT_BY_DATE = 2;
    public final static int SORT_BY_NUMBER = 3;
    public final static int ADAPTER_VIDEO = 0;
    public final static int ADAPTER_AUDIO = 1;
    public final static int ADAPTER_FILE = 2;
    private int adapterType;

    public MediaLibraryItemComparator(int adapterType) {
        sortBy = SORT_BY_TITLE;
        sortDirection = 1;
        this.adapterType = adapterType;
    }

    public int sortDirection(int sortby) {
        if (sortBy == sortby)
            return sortDirection;
        else
            return -1;
    }

    public void sortBy(int sortby, int sortdirection) {
        sortBy = sortby;
        sortDirection = sortdirection;
    }

    @Override
    public int compare(MediaLibraryItem item1, MediaLibraryItem item2) {
        if (item1 == null)
            return item2 == null ? 0 : -1;
        else if (item2 == null)
            return 1;

        if (item1.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
            int type1 = ((MediaWrapper)item1).getType();
            int type2 = ((MediaWrapper)item2).getType();
            if (type1 == MediaWrapper.TYPE_DIR && type2 != MediaWrapper.TYPE_DIR)
                return -1;
            else if (type1 != MediaWrapper.TYPE_DIR && type2 == MediaWrapper.TYPE_DIR)
                return 1;
        }
        int compare = 0;

        switch (sortBy) {
            case SORT_BY_TITLE:
                compare = item1.getTitle().toUpperCase(Locale.ENGLISH).compareTo(item2.getTitle().toUpperCase(Locale.ENGLISH));
                break;
            case SORT_BY_LENGTH:
                if (item1.getItemType() == MediaLibraryItem.TYPE_ALBUM) {
                    compare = ((Album)item1).getDuration() >= (((Album)item2).getDuration()) ? 1 : -1;
                } else if (item1.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
                    compare = ((Long) ((MediaWrapper)item1).getLength()).compareTo(((MediaWrapper)item2).getLength());
                }
                break;
            case SORT_BY_DATE:
                if (item1.getItemType() == MediaLibraryItem.TYPE_ALBUM) {
                    compare = ((Album)item1).getReleaseYear() >= ((Album)item2).getReleaseYear() ? 1 : -1;
                } else if (item1.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
                    if (adapterType == ADAPTER_AUDIO) {
                        int date1 = (((MediaWrapper) item1).getDate() == null) ? 0 : Integer.valueOf(((MediaWrapper) item1).getDate());
                        int date2 = (((MediaWrapper) item2).getDate() == null) ? 0 : Integer.valueOf(((MediaWrapper) item2).getDate());
                        compare = date1 >= date2 ? 1 : -1;
                    } else if (adapterType == ADAPTER_FILE || adapterType == ADAPTER_VIDEO)
                        compare = ((Long) ((MediaWrapper)item1).getLastModified()).compareTo(((MediaWrapper)item2).getLastModified());
                }
                break;
            case SORT_BY_NUMBER:
                if (item1.getItemType() == MediaLibraryItem.TYPE_ALBUM) {
                    compare = ((Album)item1).getTracksCount() >= ((Album)item2).getTracksCount() ? 1 : -1;
                }
                break;
        }

        return sortDirection * compare;
    }

    public int getRealSort(int mType){
        int realSort;
        switch (sortBy){
            case SORT_BY_LENGTH:
                realSort = ((mType == MediaLibraryItem.TYPE_ARTIST) || (mType == MediaLibraryItem.TYPE_GENRE) || (mType == MediaLibraryItem.TYPE_PLAYLIST))
                        ? SORT_BY_TITLE : sortBy;
                break;
            case SORT_BY_DATE:
                realSort = ((mType == MediaLibraryItem.TYPE_ARTIST) || (mType == MediaLibraryItem.TYPE_GENRE) || (mType == MediaLibraryItem.TYPE_PLAYLIST))
                        ? SORT_BY_TITLE : sortBy;
                break;
            case SORT_BY_NUMBER:
                realSort = (mType == MediaLibraryItem.TYPE_MEDIA)
                        ? SORT_BY_TITLE : sortBy;
                break;
            default:
                realSort = sortBy;
                break;
        }
        return realSort;
    }

    public static String getYear(MediaLibraryItem media) {
        switch (media.getItemType()) {
            case MediaLibraryItem.TYPE_ALBUM:
                return ((Album)media).getReleaseYear() == 0 ? "-" : String.valueOf(((Album)media).getReleaseYear());
            case MediaLibraryItem.TYPE_MEDIA:
                return (((MediaWrapper)media).getDate() == null) ? "-" : ((MediaWrapper)media).getDate();
            default:
                return "-";
        }
    }

    public static String lengthToCategory(int length) {
        int value;
        if (length == 0)
            return "-";
        if (length < 60000)
            return "< 1 min";
        if (length < 600000) {
            value = (int) Math.floor(length / 60000);
            return String.valueOf(value) + " - " + String.valueOf(value + 1) + " min";
        }
        if (length < 3600000) {
            value = (int) (10 * Math.floor(length / 600000));
            return String.valueOf(value) + " - " + String.valueOf(value + 10) + " min";
        } else {
            value = (int) Math.floor(length / 3600000);
            return String.valueOf(value) + " - " + String.valueOf(value + 1) + " h";
        }
    }

    public static int getLength(MediaLibraryItem media) {
        if (media.getItemType() == MediaLibraryItem.TYPE_ALBUM)
            return ((Album)media).getDuration();
        else if (media.getItemType() == MediaLibraryItem.TYPE_MEDIA)
            return (int) ((MediaWrapper)media).getLength();
        else
            return 0;
    }

    public static int getTracksCount(MediaLibraryItem media) {
        switch (media.getItemType()) {
            case MediaLibraryItem.TYPE_ALBUM:
                return ((Album)media).getTracksCount();
            case MediaLibraryItem.TYPE_PLAYLIST:
                return ((Playlist)media).getTracksCount();
            default:
                return 0;
        }
    }
}
