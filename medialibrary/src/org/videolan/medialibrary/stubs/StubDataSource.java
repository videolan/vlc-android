package org.videolan.medialibrary.stubs;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.interfaces.media.AbstractAlbum;
import org.videolan.medialibrary.interfaces.media.AbstractArtist;
import org.videolan.medialibrary.interfaces.media.AbstractFolder;
import org.videolan.medialibrary.interfaces.media.AbstractGenre;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.AbstractPlaylist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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

    private String TAG = this.getClass().getName();
    ArrayList<MediaWrapper> mVideoMediaWrappers = new ArrayList<>();
    ArrayList<MediaWrapper> mAudioMediaWrappers = new ArrayList<>();
    ArrayList<MediaWrapper> mStreamMediaWrappers = new ArrayList<>();
    ArrayList<MediaWrapper> mHistory = new ArrayList<>();
    ArrayList<AbstractAlbum> mAlbums = new ArrayList<>();
    ArrayList<AbstractArtist> mArtists = new ArrayList<>();
    ArrayList<AbstractGenre> mGenres = new ArrayList<>();
    ArrayList<AbstractPlaylist> mPlaylists = new ArrayList<>();
    ArrayList<String> mBannedFolders = new ArrayList<>();
    ArrayList<AbstractFolder> mFolders = new ArrayList<>();
    ArrayList<String> mDevices = new ArrayList<>();

    private static String baseMrl = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";

    private static AtomicLong uuid = new AtomicLong(2);

    private static StubDataSource mInstance = null;

    public static StubDataSource getInstance() {
        if (mInstance == null) {
            mInstance = new StubDataSource();
        }
        return mInstance;
    }

    private StubDataSource() {
    }


    public void resetData() {
        mFolders.clear();
        mVideoMediaWrappers.clear();
        mAudioMediaWrappers.clear();
        mStreamMediaWrappers.clear();
        mHistory.clear();
        mPlaylists.clear();
        mAlbums.clear();
        mArtists.clear();
        mGenres.clear();
        mBannedFolders.clear();
        mDevices.clear();
    }

    public void init() {
        AbstractArtist artist = MLServiceLocator.getAbstractArtist(1L, "", "", "", "");
        addArtistSecure(artist);
        artist = MLServiceLocator.getAbstractArtist(2L, "", "", "", "");
        addArtistSecure(artist);
    }

    <T> List<T> secureSublist(List<T> list, int offset, int nbItems) {
        int min = list.size() - 1 < 0 ? 0 : list.size();
        int secureOffset = (offset >= list.size()) && (offset > 0) ? min : offset;
        int end = offset + nbItems;
        int secureEnd = (end >= list.size()) && end > 0 ? min : end;
        return list.subList(secureOffset, secureEnd);
    }

    int compareArtistStr(String a1, String a2) {
        if ((a1.equals(AbstractArtist.SpecialRes.UNKNOWN_ARTIST) ||
                a1.equals(AbstractArtist.SpecialRes.VARIOUS_ARTISTS)) &&
                (a2.equals(AbstractArtist.SpecialRes.UNKNOWN_ARTIST) ||
                        a2.equals(AbstractArtist.SpecialRes.VARIOUS_ARTISTS))) {
            return 0;
        }
        else if (a1.equals(AbstractArtist.SpecialRes.UNKNOWN_ARTIST)||
                a1.equals(AbstractArtist.SpecialRes.VARIOUS_ARTISTS)) {
            return -1;
        } else if (a2.equals(AbstractArtist.SpecialRes.UNKNOWN_ARTIST)||
                a2.equals(AbstractArtist.SpecialRes.VARIOUS_ARTISTS)) {
            return 1;
        } else {
            return a1.compareTo(a2);
        }
    }

    int compareArtist(AbstractArtist a1, AbstractArtist a2) {
        return compareArtistStr(a1.getTitle(), a2.getTitle());
    }

    int compareAlbumStr(String a1, String a2) {
        if (a1.equals(AbstractAlbum.SpecialRes.UNKNOWN_ALBUM) &&
                a2.equals(AbstractAlbum.SpecialRes.UNKNOWN_ALBUM)) {
            return 0;
        }
        else if (a1.equals(AbstractAlbum.SpecialRes.UNKNOWN_ALBUM)) {
            return -1;
        } else if (a2.equals(AbstractAlbum.SpecialRes.UNKNOWN_ALBUM)) {
            return 1;
        } else {
            return a1.compareTo(a2);
        }
    }

    int compareAlbum(AbstractAlbum a1, AbstractAlbum a2) {
        if (a1.getTitle().equals(a2.getTitle())) {
            return compareArtist(a1.getAlbumArtist(), a2.getAlbumArtist());
        } else if (a1.getTitle().equals(AbstractAlbum.SpecialRes.UNKNOWN_ALBUM)) {
            return -1;
        } else if (a2.getTitle().equals(AbstractAlbum.SpecialRes.UNKNOWN_ALBUM)) {
            return 1;
        } else {
            return a1.getTitle().compareTo(a2.getTitle());
        }
    }

    class MediaComparator implements Comparator<MediaWrapper> {
        private int sort;
        MediaComparator(int sort) { this.sort = sort; }

        @Override //TODO checkout if types of sort are verified before being used in native
        public int compare(MediaWrapper o1, MediaWrapper o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ALPHA: return o1.getTitle().compareTo(o2.getTitle());
                case SORT_FILENAME :return o1.getFileName().compareTo(o2.getFileName());
                case SORT_DURATION: return Long.valueOf(o1.getLength()).compareTo(o2.getLength());
                case SORT_INSERTIONDATE: return Long.valueOf(o1.getTime()).compareTo(o2.getTime());
                case SORT_LASTMODIFICATIONDATE: return Long.valueOf(o1.getLastModified()).compareTo(o2.getLastModified());
                case SORT_ARTIST: return compareArtistStr(o1.getArtist(), o2.getArtist());
                case SORT_ALBUM: return compareAlbumStr(o1.getAlbum(), o2.getAlbum());
                default: return 0;
            }
        }
    }

    class ArtistComparator implements Comparator<AbstractArtist> {
        private int sort;
        ArtistComparator(int sort) { this.sort = sort; }

        @Override
        public int compare(AbstractArtist o1, AbstractArtist o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ARTIST: return compareArtist(o1, o2);
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
                case SORT_ALPHA:
                case SORT_ALBUM: return compareAlbum(o1, o2);
                case SORT_RELEASEDATE: return Integer.valueOf(o1.getReleaseYear()).compareTo(o2.getReleaseYear());
                case SORT_DURATION: return Long.valueOf(o1.getDuration()).compareTo(o2.getDuration());
                default: return 0;
            }
        }
    }

    class GenreComparator implements Comparator<AbstractGenre> {
        private int sort;
        GenreComparator(int sort) { this.sort = sort; }

        @Override
        public int compare(AbstractGenre o1, AbstractGenre o2) {
            switch (sort) {
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

    MediaWrapper[] sortMedia(List<MediaWrapper> arrayList, int sort, boolean desc) {
        List<MediaWrapper> array = new ArrayList<>(arrayList);
        Collections.sort(array, new MediaComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new MediaWrapper[0]);
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

    AtomicLong getUUID() {
        uuid.incrementAndGet();
        return uuid;
    }

    void loadJsonData(String jsonContent) {
        try {
            JSONArray jsonArray = new JSONArray(jsonContent);
            for (int i = 0 ; i < jsonArray.length() ; i++) {
                Log.d(TAG, "discover: " + jsonArray.getJSONObject(i).getString("title"));
                addMediaFromJson(jsonArray.getJSONObject(i));
            }
        } catch (JSONException exception) {
            Log.e(TAG, "discover: " + exception.toString());
        }
    }

    private void addMediaFromJson(JSONObject jsonObject) {
        try {
            int type = jsonObject.getInt("type");
            MediaWrapper media = MLServiceLocator.getAbstractMediaWrapper(
                    getUUID().longValue(),
                    jsonObject.getString("mrl"),
                    0L,
                    jsonObject.getLong("length"),
                    type,
                    jsonObject.getString("title"),
                    jsonObject.getString("filename"),
                    jsonObject.getString("artist"),
                    jsonObject.getString("genre"),
                    jsonObject.getString("album"),
                    jsonObject.getString("album_artist"),
                    jsonObject.getInt("width"),
                    jsonObject.getInt("height"),
                    jsonObject.getString("artwork_url"),
                    jsonObject.getInt("audio"),
                    jsonObject.getInt("spu"),
                    jsonObject.getInt("track_number"),
                    0,
                    jsonObject.getLong("last_modified"),
                    0L,
                    true,
                    jsonObject.getInt("release_date")
            );
            if (type == MediaWrapper.TYPE_VIDEO) {
                addVideo(media);
            } else {
                addAudio(media,
                        "",
                        jsonObject.getInt("year"),
                        jsonObject.getInt("track_total"),
                        jsonObject.getString("mrl")
                );
            }
        } catch (JSONException exception) {
            Log.e(TAG, "addMediaFromJson: failed to load json: " + exception.toString());
        }
    }

    private void addArtistSecure(AbstractArtist newArtist) {
        if (newArtist.getTitle().isEmpty())
            return;
        for (AbstractArtist artist : mArtists) {
            if (artist.getTitle().equals(newArtist.getTitle()))
                return;
        }
        mArtists.add(newArtist);
    }

    private void addGenreSecure(AbstractGenre newGenre) {
        if (newGenre.getTitle().isEmpty())
            return;
        for (AbstractGenre genre : mGenres) {
            if (genre.getTitle().equals(newGenre.getTitle()))
                return;
        }
        mGenres.add(newGenre);
    }

    private void addAlbumSecure(AbstractAlbum newAlbum) {
        if (newAlbum.getTitle().isEmpty())
            return;
        for (AbstractAlbum album : mAlbums) {
            if (album.getTitle().equals(newAlbum.getTitle()) &&
                    album.getAlbumArtist().getTitle().equals(newAlbum.getAlbumArtist().getTitle()))
                return;
        }
        mAlbums.add(newAlbum);
    }

    private AbstractArtist getArtistFromName(String name) {
        if (name.isEmpty())
            return null;
        for (AbstractArtist artist : mArtists) {
            if (artist.getTitle().equals(name))
                return artist;
        }
        return null;
    }

    private AbstractAlbum getAlbumFromName(String albumName, long artistID) {
        if (albumName.equals(""))
            albumName = AbstractAlbum.SpecialRes.UNKNOWN_ALBUM;
        for (AbstractAlbum album : mAlbums) {
            if (album.getTitle().equals(albumName) && album.getAlbumArtist().getId() == artistID) {
                return album;
            }
        }
        return null;
    }

    private void raiseAlbumDuration(AbstractAlbum album, long duration) {
        for (int i = 0 ; i < mAlbums.size() ; i++) {
            AbstractAlbum item = mAlbums.get(i);
            AbstractArtist artist = item.getAlbumArtist();
            if (item.getTitle().equals(album.getTitle()) &&
                    item.getAlbumArtist().getTitle().equals(artist.getTitle())) {
                mAlbums.set(i, MLServiceLocator.getAbstractAlbum(
                        album.getId(),
                        album.getTitle(),
                        album.getReleaseYear(),
                        album.getArtworkMrl(),
                        artist.getTitle(),
                        artist.getId(),
                        album.getTracksCount(),
                        album.getDuration() + duration));
                break;
            }
        }
    }

    private String getArtistName(String albumArtist, String artist) {
        if ((albumArtist == null || artist == null) || albumArtist.isEmpty() && artist.isEmpty())
            return AbstractArtist.SpecialRes.UNKNOWN_ARTIST;
        if (!albumArtist.isEmpty())
            return albumArtist;
        return artist;
    }

    private String getAlbumName(String name) {
        if (name == null || name.isEmpty()) {
            return AbstractAlbum.SpecialRes.UNKNOWN_ALBUM;
        }
        return name;
    }

    private void addAudio(MediaWrapper media, String shortBio, int releaseYear, int trackTotal, String mrl) {
        addFolders(media);
        String albumArtistName = getArtistName(media.getAlbumArtist(), media.getArtist());
        AbstractArtist albumArtist = getArtistFromName(albumArtistName);
        if (albumArtist == null) {
            albumArtist = MLServiceLocator.getAbstractArtist(getUUID().longValue(), albumArtistName,
                    "", media.getArtworkMrl(), "");
            addArtistSecure(albumArtist);
        }
        if (media.getArtist().isEmpty()) {
            media.setArtist(albumArtistName);
        } else if (!media.getArtist().equals(albumArtistName)) {
            AbstractArtist artist = getArtistFromName(media.getArtist());
            if (artist == null) {
                artist = MLServiceLocator.getAbstractArtist(getUUID().longValue(), media.getArtist(),
                        "", media.getArtworkMrl(), "");
                addArtistSecure(artist);
            }
        }
        String albumName = getAlbumName(media.getAlbum());
        AbstractAlbum album = getAlbumFromName(albumName, albumArtist.getId());
        if (album == null) {
            album = MLServiceLocator.getAbstractAlbum(getUUID().longValue(), albumName, releaseYear,
                        media.getArtworkMrl(), albumArtist.getTitle(),
                        albumArtist.getId(), trackTotal, 0);
            addAlbumSecure(album);
        }
        raiseAlbumDuration(album, (int) media.getLength());
        AbstractGenre genre = MLServiceLocator.getAbstractGenre(getUUID().longValue(), media.getGenre());
        addGenreSecure(genre);
        MediaWrapper newMedia = MLServiceLocator.getAbstractMediaWrapper(
                media.getId(),
                mrl,
                0L,
                media.getLength(),
                MediaWrapper.TYPE_AUDIO,
                media.getTitle(),
                media.getFileName(),
                media.getArtist(),
                genre.getTitle(),
                album.getTitle(),
                albumArtist.getTitle(),
                media.getWidth(),
                media.getHeight(),
                media.getArtworkURL(),
                media.getAudioTrack(),
                media.getSpuTrack(),
                media.getTrackNumber(),
                0,
                media.getLastModified(),
                0L,
                true,
                releaseYear
        );
        mAudioMediaWrappers.add(newMedia);
    }

    private void addVideo(MediaWrapper media) {
        addFolders(media);
        mVideoMediaWrappers.add(media);
    }

    public MediaWrapper addMediaWrapper(String mrl, String title, int type) {
        MediaWrapper media = MLServiceLocator.getAbstractMediaWrapper(getUUID().longValue(), mrl, 0L, 280224L, type,
                title, title, "Artisto", "Jazz", "XYZ CD1", "", 0, 0, baseMrl + title, -2,
                1, 1, 0, 1547452796L, 0L, true, 0);
        if (type == MediaWrapper.TYPE_ALL) type = media.getType();
        if (type == MediaWrapper.TYPE_VIDEO) addVideo(media);
        else if (type == MediaWrapper.TYPE_AUDIO) addAudio(media, "", 2018, 12313, mrl);
        return media;
    }

    public MediaWrapper addMediaWrapper(String title, int type) {
        return addMediaWrapper(baseMrl + title, title, type);
    }


    private String[] getFoldersString() {
        ArrayList<String> results = new ArrayList<>();
        for (AbstractFolder folder : mFolders) { results.add(folder.getTitle()); }
        return results.toArray(new String[0]);
    }

    private void addFolders(MediaWrapper media) {
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
                mFolders.add(MLServiceLocator.getAbstractFolder(getUUID().longValue(), name, mrl));
            }
        }
    }
}
