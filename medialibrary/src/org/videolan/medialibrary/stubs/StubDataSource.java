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
import org.videolan.medialibrary.interfaces.media.AbstractMediaWrapper;
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

    <T> List<T> secureSublist(List<T> list, int offset, int nbItems) {
        int min = list.size() - 1 < 0 ? 0 : list.size();
        int secureOffset = (offset >= list.size()) && (offset > 0) ? min : offset;
        int end = offset + nbItems;
        int secureEnd = (end >= list.size()) && end > 0 ? min : end;
        return list.subList(secureOffset, secureEnd);
    }

    class MediaComparator implements Comparator<AbstractMediaWrapper> {
        private int sort;

        MediaComparator(int sort) {
            this.sort = sort;
        }

        @Override //TODO checkout if types of sort are verified before being used in native
        public int compare(AbstractMediaWrapper o1, AbstractMediaWrapper o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ALPHA:
                    return o1.getTitle().compareTo(o2.getTitle());
                case SORT_FILENAME:
                    return o1.getFileName().compareTo(o2.getFileName());
                case SORT_DURATION:
                    return (int) (o1.getLength() - o2.getLength());
                case SORT_INSERTIONDATE:
                    return (int) (o1.getTime() - o2.getTime()); // TODO checkout if insertiton <=> time
                case SORT_LASTMODIFICATIONDATE:
                    return (int) (o1.getLastModified() - o2.getLastModified());
                case SORT_ARTIST:
                    return o1.getArtist().compareTo(o2.getArtist());
                default:
                    return 0;
            }
        }
    }

    class ArtistComparator implements Comparator<AbstractArtist> {
        private int sort;

        ArtistComparator(int sort) {
            this.sort = sort;
        }

        @Override
        public int compare(AbstractArtist o1, AbstractArtist o2) {
            switch (sort) {
                case SORT_DEFAULT:
                case SORT_ARTIST: return o1.getTitle().compareTo(o2.getTitle());
                default:
                    return 0;
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

    AtomicLong getUUID() {
        uuid.incrementAndGet();
        return uuid;
    }

    void loadJsonData(String jsonContent) {
        try {
            JSONArray jsonArray = new JSONArray(jsonContent);
            for (int i = 0 ; i < jsonArray.length() ; i++) {
                Log.w(TAG, "discover: " + jsonArray.getJSONObject(i).getString("title"));
                addMediaFromJson(jsonArray.getJSONObject(i));
            }

        } catch (JSONException exception) {
            Log.e(TAG, "discover: " + exception.toString());
        }
    }

    private void addMediaFromJson(JSONObject jsonObject) {
        try {
            AbstractMediaWrapper media = MLServiceLocator.getAbstractMediaWrapper(
                    getUUID(),
                    jsonObject.getString("mrl"),
                    0L,
                    jsonObject.getLong("length"),
                    0,
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
                    true
            );
            if (jsonObject.getString("genre").equals("")) {
                addVideo(media);
            } else {
                addAudio(media,
                        "",
                        jsonObject.getInt("year"),
                        jsonObject.getInt("album_duration"),
                        jsonObject.getInt("track_total")
                );
            }
        } catch (JSONException exception) {
            Log.e(TAG, "addMediaFromJson: failed to load json: " + exception.toString());
        }
    }

    private void addArtistSecure(AbstractArtist newArtist) {
        for (AbstractArtist artist : mArtists) {
            if (artist.getTitle().equals(newArtist.getTitle())) {
                return;
            }
        }
        mArtists.add(newArtist);
    }

    private void addGenreSecure(AbstractGenre newGenre) {
        for (AbstractGenre genre : mGenres) {
            if (genre.getTitle().equals(newGenre.getTitle()))
                return;
        }
        mGenres.add(newGenre);
    }

    private void addAlbumSecure(AbstractAlbum newAlbum) {
        for (AbstractAlbum album : mAlbums) {
            if (album.getTitle().equals(newAlbum.getTitle()))
                return;
        }
        mAlbums.add(newAlbum);
    }

    private void addAudio(AbstractMediaWrapper media, String shortBio, int releaseYear, int albumDuration, int trackTotal) {
        addFolders(media);
        mAudioMediaWrappers.add(media);
        AbstractArtist artist = MLServiceLocator.getAbstractArtist(getUUID().longValue(), media.getArtist(), shortBio, media.getArtworkMrl(), "");
        addArtistSecure(artist);
        AbstractArtist albumArtist = null;
        if (!media.getArtist().equals(media.getAlbumArtist()) && !media.getArtist().isEmpty()) {
            albumArtist = MLServiceLocator.getAbstractArtist(getUUID().longValue(), media.getAlbumArtist(), "", media.getArtworkMrl(), "");
            addArtistSecure(albumArtist);
        }
        AbstractAlbum album;
        if (albumArtist != null && !albumArtist.getTitle().isEmpty())
            album = MLServiceLocator.getAbstractAlbum(getUUID().longValue(), media.getAlbum(), releaseYear,
                    media.getArtworkMrl(), albumArtist.getTitle(),
                    albumArtist.getId(), trackTotal, albumDuration);
        else
            album = MLServiceLocator.getAbstractAlbum(getUUID().longValue(), media.getAlbum(), releaseYear,
                    media.getArtworkMrl(), artist.getTitle(),
                    artist.getId(), trackTotal, albumDuration);
        addAlbumSecure(album);
        addGenreSecure(MLServiceLocator.getAbstractGenre(getUUID().longValue(), media.getGenre()));
    }

    private void addVideo(AbstractMediaWrapper media) {
        addFolders(media);
        mVideoMediaWrappers.add(media);
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
                mFolders.add(MLServiceLocator.getAbstractFolder(getUUID().longValue(), name, mrl));
            }
        }
    }
}
