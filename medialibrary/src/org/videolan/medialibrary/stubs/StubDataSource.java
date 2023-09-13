package org.videolan.medialibrary.stubs;

import static org.videolan.medialibrary.interfaces.Medialibrary.SORT_ALBUM;
import static org.videolan.medialibrary.interfaces.Medialibrary.SORT_ALPHA;
import static org.videolan.medialibrary.interfaces.Medialibrary.SORT_ARTIST;
import static org.videolan.medialibrary.interfaces.Medialibrary.SORT_DEFAULT;
import static org.videolan.medialibrary.interfaces.Medialibrary.SORT_DURATION;
import static org.videolan.medialibrary.interfaces.Medialibrary.SORT_FILENAME;
import static org.videolan.medialibrary.interfaces.Medialibrary.SORT_INSERTIONDATE;
import static org.videolan.medialibrary.interfaces.Medialibrary.SORT_LASTMODIFICATIONDATE;
import static org.videolan.medialibrary.interfaces.Medialibrary.SORT_RELEASEDATE;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.interfaces.media.Album;
import org.videolan.medialibrary.interfaces.media.Artist;
import org.videolan.medialibrary.interfaces.media.Folder;
import org.videolan.medialibrary.interfaces.media.Genre;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;
import org.videolan.medialibrary.interfaces.media.Playlist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class StubDataSource {

    public static final String STUBBED_VIDEO_TITLE = "Invincible";
    public static final String STUBBED_AUDIO_TITLE = "Show Me The Way";

    public static final String STUBBED_VIDEO_EXTENSION = ".mp4";
    public static final String STUBBED_AUDIO_EXTENSION = ".mp3";

    private String TAG = this.getClass().getName();
    ArrayList<MediaWrapper> mVideoMediaWrappers = new ArrayList<>();
    ArrayList<MediaWrapper> mAudioMediaWrappers = new ArrayList<>();
    ArrayList<MediaWrapper> mStreamMediaWrappers = new ArrayList<>();
    ArrayList<MediaWrapper> mHistory = new ArrayList<>();
    ArrayList<Album> mAlbums = new ArrayList<>();
    ArrayList<Artist> mArtists = new ArrayList<>();
    ArrayList<Genre> mGenres = new ArrayList<>();
    ArrayList<Playlist> mPlaylists = new ArrayList<>();
    ArrayList<String> mBannedFolders = new ArrayList<>();
    ArrayList<Folder> mFolders = new ArrayList<>();
    ArrayList<String> mDevices = new ArrayList<>();

    private static String baseMrl = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";

    public static AtomicLong uuid = new AtomicLong(2);

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

    public void setVideoByCount(int count, @Nullable String folder) {
        MediaWrapper media;
        String fileName;

        for (int i = 0; i < count; i++) {
            fileName = i + " - " + STUBBED_VIDEO_TITLE + STUBBED_AUDIO_EXTENSION;
            String mrl = baseMrl + ((folder != null) ? folder + "/" : "") + fileName;
            media = MLServiceLocator.getAbstractMediaWrapper(getUUID(), mrl, -1L, -1F, 18820L, MediaWrapper.TYPE_VIDEO,
                    fileName, fileName, "", "",
                    "", "", 416, 304, "", 0, -2,
                    0, 0, 1509466228L, 0L, true, false, 1970, true, 1683711438317L);
            addVideo(media);
        }
    }

    public void setAudioByCount(int count, @Nullable String folder) {
        mAudioMediaWrappers.clear();
        String fileName;
        MediaWrapper media;

        for (int i = 0; i < count; i++) {
            fileName = i + " - " + STUBBED_AUDIO_TITLE + STUBBED_AUDIO_EXTENSION;
            String mrl = baseMrl + ((folder != null) ? folder + "/" : "") + fileName;
            media = MLServiceLocator.getAbstractMediaWrapper(getUUID(), mrl, -1L, -1F, 280244L, MediaWrapper.TYPE_AUDIO,
                    i + "-Show Me The Way", fileName, "Peter Frampton", "Rock",
                    "Shine On CD2", "Peter Frampton",
                    0, 0, baseMrl + folder + ".jpg",
                    0, -2, 1, 0,
                    1547452796L, 0L, true, false, 1965, true, 1683711438317L);
            addAudio(media, "", 1965, 400, mrl);
        }
    }

    public Folder createFolder(String name) {
        Folder folder = MLServiceLocator.getAbstractFolder(getUUID(), name, baseMrl + name, 1, false);
        mFolders.add(folder);
        return folder;
    }

    public void init() {
        Artist artist = MLServiceLocator.getAbstractArtist(1L, "", "", "", "", 0, 0, 0, false);
        addArtistSecure(artist);
        artist = MLServiceLocator.getAbstractArtist(2L, "", "", "", "", 0, 0, 0, false);
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
        if ((a1.equals(Artist.SpecialRes.UNKNOWN_ARTIST) ||
                a1.equals(Artist.SpecialRes.VARIOUS_ARTISTS)) &&
                (a2.equals(Artist.SpecialRes.UNKNOWN_ARTIST) ||
                        a2.equals(Artist.SpecialRes.VARIOUS_ARTISTS))) {
            return 0;
        } else if (a1.equals(Artist.SpecialRes.UNKNOWN_ARTIST) ||
                a1.equals(Artist.SpecialRes.VARIOUS_ARTISTS)) {
            return -1;
        } else if (a2.equals(Artist.SpecialRes.UNKNOWN_ARTIST) ||
                a2.equals(Artist.SpecialRes.VARIOUS_ARTISTS)) {
            return 1;
        } else {
            return a1.compareTo(a2);
        }
    }

    int compareArtist(Artist a1, Artist a2) {
        return compareArtistStr(a1.getTitle(), a2.getTitle());
    }

    int compareAlbumStr(String a1, String a2) {
        if (a1.equals(Album.SpecialRes.UNKNOWN_ALBUM) &&
                a2.equals(Album.SpecialRes.UNKNOWN_ALBUM)) {
            return 0;
        } else if (a1.equals(Album.SpecialRes.UNKNOWN_ALBUM)) {
            return -1;
        } else if (a2.equals(Album.SpecialRes.UNKNOWN_ALBUM)) {
            return 1;
        } else {
            return a1.compareTo(a2);
        }
    }

    int compareAlbum(Album a1, Album a2) {
        if (a1.getTitle().equals(a2.getTitle())) {
            return compareArtist(a1.retrieveAlbumArtist(), a2.retrieveAlbumArtist());
        } else if (a1.getTitle().equals(Album.SpecialRes.UNKNOWN_ALBUM)) {
            return -1;
        } else if (a2.getTitle().equals(Album.SpecialRes.UNKNOWN_ALBUM)) {
            return 1;
        } else {
            return a1.getTitle().compareTo(a2.getTitle());
        }
    }

    class MediaComparator implements Comparator<MediaWrapper> {
        private int sort;

        MediaComparator(int sort) {
            this.sort = sort;
        }

        @Override //TODO checkout if types of sort are verified before being used in native
        public int compare(MediaWrapper o1, MediaWrapper o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ALPHA:
                    return o1.getTitle().compareTo(o2.getTitle());
                case SORT_FILENAME:
                    return o1.getFileName().compareTo(o2.getFileName());
                case SORT_DURATION:
                    return Long.valueOf(o1.getLength()).compareTo(o2.getLength());
                case SORT_INSERTIONDATE:
                    return Long.valueOf(o1.getTime()).compareTo(o2.getTime());
                case SORT_LASTMODIFICATIONDATE:
                    return Long.valueOf(o1.getLastModified()).compareTo(o2.getLastModified());
                case SORT_ARTIST:
                    return compareArtistStr(o1.getArtist(), o2.getArtist());
                case SORT_ALBUM:
                    return compareAlbumStr(o1.getAlbum(), o2.getAlbum());
                default:
                    return 0;
            }
        }
    }

    class ArtistComparator implements Comparator<Artist> {
        private int sort;

        ArtistComparator(int sort) {
            this.sort = sort;
        }

        @Override
        public int compare(Artist o1, Artist o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ARTIST:
                    return compareArtist(o1, o2);
                default:
                    return 0;
            }
        }
    }

    class AlbumComparator implements Comparator<Album> {
        private int sort;

        AlbumComparator(int sort) {
            this.sort = sort;
        }

        @Override
        public int compare(Album o1, Album o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ALPHA:
                case SORT_ALBUM:
                    return compareAlbum(o1, o2);
                case SORT_RELEASEDATE:
                    return Integer.valueOf(o1.getReleaseYear()).compareTo(o2.getReleaseYear());
                case SORT_DURATION:
                    return Long.valueOf(o1.getDuration()).compareTo(o2.getDuration());
                default:
                    return 0;
            }
        }
    }

    class GenreComparator implements Comparator<Genre> {
        private int sort;

        GenreComparator(int sort) {
            this.sort = sort;
        }

        @Override
        public int compare(Genre o1, Genre o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ALPHA:
                    return o1.getTitle().compareTo(o2.getTitle());
                default:
                    return 0;
            }
        }
    }

    class PlaylistComparator implements Comparator<Playlist> {
        private int sort;

        PlaylistComparator(int sort) {
            this.sort = sort;
        }

        @Override
        public int compare(Playlist o1, Playlist o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ALPHA:
                    return o1.getTitle().compareTo(o2.getTitle());
                case SORT_DURATION:
                    return 0; //TODO WTF is there a duration attribute
                default:
                    return 0;
            }
        }
    }

    class FolderComparator implements Comparator<Folder> {
        private int sort;

        FolderComparator(int sort) {
            this.sort = sort;
        }

        @Override
        public int compare(Folder o1, Folder o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ALPHA:
                    return o1.getTitle().compareTo(o2.getTitle());
                default:
                    return 0;
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

    Album[] sortAlbum(List<Album> arrayList, int sort, boolean desc) {
        List<Album> array = new ArrayList<>(arrayList);
        Collections.sort(array, new AlbumComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new Album[0]);
    }

    Artist[] sortArtist(List<Artist> arrayList, int sort, boolean desc) {
        List<Artist> array = new ArrayList<>(arrayList);
        Collections.sort(array, new ArtistComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new Artist[0]);
    }

    Genre[] sortGenre(List<Genre> arrayList, int sort, boolean desc) {
        List<Genre> array = new ArrayList<>(arrayList);
        Collections.sort(array, new GenreComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new Genre[0]);
    }

    Playlist[] sortPlaylist(List<Playlist> arrayList, int sort, boolean desc) {
        List<Playlist> array = new ArrayList<>(arrayList);
        Collections.sort(array, new PlaylistComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new Playlist[0]);
    }

    Folder[] sortFolder(List<Folder> arrayList, int sort, boolean desc) {
        List<Folder> array = new ArrayList<>(arrayList);
        Collections.sort(array, new FolderComparator(sort));
        if (desc)
            Collections.reverse(array);
        return array.toArray(new Folder[0]);
    }

    public long getUUID() {
        return uuid.incrementAndGet();
    }

    void loadJsonData(String jsonContent) {
        try {
            JSONArray jsonArray = new JSONArray(jsonContent);
            for (int i = 0; i < jsonArray.length(); i++) {
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
                    getUUID(),
                    jsonObject.getString("mrl"),
                    -1L,
                    -1F,
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
                    false,
                    jsonObject.getInt("release_date"),
                    true,
                    1683711438317L
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

    private void addArtistSecure(Artist newArtist) {
        if (newArtist.getTitle().isEmpty())
            return;
        for (Artist artist : mArtists) {
            if (artist.getTitle().equals(newArtist.getTitle()))
                return;
        }
        mArtists.add(newArtist);
    }

    private void addGenreSecure(Genre newGenre) {
        if (newGenre.getTitle().isEmpty())
            return;
        for (Genre genre : mGenres) {
            if (genre.getTitle().equals(newGenre.getTitle()))
                return;
        }
        mGenres.add(newGenre);
    }

    private void addAlbumSecure(Album newAlbum) {
        if (newAlbum.getTitle().isEmpty())
            return;
        for (Album album : mAlbums) {
            if (album.getTitle().equals(newAlbum.getTitle()) &&
                    album.retrieveAlbumArtist().getTitle().equals(newAlbum.retrieveAlbumArtist().getTitle()))
                return;
        }
        mAlbums.add(newAlbum);
    }

    private Artist getArtistFromName(String name) {
        if (name.isEmpty())
            return null;
        for (Artist artist : mArtists) {
            if (artist.getTitle().equals(name))
                return artist;
        }
        return null;
    }

    private Album getAlbumFromName(String albumName, long artistID) {
        if (albumName.equals(""))
            albumName = Album.SpecialRes.UNKNOWN_ALBUM;
        for (Album album : mAlbums) {
            if (album.getTitle().equals(albumName) && album.retrieveAlbumArtist().getId() == artistID) {
                return album;
            }
        }
        return null;
    }

    private void raiseAlbumDuration(Album album, long duration) {
        for (int i = 0; i < mAlbums.size(); i++) {
            Album item = mAlbums.get(i);
            Artist artist = item.retrieveAlbumArtist();
            if (item.getTitle().equals(album.getTitle()) &&
                    item.retrieveAlbumArtist().getTitle().equals(artist.getTitle())) {
                mAlbums.set(i, MLServiceLocator.getAbstractAlbum(
                        album.getId(),
                        album.getTitle(),
                        album.getReleaseYear(),
                        album.getArtworkMrl(),
                        artist.getTitle(),
                        artist.getId(),
                        album.getTracksCount(),
                        album.getPresentTracksCount(),
                        album.getDuration() + duration,
                        album.isFavorite()));
                break;
            }
        }
    }

    private String getArtistName(String albumArtist, String artist) {
        if ((albumArtist == null || artist == null) || albumArtist.isEmpty() && artist.isEmpty())
            return Artist.SpecialRes.UNKNOWN_ARTIST;
        if (!albumArtist.isEmpty())
            return albumArtist;
        return artist;
    }

    private String getAlbumName(String name) {
        if (name == null || name.isEmpty()) {
            return Album.SpecialRes.UNKNOWN_ALBUM;
        }
        return name;
    }

    private void addAudio(MediaWrapper media, String shortBio, int releaseYear, int trackTotal, String mrl) {
        addFolders(media);
        String albumArtistName = getArtistName(media.getAlbumArtist(), media.getArtist());
        Artist albumArtist = getArtistFromName(albumArtistName);
        if (albumArtist == null) {
            albumArtist = MLServiceLocator.getAbstractArtist(getUUID(), albumArtistName,
                    "", media.getArtworkMrl(), "", 0, trackTotal, trackTotal, false);
            addArtistSecure(albumArtist);
        }
        if (media.getArtist().isEmpty()) {
            media.setArtist(albumArtistName);
        } else if (!media.getArtist().equals(albumArtistName)) {
            Artist artist = getArtistFromName(media.getArtist());
            if (artist == null) {
                artist = MLServiceLocator.getAbstractArtist(getUUID(), media.getArtist(),
                        "", media.getArtworkMrl(), "", 1, trackTotal, trackTotal, false);
                addArtistSecure(artist);
            }
        }
        String albumName = getAlbumName(media.getAlbum());
        Album album = getAlbumFromName(albumName, albumArtist.getId());
        if (album == null) {
            album = MLServiceLocator.getAbstractAlbum(getUUID(), albumName, releaseYear,
                    media.getArtworkMrl(), albumArtist.getTitle(),
                    albumArtist.getId(), trackTotal, trackTotal, 0, false);
            addAlbumSecure(album);
        }
        raiseAlbumDuration(album, (int) media.getLength());
        Genre genre = MLServiceLocator.getAbstractGenre(getUUID(), media.getGenre(), false);
        addGenreSecure(genre);
        MediaWrapper newMedia = MLServiceLocator.getAbstractMediaWrapper(
                media.getId(),
                mrl,
                -1L,
                -1F,
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
                false,
                releaseYear,
                true,
                1683711438317L
        );
        mAudioMediaWrappers.add(newMedia);
    }

    private void addVideo(MediaWrapper media) {
        addFolders(media);
        mVideoMediaWrappers.add(media);
    }

    public MediaWrapper addMediaWrapper(String mrl, String title, int type) {
        MediaWrapper media = MLServiceLocator.getAbstractMediaWrapper(getUUID(), mrl, -1L, -1F, 280224L, type,
                title, title, "Artisto", "Jazz", "XYZ CD1", "", 0, 0, baseMrl + title, -2,
                1, 1, 0, 1547452796L, 0L, true, false, 0, true, 1683711438317L);
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
        for (Folder folder : mFolders) {
            results.add(folder.getTitle());
        }
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
                mFolders.add(MLServiceLocator.getAbstractFolder(getUUID(), name, mrl, 1, false));
            }
        }
    }
}
