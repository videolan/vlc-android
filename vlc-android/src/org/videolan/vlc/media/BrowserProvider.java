/*
 * ************************************************************************
 *  BrowserProvider.java
 * *************************************************************************
 *  Copyright © 2016 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *
 *  *************************************************************************
 */

package org.videolan.vlc.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.extensions.ExtensionListing;
import org.videolan.vlc.extensions.ExtensionManagerService;
import org.videolan.vlc.extensions.ExtensionsManager;
import org.videolan.vlc.extensions.api.VLCExtensionItem;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.util.Constants;
import org.videolan.vlc.util.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;


public class BrowserProvider implements ExtensionManagerService.ExtensionManagerActivity {

    private static final String TAG = "VLC/BrowserProvider";

    private static final Bitmap DEFAULT_AUDIO_COVER = BitmapFactory.decodeResource(VLCApplication.getAppResources(), R.drawable.ic_menu_audio);
    private static String BASE_DRAWABLE_URI;

    public static final String ID_ROOT = "ID_ROOT";
    private static final String ID_ARTISTS = "ID_ARTISTS";
    private static final String ID_ALBUMS = "ID_ALBUMS";
    private static final String ID_SONGS = "ID_SONGS";
    private static final String ID_GENRES = "ID_GENRES";
    private static final String ID_PLAYLISTS = "ID_PLAYLISTS";
    private static final String ID_HISTORY = "ID_HISTORY";
    private static final String ID_LAST_ADDED = "ID_RECENT";
    public static final String ALBUM_PREFIX = "album";
    private static final String ARTIST_PREFIX = "artist";
    private static final String GENRE_PREFIX = "genre";
    public static final String PLAYLIST_PREFIX = "playlist";
    private static final String DUMMY = "dummy";
    private static final int MAX_HISTORY_SIZE = 50;
    private static final int MAX_EXTENSION_SIZE = 100;

    // Extensions management
    private static ServiceConnection sExtensionServiceConnection = null;
    private static ExtensionManagerService sExtensionManagerService;
    private static List<MediaBrowserCompat.MediaItem> extensionItems = new ArrayList<>();
    private static Semaphore extensionLock = new Semaphore(0);

