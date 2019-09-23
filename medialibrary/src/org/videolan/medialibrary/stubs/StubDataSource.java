package org.videolan.medialibrary.stubs;

import android.text.TextUtils;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractFolder;
import org.videolan.medialibrary.interfaces.media.AbstractGenre;
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;
import org.videolan.medialibrary.interfaces.media.AbstractPlaylist;
import org.videolan.medialibrary.media.MediaLibraryItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.videolan.medialibrary.interfaces.AbstractMedialibrary.SORT_ALBUM;
import static org.videolan.medialibrary.interfaces.AbstractMedialibrary.SORT_ALPHA;
import static org.videolan.medialibrary.interfaces.AbstractMedialibrary.SORT_ARTIST;
import static org.videolan.medialibrary.interfaces.AbstractMedialibrary.SORT_DEFAULT;
import static org.videolan.medialibrary.interfaces.AbstractMedialibrary.SORT_DURATION;
import static org.videolan.medialibrary.interfaces.AbstractMedialibrary.SORT_FILENAME;
import static org.videolan.medialibrary.interfaces.AbstractMedialibrary.SORT_INSERTIONDATE;
import static org.videolan.medialibrary.interfaces.AbstractMedialibrary.SORT_LASTMODIFICATIONDATE;
import static org.videolan.medialibrary.interfaces.AbstractMedialibrary.SORT_RELEASEDATE;

public class StubDataSource {

    ArrayList<AbstractMediaWrapper> mVideoMediaWrappers = new ArrayList<>();
    ArrayList<AbstractMediaWrapper> mAudioMediaWrappers = new ArrayList<>();
    ArrayList<AbstractMediaWrapper> mStreamMediaWrappers = new ArrayList<>();
    ArrayList<AbstractMediaWrapper> mHistory = new ArrayList<>();
    ArrayList<AbstractAlbum> mAlbums = new ArrayList<>();
    ArrayList<AbstractArtist> mArtists = new ArrayList<>();
    ArrayList<AbstractGenre> mGenres = new ArrayList<>();
    ArrayList<AbstractPlaylist> mPlaylists = new ArrayList<>();
    ArrayList<String> mBannedFolders = new ArrayList<>();
    ArrayList<AbstractFolder> mFolders = new ArrayList<>();
    ArrayList<String> mDevices = new ArrayList<>();

    private static long uuid = 2;

    private static StubDataSource mInstance = null;

    public static StubDataSource getInstance() {
        if (mInstance == null) {
            mInstance = new StubDataSource();
        }
        return mInstance;
    }

    private StubDataSource() { }

    public void init() {
        String baseMrl = "/storage/emulated/0/Movies/";
        AbstractMediaWrapper media;

        // Video
        String fileName = "058_foar_everywun_frum_boxxy.flv";
        media = MLServiceLocator.getAbstractMediaWrapper(getUUID(), baseMrl + fileName, 0L, 18820L, 0,
                fileName, fileName, "", "",
                "", "", 416, 304, "", 0, -2,
                0, 0, 1509466228L, 0L, true, 0);
        addVideo(media);

        fileName = "FMA - MultiChapter.mkv";
        media = MLServiceLocator.getAbstractMediaWrapper(getUUID(), baseMrl + fileName, 0L, 1467383L, 0,
                "Encoded with MiniCoder", fileName, "", "",
                "", "", 1280, 720, "", 0,
                -2, 0, 0, 1512396147L, 0L, true, 0);
        addVideo(media);

        fileName = "114_My_Heart_Will_Go_On.avi";
        media = MLServiceLocator.getAbstractMediaWrapper(getUUID(), baseMrl + fileName, 0L, 20000L, 0,
                "My Heart Will Go On - Celine Dion", fileName, "", "",
                "", "", 352, 220, "", 0,
                -2, 0, 0, 1509465852L, 0L, true, 0);
        addVideo(media);

        // Audio

        fileName = "01-Show Me The Way.mp3";
        baseMrl = "/storage/emulated/0/Music/Peter Frampton/Shine On - CD2/";
        media = MLServiceLocator.getAbstractMediaWrapper(getUUID(), baseMrl + fileName, 0L, 280244L, 1,
                "01-Show Me The Way", fileName, "Peter Frampton", "Rock",
                "Shine On CD2", "Peter Frampton",
                0, 0, "/storage/emulated/0/Music/Peter Frampton/Shine On - CD2/Folder.jpg",
                0, -2, 1, 0,
                1547452796L, 0L, true, 0);
        addAudio(media, "", 1965, 400);

        fileName = "01-Wind Of Change.mp3";
        baseMrl = "/storage/emulated/0/Music/Peter Frampton/Shine On - CD1/";
        media = MLServiceLocator.getAbstractMediaWrapper(getUUID(), baseMrl + fileName, 0L, 184271L, 1,
                "01-Wind Of Change", fileName, "Peter Frampton", "Rock",
                "Shine On CD1", "Peter Frampton",
                0, 0, "/storage/emulated/0/Music/Peter Frampton/Shine On - CD1/Folder.jpg",
                0, -2, 1, 0,
                1547452786L, 0L, true, 0);
        addAudio(media, "", 1960, 250);

        fileName = "03 Bloody Well Right.wma";
        baseMrl = "/storage/emulated/0/Music/Supertramp/Best of/";
        media = MLServiceLocator.getAbstractMediaWrapper(getUUID(), baseMrl + fileName, 0L, 257199L, 1,
                "Bloody Well Right", fileName, "Supertramp", "Rock",
                "The Autobiography of Supertramp", "Supertramp",
                0, 0, "/storage/emulated/0/Music/Supertramp/Best of/Folder.jpg", 0,
                -2, 3, 0,
                1547452814L, 0L, true, 0);
        addAudio(media, "", 1970, 360);
    }

