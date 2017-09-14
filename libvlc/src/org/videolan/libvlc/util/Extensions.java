/*****************************************************************************
 * Extensions.java
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

package org.videolan.libvlc.util;

import java.util.Arrays;
import java.util.HashSet;

public class Extensions {

    public final static HashSet<String> VIDEO = new HashSet<String>();
    public final static HashSet<String> AUDIO = new HashSet<String>();
    public final static HashSet<String> SUBTITLES = new HashSet<String>();
    public final static HashSet<String> PLAYLIST = new HashSet<String>();


    static {
        final String[] videoExtensions = {
                ".3g2", ".3gp", ".3gp2", ".3gpp", ".amv", ".asf", ".avi", ".divx", ".drc", ".dv",
                ".f4v", ".flv", ".gvi", ".gxf", ".ismv", ".iso", ".m1v", ".m2v", ".m2t", ".m2ts",
                ".m4v", ".mkv", ".mov", ".mp2", ".mp2v", ".mp4", ".mp4v", ".mpe", ".mpeg",
                ".mpeg1", ".mpeg2", ".mpeg4", ".mpg", ".mpv2", ".mts", ".mtv", ".mxf", ".mxg",
                ".nsv", ".nut", ".nuv", ".ogm", ".ogv", ".ogx", ".ps", ".rec", ".rm", ".rmvb",
                ".tod", ".ts", ".tts", ".vob", ".vro", ".webm", ".wm", ".wmv", ".wtv", ".xesc" };

        final String[] audioExtensions = {
                ".3ga", ".a52", ".aac", ".ac3", ".adt", ".adts", ".aif", ".aifc", ".aiff", ".alac",
                ".amr", ".aob", ".ape", ".awb", ".caf", ".dts", ".flac", ".it", ".m4a", ".m4b",
                ".m4p", ".mid", ".mka", ".mlp", ".mod", ".mpa", ".mp1", ".mp2", ".mp3", ".mpc",
                ".mpga", ".oga", ".ogg", ".oma", ".opus", ".ra", ".ram", ".rmi", ".s3m", ".spx",
                ".tta", ".voc", ".vqf", ".w64", ".wav", ".wma", ".wv", ".xa", ".xm" };

        final String[] subtitlesExtensions = {
                ".idx", ".sub",  ".srt", ".ssa", ".ass",  ".smi", ".utf", ".utf8", ".utf-8",
                ".rt",   ".aqt", ".txt", ".usf", ".jss",  ".cdg", ".psb", ".mpsub",".mpl2",
                ".pjs", ".dks", ".stl", ".vtt", ".ttml" };

        final String[] playlistExtensions = {".m3u", ".asx",  ".b4s",  ".pls", ".xspf"/*,  ".zip"*/};

        VIDEO.addAll(Arrays.asList(videoExtensions));
        AUDIO.addAll(Arrays.asList(audioExtensions));
        SUBTITLES.addAll(Arrays.asList(subtitlesExtensions));
        PLAYLIST.addAll(Arrays.asList(playlistExtensions));
    }
}