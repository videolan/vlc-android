/*****************************************************************************
 * DirectoryAdapter.java
 *****************************************************************************
 * Copyright © 2012 VLC authors and VideoLAN
 * Copyright © 2012 Edward Wang
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
package org.videolan.vlc.gui;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;

import org.videolan.vlc.LibVLC;
import org.videolan.vlc.Media;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCApplication;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class DirectoryAdapter extends BaseAdapter {
    public final static String TAG = "VLC/DirectoryAdapter";

    public static boolean acceptedPath(String f) {
        return Pattern.compile(Media.EXTENSIONS_REGEX, Pattern.CASE_INSENSITIVE).matcher(f).matches();
    }

    /**
     * Private helper class to implement the directory tree
     */
    public class Node implements Comparable<DirectoryAdapter.Node> {
        public ArrayList<DirectoryAdapter.Node> children;
        String name;
        public Boolean isFile;

        public Node(String _name) {
            this.name = _name;
            this.children = new ArrayList<DirectoryAdapter.Node>();
            this.isFile = false;
        }

        public Boolean isFile() {
            return this.isFile;
        }

        public void setIsFile() {
            this.isFile = true;
        }

        public Boolean existsChild(String _n) {
            for(DirectoryAdapter.Node n : this.children) {
                if(n.name == _n) return true;
            }
            return false;
        }

        public DirectoryAdapter.Node ensureExists(String _n) {
            for(DirectoryAdapter.Node n : this.children) {
                if(n.name == _n) return n;
            }
            DirectoryAdapter.Node nn = new Node(_n);
            this.children.add(nn);
            return nn;
        }

        public int subfolderCount() {
            int c = 0;
            for(DirectoryAdapter.Node n : this.children) {
                if(n.isFile() == false && n.name != "..") c++;
            }
            return c;
        }

        public int subfilesCount() {
            int c = 0;
            for(DirectoryAdapter.Node n : this.children) {
                if(n.isFile() == true) c++;
            }
            return c;
        }

        @Override
        public int compareTo(Node arg0) {
            if(this.isFile && !(arg0.isFile))
                return 1;
            else if(!(this.isFile) && arg0.isFile)
                return -1;
            else
                return String.CASE_INSENSITIVE_ORDER.compare(this.name, arg0.name);
        }
    }

    static class DirectoryViewHolder {
        View layout;
        TextView title;
        TextView text;
        ImageView icon;
    }

    public void populateNode(DirectoryAdapter.Node n, String path) {
        File file = new File(path);
        if(!file.exists() || !file.isDirectory())
            return;

        ArrayList<String> files = new ArrayList<String>();
        LibVLC.getExistingInstance().nativeReadDirectory(path, files);
        StringBuilder sb = new StringBuilder(100);
        /* If no sub-directories or I/O error don't crash */
        if(files == null || files.size() < 1) {
            //return
        } else {
            for(int i = 0; i < files.size(); i++) {
                String filename = files.get(i);
                /* Avoid infinite loop */
                if(filename.equals(".") || filename.equals("..") || filename.startsWith(".")) continue;

                DirectoryAdapter.Node nss = new DirectoryAdapter.Node(filename);
                nss.isFile = false;
                sb.append(path);
                sb.append("/");
                sb.append(filename);
                String newPath = sb.toString();
                sb.setLength(0);

                if(LibVLC.getExistingInstance().nativeIsPathDirectory(newPath)) {
                    ArrayList<String> files_int = new ArrayList<String>();
                    LibVLC.getExistingInstance().nativeReadDirectory(newPath, files_int);
                    if(files_int.size() < 8) { /* Optimisation: If there are more than 8
                                                   sub-folders, don't scan each one, otherwise
                                                   when scaled it is very slow to load */
                        String mCurrentDir_old = mCurrentDir;
                        mCurrentDir = path;
                        this.populateNode(nss, newPath);
                        mCurrentDir = mCurrentDir_old;
                    }
                } else {
                    if(acceptedPath(newPath))
                        nss.setIsFile();
                    else
                        continue;
                }

                n.children.add(nss);
            }
            Collections.sort(n.children);
        }
        /* Don't let the user escape into the wild by jumping above the root dir */
        if(mCurrentDir.contains(mRootDir) && !mCurrentDir.equals(mRootDir)) {
            DirectoryAdapter.Node up = new DirectoryAdapter.Node("..");
            n.children.add(0, up);
        }
    }

    private LayoutInflater mInflater;
    private DirectoryAdapter.Node mRootNode;
    private DirectoryAdapter.Node mCurrentNode;
    private String mRootDir;
    private String mCurrentDir;

    public DirectoryAdapter() {
        DirectoryAdapter_Core(
                PreferenceManager.getDefaultSharedPreferences(VLCApplication.getAppContext())
                .getString("directories_root", android.os.Environment.getExternalStorageDirectory().getPath())
        );
    }

    public DirectoryAdapter(Context context, String rootDir) {
        DirectoryAdapter_Core(rootDir);
    }

    private void DirectoryAdapter_Core(String rootDir) {
        rootDir = Util.stripTrailingSlash(rootDir);
        Log.v(TAG, "rootMRL is " + rootDir);
        mInflater = LayoutInflater.from(VLCApplication.getAppContext());
        mRootNode = new DirectoryAdapter.Node(rootDir);
        mCurrentDir = rootDir;
        mRootDir = rootDir;
        this.populateNode(mRootNode, rootDir);
        mCurrentNode = mRootNode;
    }

    @Override
    public boolean hasStableIds() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public int getCount() {
        return mCurrentNode.children.size();
    }

    @Override
    public Object getItem(int arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getItemId(int arg0) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        DirectoryAdapter.Node selectedNode = mCurrentNode.children.get(position);
        DirectoryViewHolder holder;
        View v = convertView;

        Context context = VLCApplication.getAppContext();

        /* If view not created */
        if (v == null) {
            v = mInflater.inflate(R.layout.directory_view_item, parent, false);
            holder = new DirectoryViewHolder();
            holder.layout = v.findViewById(R.id.layout_item);
            holder.title = (TextView) v.findViewById(R.id.title);
            holder.text = (TextView) v.findViewById(R.id.text);
            holder.icon = (ImageView) v.findViewById(R.id.dvi_icon);
            v.setTag(holder);
        } else
            holder = (DirectoryViewHolder) v.getTag();

        Util.setItemBackground(holder.layout, position);

        String holderText = "";
        if(selectedNode.isFile()) {
            Log.d(TAG, "Loading media " + selectedNode.name);
            Media m = new Media(getMediaLocation(position), false);
            holder.title.setText(m.getTitle());
            holderText = m.getArtist() + " - " + m.getAlbum();
        } else
            holder.title.setText(selectedNode.name);

        if(selectedNode.name == "..")
            holderText = context.getString(R.string.parent_folder);
        else if(!selectedNode.isFile()) {
            int folderCount = selectedNode.subfolderCount();
            int songCount = selectedNode.subfilesCount();
            holderText = "";

            if(folderCount > 0)
                holderText += context.getResources().getQuantityString(
                        R.plurals.subfolders, folderCount, folderCount
                );
            if(folderCount > 0 && songCount > 0)
                holderText += ", ";
            if(songCount > 0)
                holderText += context.getResources().getQuantityString(
                        R.plurals.songs, songCount, songCount
                );
        }
        holder.text.setText(holderText);
        if(selectedNode.isFile())
            holder.icon.setImageResource(R.drawable.icon);
        else
            holder.icon.setImageResource(R.drawable.ic_folder);

        return v;
    }

    public Boolean browse(int position) {
        DirectoryAdapter.Node selectedNode = mCurrentNode.children.get(position);
        if(selectedNode.isFile()) return false;
        try {
            this.mCurrentDir = new URI(LibVLC.getExistingInstance().nativeToURI(this.mCurrentDir + "/" + selectedNode.name)).normalize().getPath();
            this.mCurrentDir = Util.stripTrailingSlash(this.mCurrentDir);
        } catch (URISyntaxException e) {
            /* blah blah blah blah blah */
        }

        Log.d(TAG, "Browsing to " + this.mCurrentDir);

        this.mCurrentNode.children.clear();
        this.mCurrentNode = new DirectoryAdapter.Node(mCurrentDir);
        this.populateNode(mCurrentNode, mCurrentDir);

        this.notifyDataSetChanged();
        return true;
    }

    public String getMediaLocation(int position) {
        return LibVLC.getExistingInstance().nativeToURI(
                this.mCurrentDir + "/" + mCurrentNode.children.get(position).name
        );
    }

    public void clear() {
        for(DirectoryAdapter.Node n : this.mCurrentNode.children)
            n.children.clear();
        this.mCurrentNode.children.clear();
        this.mCurrentNode = new DirectoryAdapter.Node(mCurrentDir);
        this.populateNode(mCurrentNode, mCurrentDir);

        this.notifyDataSetChanged();
    }
}