    <T> List<T> secureSublist(List<T> list, int offset, int nbItems) {
        int min = list.size() - 1 < 0 ? 0 : list.size();
        int secureOffset =  (offset >= list.size()) && (offset > 0) ? min : offset;
        int end = offset + nbItems;
        int secureEnd = (end >= list.size()) && end > 0 ? min : end;
        return list.subList(secureOffset, secureEnd);
    }

    class MediaComparator implements Comparator<AbstractMediaWrapper> {
        private int sort;
        MediaComparator(int sort) { this.sort = sort; }

        @Override //TODO checkout if types of sort are verified before being used in native
        public int compare(AbstractMediaWrapper o1, AbstractMediaWrapper o2) {
            switch(sort) {
                case SORT_DEFAULT:
                case SORT_ALPHA: return o1.getTitle().compareTo(o2.getTitle());
                case SORT_FILENAME :return o1.getFileName().compareTo(o2.getFileName());
                case SORT_DURATION: return (int)(o1.getLength() - o2.getLength());
                case SORT_INSERTIONDATE: return (int)(o1.getTime() - o2.getTime()); // TODO checkout if insertiton <=> time
                case SORT_LASTMODIFICATIONDATE: return (int)(o1.getLastModified() - o2.getLastModified());
                case SORT_ARTIST: return o1.getArtist().compareTo(o2.getArtist());
                default: return 0;
            }
        }
    }

    class ArtistComparator implements Comparator<AbstractArtist> {
        private int sort;
        ArtistComparator(int sort) { this.sort = sort; }

        @Override
        public int compare(AbstractArtist o1, AbstractArtist o2) {
            switch(sort) {
                case SORT_DEFAULT:
                case SORT_ARTIST: return o1.getTitle().compareTo(o2.getTitle());
                default: return 0;
            }
        }
    }

    class AlbumComparator implements Comparator<AbstractAlbum> {
        private int sort;
        AlbumComparator(int sort) { this.sort = sort; }