    @WorkerThread
    public static List<MediaBrowserCompat.MediaItem> browse(Context context, String parentId) {
        List<MediaBrowserCompat.MediaItem> results = new ArrayList<>();
        MediaLibraryItem[] list = null;
        boolean limitSize = false;
        Resources res = VLCApplication.getAppResources();

        //Extensions
        if (parentId.startsWith(ExtensionsManager.EXTENSION_PREFIX)) {
            if (sExtensionServiceConnection == null) {
                createExtensionServiceConnection(context);
                try {
                    extensionLock.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (sExtensionServiceConnection == null)
                return null;
            String[] data = parentId.split("_");
            int index = Integer.valueOf(data[1]);
            extensionItems.clear();
            if (data.length == 2) {
                //case extension root
                sExtensionManagerService.connectService(index);
            } else {
                //case sub-directory
                String stringId = parentId.replace(ExtensionsManager.EXTENSION_PREFIX + "_" + String.valueOf(index) + "_", "");
                sExtensionManagerService.browse(stringId);
            }
            try {
                extensionLock.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            results = extensionItems;
        } else {

            switch (parentId) {
                case ID_ROOT:
                    MediaDescriptionCompat.Builder item = new MediaDescriptionCompat.Builder();
                    //List of Extensions
                    List<ExtensionListing> extensions = ExtensionsManager.getInstance().getExtensions(context, true);
                    for (int i = 0; i < extensions.size(); i++) {
                        ExtensionListing extension = extensions.get(i);
                        if (extension.androidAutoEnabled()
                                && Settings.INSTANCE.getInstance(context).getBoolean(ExtensionsManager.EXTENSION_PREFIX + "_" + extension.componentName().getPackageName() + "_" + ExtensionsManager.ANDROID_AUTO_SUFFIX, false)) {
                            item.setMediaId(ExtensionsManager.EXTENSION_PREFIX + "_" + String.valueOf(i))
                                    .setTitle(extension.title());

                            int iconRes = extension.menuIcon();
                            Bitmap b = null;
                            Resources extensionRes;
                            if (iconRes != 0) {
                                try {
                                    extensionRes = context.getPackageManager()
                                            .getResourcesForApplication(extension.componentName().getPackageName());
                                    b = BitmapFactory.decodeResource(extensionRes, iconRes);
                                } catch (PackageManager.NameNotFoundException ignored) {}
                            }
                            if (b != null) item.setIconBitmap(b);
                            else try {
                                b = ((BitmapDrawable) context.getPackageManager().getApplicationIcon(extension.componentName().getPackageName())).getBitmap();
                                item.setIconBitmap(b);
                            } catch (PackageManager.NameNotFoundException e) {
                                b = BitmapFactory.decodeResource(res, R.drawable.icon);
                                item.setIconBitmap(b);
                            }
                            results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                        }
                    }
                    if (BASE_DRAWABLE_URI == null) BASE_DRAWABLE_URI = "android.resource://"+context.getPackageName()+"/drawable/";
                    //Last added
                    item = new MediaDescriptionCompat.Builder()
                            .setMediaId(ID_LAST_ADDED)
                            .setTitle(res.getString(R.string.last_added_media))
                            .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_history_normal"));
                    results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                    //History
                    item = new MediaDescriptionCompat.Builder()
                            .setMediaId(ID_HISTORY)
                            .setTitle(res.getString(R.string.history))
                            .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_history_normal"));
                    results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                    //Playlists
                    item.setMediaId(ID_PLAYLISTS)
                            .setTitle(res.getString(R.string.playlists))
                            .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_playlist_normal"));
                    results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                    //Artists
                    item.setMediaId(ID_ARTISTS)
                            .setTitle(res.getString(R.string.artists))
                            .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_artist_normal"));
                    results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                    //Albums
                    item.setMediaId(ID_ALBUMS)
                            .setTitle(res.getString(R.string.albums))
                            .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_album_normal"));
                    results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                    //Songs
                    item.setMediaId(ID_SONGS)
                            .setTitle(res.getString(R.string.songs))
                            .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_audio_normal"));
                    results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                    //Genres
                    item.setMediaId(ID_GENRES)
                            .setTitle(res.getString(R.string.genres))
                            .setIconUri(Uri.parse(BASE_DRAWABLE_URI + "ic_auto_genre_normal"));
                    results.add(new MediaBrowserCompat.MediaItem(item.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                    return results;
                case ID_LAST_ADDED:
                    limitSize = true;
                    list = Medialibrary.getInstance().getRecentAudio();
                    break;
                case ID_HISTORY:
                    limitSize = true;
                    list = Medialibrary.getInstance().lastMediaPlayed();
                    break;
                case ID_ARTISTS:
                    list = Medialibrary.getInstance().getArtists(Settings.INSTANCE.getInstance(context).getBoolean(Constants.KEY_ARTISTS_SHOW_ALL, false));
                    break;
                case ID_ALBUMS:
                    list = Medialibrary.getInstance().getAlbums();
                    break;
                case ID_GENRES:
                    list = Medialibrary.getInstance().getGenres();
                    break;
                case ID_PLAYLISTS:
                    list = Medialibrary.getInstance().getPlaylists();
                    break;
                case ID_SONGS:
                    list = Medialibrary.getInstance().getAudio();
                    break;
                default:
                    String[] idSections = parentId.split("_");
                    Medialibrary ml = Medialibrary.getInstance();
                    long id = Long.parseLong(idSections[1]);
                    switch (idSections[0]) {
                        case ARTIST_PREFIX:
                            list = ml.getArtist(id).getAlbums();
                            break;
                        case GENRE_PREFIX:
                            list = ml.getGenre(id).getAlbums();
                            break;
                    }
            }
            if (list != null) {
                MediaDescriptionCompat.Builder item = new MediaDescriptionCompat.Builder();
                for (MediaLibraryItem libraryItem : list) {
                    if (libraryItem == null || (libraryItem.getItemType() == MediaLibraryItem.TYPE_MEDIA && ((MediaWrapper) libraryItem).getType() != MediaWrapper.TYPE_AUDIO))
                        continue;
                    Bitmap cover = AudioUtil.readCoverBitmap(Uri.decode(libraryItem.getArtworkMrl()), 256);
                    if (cover == null) cover = DEFAULT_AUDIO_COVER;
                    item.setTitle(libraryItem.getTitle())
                            .setMediaId(generateMediaId(libraryItem));
                    item.setIconBitmap(cover);
                    if (libraryItem.getItemType() == MediaLibraryItem.TYPE_MEDIA) {
                        item.setMediaUri(((MediaWrapper) libraryItem).getUri())
                                .setSubtitle(MediaUtils.INSTANCE.getMediaSubtitle((MediaWrapper) libraryItem));
                    } else item.setSubtitle(libraryItem.getDescription());
                    boolean playable = libraryItem.getItemType() == MediaLibraryItem.TYPE_MEDIA ||
                            libraryItem.getItemType() == MediaLibraryItem.TYPE_ALBUM ||
                            libraryItem.getItemType() == MediaLibraryItem.TYPE_PLAYLIST;
                    results.add(new MediaBrowserCompat.MediaItem(item.build(), playable ? MediaBrowserCompat.MediaItem.FLAG_PLAYABLE : MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
                    if (limitSize && results.size() == MAX_HISTORY_SIZE)
                        break;
                }
            }
        }
        if (results.isEmpty()) {
            MediaDescriptionCompat.Builder mediaItem = new MediaDescriptionCompat.Builder();
            mediaItem.setMediaId(DUMMY);
            mediaItem.setTitle(context.getString(R.string.search_no_result));
            results.add(new MediaBrowserCompat.MediaItem(mediaItem.build(), 0));
        }
        return results;
    }

    @NonNull
    public static String generateMediaId(MediaLibraryItem libraryItem) {
        String prefix;
        switch (libraryItem.getItemType()) {
            case MediaLibraryItem.TYPE_ALBUM:
                prefix = ALBUM_PREFIX;
                break;
            case MediaLibraryItem.TYPE_ARTIST:
                prefix = ARTIST_PREFIX;
                break;
            case MediaLibraryItem.TYPE_GENRE:
                prefix = GENRE_PREFIX;
                break;
            case MediaLibraryItem.TYPE_PLAYLIST:
                prefix = PLAYLIST_PREFIX;
                break;
            default:
                return String.valueOf(libraryItem.getId());
        }
        return prefix+"_"+libraryItem.getId();
    }

    @Override
    public void displayExtensionItems(int extensionId, String title, List<VLCExtensionItem> items, boolean showParams, boolean isRefresh) {
        if (showParams && items.size() == 1 && items.get(0).getType() == VLCExtensionItem.TYPE_DIRECTORY) {
            sExtensionManagerService.browse(items.get(0).stringId);
            return;
        }
        MediaDescriptionCompat.Builder mediaItem;
        VLCExtensionItem extensionItem;
        for (int i=0; i<items.size(); i++) {
            extensionItem = items.get(i);
            if (extensionItem == null || (extensionItem.getType() != VLCExtensionItem.TYPE_AUDIO && extensionItem.getType() != VLCExtensionItem.TYPE_DIRECTORY) )
                continue;
            mediaItem = new MediaDescriptionCompat.Builder();
            Uri coverUri = extensionItem.getImageUri();
            if (coverUri == null) mediaItem.setIconBitmap(DEFAULT_AUDIO_COVER);
            else
                mediaItem.setIconUri(coverUri);
            mediaItem.setTitle(extensionItem.getTitle());
            mediaItem.setSubtitle(extensionItem.getSubTitle());
            boolean playable = extensionItem.getType() == VLCExtensionItem.TYPE_AUDIO;
            if (playable) {
                mediaItem.setMediaId(ExtensionsManager.EXTENSION_PREFIX + "_" + String.valueOf(extensionId) + "_" + extensionItem.getLink());
                extensionItems.add(new MediaBrowserCompat.MediaItem(mediaItem.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
            } else {
                mediaItem.setMediaId(ExtensionsManager.EXTENSION_PREFIX + "_" + String.valueOf(extensionId) + "_" + extensionItem.stringId);
                extensionItems.add(new MediaBrowserCompat.MediaItem(mediaItem.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
            }
            if (i == MAX_EXTENSION_SIZE-1)
                break;
        }
        extensionLock.release();
    }

    private static BrowserProvider instance;
    private static BrowserProvider getInstance() {
        if (instance == null)
            instance = new BrowserProvider();
        return instance;
    }

    private static void createExtensionServiceConnection(final Context context) {
        sExtensionServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                sExtensionManagerService = ((ExtensionManagerService.LocalBinder)service).getService();
                sExtensionManagerService.setExtensionManagerActivity(BrowserProvider.getInstance());
                extensionLock.release();
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                context.unbindService(sExtensionServiceConnection);
                sExtensionServiceConnection = null;
                sExtensionManagerService.stopSelf();
            }
        };

        if (!context.bindService(new Intent(context, ExtensionManagerService.class), sExtensionServiceConnection, Context.BIND_AUTO_CREATE))
            sExtensionServiceConnection = null;
    }

    public static void unbindExtensionConnection() {
        if (sExtensionServiceConnection != null)
            sExtensionManagerService.disconnect();
    }
}
