/*****************************************************************************
 * DirectoryAdapter.java
 *****************************************************************************
 * Copyright © 2012-2013 VLC authors and VideoLAN
 * Copyright © 2012-2013 Edward Wang
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
import java.util.ListIterator;
import java.util.regex.Pattern;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.vlc.R;
import org.videolan.vlc.Util;
import org.videolan.vlc.VLCApplication;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
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
        public DirectoryAdapter.Node parent;
        public ArrayList<DirectoryAdapter.Node> children;
        /**
         * Name of the file/folder (not full path).
         *
         * null on the root node (root selection folder).
         */
        String name;
        String visibleName;
        public Boolean isFile;

        public Node(String _name) {
            this(_name, null);
        }

        public Node(String _name, String _visibleName) {
            this.name = _name;
            this.visibleName = _visibleName;
            this.children = new ArrayList<DirectoryAdapter.Node>();
            this.isFile = false;
            this.parent = null;
        }

        public void addChildNode(DirectoryAdapter.Node n) {
            n.parent = this;
            this.children.add(n);
        }

        public DirectoryAdapter.Node getChildNode(String directoryName) {
            for(DirectoryAdapter.Node n : this.children) {
                if(n.name.equals(directoryName))
                    return n;
            }
            DirectoryAdapter.Node n = new DirectoryAdapter.Node(directoryName);
            this.addChildNode(n);
            return n;
        }

        public Boolean isFile() {
            return this.isFile;
        }

        public void setIsFile() {
            this.isFile = true;
        }

        public Boolean existsChild(String _n) {
            for(DirectoryAdapter.Node n : this.children) {
                if(Util.nullEquals(n.name, _n)) return true;
            }
            return false;
        }

        public int getChildPosition(DirectoryAdapter.Node child){
            if(child == null)
                return -1;

            ListIterator<DirectoryAdapter.Node> it = children.listIterator();
            while(it.hasNext()){
                DirectoryAdapter.Node node = it.next();
                if(child.equals(node)) return it.previousIndex();
            }

            return -1;
        }

        public DirectoryAdapter.Node ensureExists(String _n) {
            for(DirectoryAdapter.Node n : this.children) {
                if(Util.nullEquals(n.name, _n)) return n;
            }
            DirectoryAdapter.Node nn = new Node(_n);
            this.children.add(nn);
            return nn;
        }

        public int subfolderCount() {
            int c = 0;
            for(DirectoryAdapter.Node n : this.children) {
                if(n.isFile() == false && !n.name.equals("..")) c++;
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

        public String getVisibleName() {
            return (this.visibleName != null) ? this.visibleName : this.name;
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

    private void populateNode(DirectoryAdapter.Node n, String path) {
        populateNode(n, path, 0);
    }

    /**
     * @param n Node to populate
     * @param path Path to populate
     * @param depth Depth of iteration (0 = 1st level of nesting, 1 = 2 level of nesting, etc)
     */
    private void populateNode(DirectoryAdapter.Node n, String path, int depth) {
        if (path == null) {
            // We're on the storage list
            String storages[] = Util.getMediaDirectories();
            for (String storage : storages) {
                File f = new File(storage);
                DirectoryAdapter.Node child = new DirectoryAdapter.Node(f.getName(), getVisibleName(f));
                child.isFile = false;
                this.populateNode(child, storage, 0);
                n.addChildNode(child);
            }
            return;
        }


        File file = new File(path);
        if(!file.exists() || !file.isDirectory())
            return;

        ArrayList<String> files = new ArrayList<String>();
        LibVLC.nativeReadDirectory(path, files);
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

                // Don't try to go beyond depth 10 as a safety measure.
                if (LibVLC.nativeIsPathDirectory(newPath) && depth < 10) {
                    ArrayList<String> files_int = new ArrayList<String>();
                    LibVLC.nativeReadDirectory(newPath, files_int);
                    if(files_int.size() < 8) { /* Optimisation: If there are more than 8
                                                   sub-folders, don't scan each one, otherwise
                                                   when scaled it is very slow to load */
                        String mCurrentDir_old = mCurrentDir;
                        mCurrentDir = path;
                        this.populateNode(nss, newPath, depth+1);
                        mCurrentDir = mCurrentDir_old;
                    }
                } else {
                    if(acceptedPath(newPath))
                        nss.setIsFile();
                    else
                        continue;
                }

                n.addChildNode(nss);
            }
            Collections.sort(n.children);
        }

        DirectoryAdapter.Node up = new DirectoryAdapter.Node("..");
        n.children.add(0, up);
    }

    private LayoutInflater mInflater;
    private DirectoryAdapter.Node mRootNode;
    private DirectoryAdapter.Node mCurrentNode;
    private String mCurrentDir;
    private String mCurrentRoot;

    public DirectoryAdapter(Context context) {
        DirectoryAdapter_Core(context, null);
    }

    private void DirectoryAdapter_Core(Context activityContext, String rootDir) {
        if (rootDir != null)
            rootDir = Util.stripTrailingSlash(rootDir);
        Log.v(TAG, "rootMRL is " + rootDir);
        mInflater = LayoutInflater.from(activityContext);
        mRootNode = new DirectoryAdapter.Node(rootDir);
        mCurrentDir = rootDir;
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

        String holderText = "";
        if(selectedNode.isFile()) {
            Log.d(TAG, "Loading media " + selectedNode.name);
            Media m = new Media(LibVLC.getExistingInstance(), getMediaLocation(position));
            holder.title.setText(m.getTitle());
            holderText = m.getSubtitle();
        } else
            holder.title.setText(selectedNode.getVisibleName());

        if(selectedNode.name.equals(".."))
            holderText = context.getString(R.string.parent_folder);
        else if(!selectedNode.isFile()) {
            int folderCount = selectedNode.subfolderCount();
            int mediaFileCount = selectedNode.subfilesCount();
            holderText = "";

            if(folderCount > 0)
                holderText += context.getResources().getQuantityString(
                        R.plurals.subfolders_quantity, folderCount, folderCount
                );
            if(folderCount > 0 && mediaFileCount > 0)
                holderText += ", ";
            if(mediaFileCount > 0)
                holderText += context.getResources().getQuantityString(
                        R.plurals.mediafiles_quantity, mediaFileCount,
                        mediaFileCount);
        }
        holder.text.setText(holderText);
        if(selectedNode.isFile())
            holder.icon.setImageResource(R.drawable.icon);
        else
            holder.icon.setImageResource(R.drawable.ic_menu_folder);

        return v;
    }

    /**
     * @return position of the current directory in a newly formed listview
     * or -1 if opening not successful
     * */

    public int browse(int position) {
        DirectoryAdapter.Node selectedNode = mCurrentNode.children.get(position);
        if(selectedNode.isFile()) return -1;
        return browse(selectedNode.name);
    }

    /**
     * @return position of the current directory in a newly formed listview
     * or -1 if opening not successful
     * */

    public int browse(String directoryName) {
        if (this.mCurrentDir == null) {
            // We're on the storage list
            String storages[] = Util.getMediaDirectories();
            for (String storage : storages) {
                storage = Util.stripTrailingSlash(storage);
                if (storage.endsWith(directoryName)) {
                    this.mCurrentRoot = storage;
                    this.mCurrentDir = Util.stripTrailingSlash(storage);
                    break;
                }
            }
        } else {
            try {
                this.mCurrentDir = new URI(
                        LibVLC.PathToURI(this.mCurrentDir + "/" + directoryName))
                        .normalize().getPath();
                this.mCurrentDir = Util.stripTrailingSlash(this.mCurrentDir);

                if (this.mCurrentDir.equals(getParentDir(this.mCurrentRoot))) {
                    // Returning to the storage list
                    this.mCurrentDir = null;
                    this.mCurrentRoot = null;
                }
            } catch(URISyntaxException e) {
                Log.e(TAG, "URISyntaxException in browse()", e);
                return -1;
            } catch(NullPointerException e) {
                Log.e(TAG, "NullPointerException in browse()", e);
                return -1;
            }
        }

        Log.d(TAG, "Browsing to " + this.mCurrentDir);

        int currentDirPosition = 0;
        if(directoryName.equals("..")){
            currentDirPosition = mCurrentNode.parent.getChildPosition(mCurrentNode);
            this.mCurrentNode = this.mCurrentNode.parent;
        } else {
            this.mCurrentNode = this.mCurrentNode.getChildNode(directoryName);
            if(mCurrentNode.subfolderCount() < 1) {
                // Clear the ".." entry
                this.mCurrentNode.children.clear();
                this.populateNode(mCurrentNode, mCurrentDir);
            }
        }

        this.notifyDataSetChanged();
        return (currentDirPosition == -1) ? 0 : currentDirPosition;
    }

    public boolean isChildFile(int position) {
        DirectoryAdapter.Node selectedNode = mCurrentNode.children.get(position);
        return selectedNode.isFile();
    }

    public String getMediaLocation(int position) {
        if (position >= mCurrentNode.children.size())
            return null;
        return LibVLC.PathToURI(
                this.mCurrentDir + "/" + mCurrentNode.children.get(position).name
        );
    }

    public boolean isRoot() {
        return mCurrentDir == null;
    }

    public String getmCurrentDir() {
        return mCurrentDir;
    }

    public ArrayList<String> getAllMediaLocations() {
        ArrayList<String> a = new ArrayList<String>();
        // i = 1 to exclude ".." folder
        for(int i = 1; i < mCurrentNode.children.size(); i++)
            if(mCurrentNode.children.get(i).isFile)
                a.add(getMediaLocation(i));
        return a;
    }

    public void refresh() {
        for(DirectoryAdapter.Node n : this.mCurrentNode.children)
            n.children.clear();
        this.mCurrentNode.children.clear();
        this.populateNode(mCurrentNode, mCurrentDir);

        this.notifyDataSetChanged();
    }

    private String getParentDir(String path) {
        try {
            path = new URI(LibVLC.PathToURI(path + "/.."))
                    .normalize().getPath();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return Util.stripTrailingSlash(path);
    }

    private String getVisibleName(File file) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // Show "sdcard" for the user's folder when running in multi-user
            if (file.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getPath())) {
                return VLCApplication.getAppContext().getString(R.string.internal_memory);
            }
        }
        return file.getName();
    }
}