        @Override
        public int compare(AbstractAlbum o1, AbstractAlbum o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ALBUM: return o1.getTitle().compareTo(o2.getTitle());
                case SORT_RELEASEDATE: return o1.getReleaseYear() - o2.getReleaseYear();
                case SORT_DURATION: return (int)(o1.getDuration() - o2.getDuration());
                default: return 0;
            }
        }
    }

    class GenreComparator implements Comparator<AbstractGenre> {
        private int sort;
        GenreComparator(int sort) { this.sort = sort; }

        @Override
        public int compare(AbstractGenre o1, AbstractGenre o2) {
            switch(sort) {
                case SORT_DEFAULT:
                case SORT_ALPHA: return o1.getTitle().compareTo(o2.getTitle());
                default: return 0;
            }
        }
    }

    class PlaylistComparator implements Comparator<AbstractPlaylist> {
        private int sort;
        PlaylistComparator(int sort) { this.sort = sort; }

        @Override
        public int compare(AbstractPlaylist o1, AbstractPlaylist o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ALPHA: return o1.getTitle().compareTo(o2.getTitle());
                case SORT_DURATION: return 0; //TODO WTF is there a duration attribute
                default: return 0;
            }
        }
    }

    class FolderComparator implements Comparator<AbstractFolder> {
        private int sort;
        FolderComparator(int sort) { this.sort = sort; }

        @Override
        public int compare(AbstractFolder o1, AbstractFolder o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ALPHA: return o1.getTitle().compareTo(o2.getTitle());
                default: return 0;
            }
        }
    }

    AbstractMediaWrapper[] sortMedia(List<AbstractMediaWrapper> arrayList, int sort, boolean desc) {
        List<AbstractMediaWrapper> array = new ArrayList<>(arrayList);
        Collections.sort(array, new MediaComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new AbstractMediaWrapper[0]);
    }

    AbstractAlbum[] sortAlbum(List<AbstractAlbum> arrayList, int sort, boolean desc) {
        List<AbstractAlbum> array = new ArrayList<>(arrayList);
        Collections.sort(array, new AlbumComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new AbstractAlbum[0]);
    }

    AbstractArtist[] sortArtist(List<AbstractArtist> arrayList, int sort, boolean desc) {
        List<AbstractArtist> array = new ArrayList<>(arrayList);
        Collections.sort(array, new ArtistComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new AbstractArtist[0]);
    }

    AbstractGenre[] sortGenre(List<AbstractGenre> arrayList, int sort, boolean desc) {
        List<AbstractGenre> array = new ArrayList<>(arrayList);
        Collections.sort(array, new GenreComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new AbstractGenre[0]);
    }

    AbstractPlaylist[] sortPlaylist(List<AbstractPlaylist> arrayList, int sort, boolean desc) {
        List<AbstractPlaylist> array = new ArrayList<>(arrayList);
        Collections.sort(array, new PlaylistComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new AbstractPlaylist[0]);
    }

    AbstractFolder[] sortFolder(List<AbstractFolder> arrayList, int sort, boolean desc) {
        List<AbstractFolder> array = new ArrayList<>(arrayList);
        Collections.sort(array, new FolderComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new AbstractFolder[0]);
    }

    private boolean checkUuidForMatches(long id) {
        if (id == 1L || id == 2L)
            return true;
        for (MediaLibraryItem item : mVideoMediaWrappers) {
            if (item.getId() == id)
                return true;
        }
        for (MediaLibraryItem item : mAudioMediaWrappers) {
            if (item.getId() == id)
                return true;
        }
        for (MediaLibraryItem item : mAlbums) {
            if (item.getId() == id)
                return true;
        }
        for (MediaLibraryItem item : mArtists) {
            if (item.getId() == id)
                return true;
        }
        for (MediaLibraryItem item : mGenres) {
            if (item.getId() == id)
                return true;
        }
        for (MediaLibraryItem item : mFolders) {
            if (item.getId() == id)
                return true;
        }
        return false;
    }

    long getUUID() {
        uuid++;
        return uuid;
    }

    private void addAudio(AbstractMediaWrapper media, String shortBio, int releaseYear, int albumDuration) {
        addFolders(media);
        mAudioMediaWrappers.add(media);
        AbstractArtist artist = MLServiceLocator.getAbstractArtist(getUUID(), media.getArtist(), shortBio, media.getArtworkMrl(), "");
        mArtists.add(artist);
        AbstractArtist albumArtist = null;
        if (!media.getArtist().equals(media.getAlbumArtist())) {
            albumArtist = MLServiceLocator.getAbstractArtist(getUUID(), media.getAlbumArtist(), "", media.getArtworkMrl(), "");
            mArtists.add(albumArtist);
        }
        AbstractAlbum album;
        if (albumArtist == null)
            album = MLServiceLocator.getAbstractAlbum(getUUID(), media.getAlbum(), releaseYear,
                    media.getArtworkMrl(), artist.getTitle(),
                    artist.getId(), media.getTracks().length, albumDuration);
        else
            album = MLServiceLocator.getAbstractAlbum(getUUID(), media.getAlbum(), releaseYear,
                    media.getArtworkMrl(), albumArtist.getTitle(),
                    albumArtist.getId(), media.getTracks().length, albumDuration);
        mAlbums.add(album);
        ArrayList genreStrings = new ArrayList<>();
//        if (!getGenresString())
        mGenres.add(MLServiceLocator.getAbstractGenre(getUUID(), media.getGenre()));
    }

    private void addVideo(AbstractMediaWrapper media) {
        addFolders(media);
        mVideoMediaWrappers.add(media);
    }

    private String[] getGenresString() {
        ArrayList<String> results = new ArrayList<>();
        for (AbstractGenre genre : mGenres) { results.add(genre.getTitle()); }
        return results.toArray(new String[0]);
    }

    private String[] getFoldersString() {
        ArrayList<String> results = new ArrayList<>();
        for (AbstractFolder folder : mFolders) { results.add(folder.getTitle()); }
        return results.toArray(new String[0]);
    }

    private void addFolders(AbstractMediaWrapper media) {
        String path = media.getUri().getPath();
        if (path == null)
            return;
        String[] folderArray = path.split("/");
        ArrayList<String> newFolders = new ArrayList<>(Arrays.asList(folderArray));
        for (int i = 0; i < newFolders.size(); i++) {
            final String mrl = TextUtils.join("/", secureSublist(newFolders, 0, i));
            ArrayList<String> mlFolders = new ArrayList<>(Arrays.asList(getFoldersString()));
            if (!mlFolders.contains(mrl)) {
                final String name = folderArray[folderArray.length - 1];
                mFolders.add(MLServiceLocator.getAbstractFolder(getUUID(), name, mrl));
            }
        }
    }
}
