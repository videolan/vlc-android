/*****************************************************************************
 * Dialog.java
 *****************************************************************************
 * Copyright © 2016 VLC authors, VideoLAN and VideoLabs
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

package org.videolan.libvlc;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;

@SuppressWarnings("unused, JniMissingFunction")
public abstract class Dialog {

    /**
     * Dialog Callback, see {@link Dialog#setCallbacks(LibVLC, Callbacks)}
     */
    public interface Callbacks {
        /**
         * Call when an error message need to be displayed
         *
         * @param dialog error dialog to be displayed
         */
        @MainThread
        void onDisplay(ErrorMessage dialog);

        /**
         * Called when a login dialog need to be displayed
         *
         * Call {@link LoginDialog#postLogin(String, String, boolean)} to post the answer, or
         * call {@link LoginDialog#dismiss()} to dismiss the dialog.
         *
         * @param dialog login dialog to be displayed
         */
        @MainThread
        void onDisplay(LoginDialog dialog);

        /**
         * Called when a question dialog need to be displayed
         *
         * Call {@link QuestionDialog#postAction(int)} to post the answer, or
         * call {@link QuestionDialog#dismiss()} to dismiss the dialog.
         *
         * @param dialog question dialog to be displayed
         */
        @MainThread
        void onDisplay(QuestionDialog dialog);

        /**
         * Called when a progress dialog need to be displayed
         *
         * Call {@link ProgressDialog#dismiss()} to dismiss the dialog (if it's cancelable).
         *
         * @param dialog question dialog to be displayed
         */
        @MainThread
        void onDisplay(ProgressDialog dialog);

        /**
         * Called when a previously displayed dialog need to be canceled
         *
         * @param dialog dialog to be canceled
         */
        @MainThread
        void onCanceled(Dialog dialog);

        /**
         * Called when a progress dialog needs to be updated
         *
         * Dialog text and position may be updated, call {@link ProgressDialog#getText()} and
         * {@link ProgressDialog#getPosition()} to get the updated information.
         *
         * @param dialog dialog to be updated
         */
        @MainThread
        void onProgressUpdate(ProgressDialog dialog);
    }

    public static final int TYPE_ERROR = 0;
    public static final int TYPE_LOGIN = 1;
    public static final int TYPE_QUESTION = 2;
    public static final int TYPE_PROGRESS = 3;

    protected final int mType;
    private final String mTitle;
    protected String mText;
    private Object mContext;

    private static Handler sHandler = null;
    private static Callbacks sCallbacks = null;

    protected Dialog(int type, String title, String text) {
        mType = type;
        mTitle = title;
        mText = text;
    }

    /**
     * Get the type of the dialog
     *
     * See {@link Dialog#TYPE_ERROR}, {@link Dialog#TYPE_LOGIN}, {@link Dialog#TYPE_QUESTION} and
     * {@link Dialog#TYPE_PROGRESS}
     * @return
     */
    @MainThread
    public int getType() {
        return mType;
    }

    /**
     * Get the title of the dialog
     */
    @MainThread
    public String getTitle() {
        return mTitle;
    }

    /**
     * Get the text of the dialog
     */
    @MainThread
    public String getText() {
        return mText;
    }

    /**
     * Associate an object with the dialog
     */
    @MainThread
    public void setContext(Object context) {
        mContext = context;
    }

    /**
     * Return the object associated with the dialog
     */
    @MainThread
    public Object getContext() {
        return mContext;
    }

    /**
     * Dismiss the dialog
     */
    @MainThread
    public void dismiss() {
    }

    /**
     * Register callbacks in order to handle VLC dialogs
     *
     * @param libVLC valid LibVLC object
     * @param callbacks dialog callbacks or null to unregister
     */
    @MainThread
    public static void setCallbacks(LibVLC libVLC, Callbacks callbacks) {
        if (callbacks != null && sHandler == null)
            sHandler = new Handler(Looper.getMainLooper());
        sCallbacks = callbacks;
        nativeSetCallbacks(libVLC, callbacks != null);
    }

    /**
     * Error message
     *
     * Used to signal an error message to the user
     */
    public static class ErrorMessage extends Dialog {

        private ErrorMessage(String title, String text) {
            super(TYPE_ERROR, title, text);
        }
    }

    protected static abstract class IdDialog extends Dialog {
        protected long mId;

        protected IdDialog(long id, int type, String title, String text) {
            super(type, title, text);
            mId = id;
        }

        @MainThread
        public void dismiss() {
            if (mId != 0) {
                nativeDismiss(mId);
                mId = 0;
            }
        }
        private native void nativeDismiss(long id);
    }

    /**
     * Login Dialog
     *
     * Used to ask credentials to the user
     */
    public static class LoginDialog extends IdDialog {
        private final String mDefaultUsername;
        private final boolean mAskStore;

        private LoginDialog(long id, String title, String text, String defaultUsername, boolean askStore) {
            super(id, TYPE_LOGIN, title, text);
            mDefaultUsername = defaultUsername;
            mAskStore = askStore;
        }

        /**
         * Get the default user name that should be pre-filled
         */
        @MainThread
        public String getDefaultUsername() {
            return mDefaultUsername;
        }

        /**
         * Should the dialog ask to the user to store the credentials ?
         *
         * @return if true, add a checkbox that ask to the user if he wants to store the credentials
         */
        @MainThread
        public boolean asksStore() {
            return mAskStore;
        }

        /**
         * Post an answer
         *
         * @param username valid username (can't be empty)
         * @param password valid password (can be empty)
         * @param store if true, store the credentials
         */
        @MainThread
        public void postLogin(String username, String password, boolean store) {
            if (mId != 0) {
                nativePostLogin(mId, username, password, store);
                mId = 0;
            }
        }

        private native void nativePostLogin(long id, String username, String password, boolean store);
    }

    /**
     * Question dialog
     *
     * Used to ask a blocking question
     */
    public static class QuestionDialog extends IdDialog {
        public static final int TYPE_NORMAL = 0;
        public static final int TYPE_WARNING = 1;
        public static final int TYPE_ERROR = 2;

        private final int mQuestionType;
        private final String mCancelText;
        private final String mAction1Text;
        private final String mAction2Text;

        private QuestionDialog(long id, String title, String text, int type, String cancelText,
                               String action1Text, String action2Text) {
            super(id, TYPE_QUESTION, title, text);
            mQuestionType = type;
            mCancelText = cancelText;
            mAction1Text = action1Text;
            mAction2Text = action2Text;
        }

        /**
         * Get the type (or severity) of the question dialog
         *
         * See {@link QuestionDialog#TYPE_NORMAL}, {@link QuestionDialog#TYPE_WARNING} and
         * {@link QuestionDialog#TYPE_ERROR}
         */
        @MainThread
        public int getQuestionType() {
            return mQuestionType;
        }

        /**
         * Get the text of the cancel button
         */
        @MainThread
        public String getCancelText() {
            return mCancelText;
        }

        /**
         * Get the text of the first button (optional, can be null)
         */
        @MainThread
        public String getAction1Text() {
            return mAction1Text;
        }

        /**
         * Get the text of the second button (optional, can be null)
         */
        @MainThread
        public String getAction2Text() {
            return mAction2Text;
        }

        /**
         * Post an answer
         *
         * @param action 1 for first action, 2 for second action
         */
        @MainThread
        public void postAction(int action) {
            if (mId != 0) {
                nativePostAction(mId, action);
                mId = 0;
            }
        }
        private native void nativePostAction(long id, int action);
    }

    /**
     * Progress Dialog
     *
     * Used to display a progress dialog
     */
    public static class ProgressDialog extends IdDialog {
        private final boolean mIndeterminate;
        private float mPosition;
        private final String mCancelText;

        private ProgressDialog(long id, String title, String text, boolean indeterminate,
                               float position, String cancelText) {
            super(id, TYPE_PROGRESS, title, text);
            mIndeterminate = indeterminate;
            mPosition = position;
            mCancelText = cancelText;
        }

        /**
         * Return true if the progress dialog is inderterminate
         */
        @MainThread
        public boolean isIndeterminate() {
            return mIndeterminate;
        }

        /**
         * Return true if the progress dialog is cancelable
         */
        @MainThread
        public boolean isCancelable() {
            return mCancelText != null;
        }

        /**
         * Get the position of the progress dialog
         * @return position between 0.0 and 1.0
         */
        @MainThread
        public float getPosition() {
            return mPosition;
        }

        /**
         * Get the text of the cancel button
         */
        @MainThread
        public String getCancelText() {
            return mCancelText;
        }

        private void update(float position, String text) {
            mPosition = position;
            mText = text;
        }

    }

    @SuppressWarnings("unused") /* Used from JNI */
    private static void displayErrorFromNative(String title, String text) {
        final ErrorMessage dialog = new ErrorMessage(title, text);
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sCallbacks != null)
                    sCallbacks.onDisplay(dialog);
            }
        });
    }


    @SuppressWarnings("unused") /* Used from JNI */
    private static Dialog displayLoginFromNative(long id, String title, String text,
                                                 String defaultUsername, boolean askStore) {
        final LoginDialog dialog = new LoginDialog(id, title, text, defaultUsername, askStore);
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sCallbacks != null)
                    sCallbacks.onDisplay(dialog);
            }
        });
        return dialog;
    }

    @SuppressWarnings("unused") /* Used from JNI */
    private static Dialog displayQuestionFromNative(long id, String title, String text,
                                                    int type, String cancelText,
                                                    String action1Text, String action2Text) {
        final QuestionDialog dialog = new QuestionDialog(id, title, text, type, cancelText,
                action1Text, action2Text);
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sCallbacks != null)
                    sCallbacks.onDisplay(dialog);
            }
        });
        return dialog;
    }

    @SuppressWarnings("unused") /* Used from JNI */
    private static Dialog displayProgressFromNative(long id, String title, String text,
                                                    boolean indeterminate,
                                                    float position, String cancelText) {
        final ProgressDialog dialog = new ProgressDialog(id, title, text, indeterminate, position, cancelText);
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sCallbacks != null)
                    sCallbacks.onDisplay(dialog);
            }
        });
        return dialog;
    }

    @SuppressWarnings("unused") /* Used from JNI */
    private static void cancelFromNative(final Dialog dialog) {
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                if (dialog instanceof IdDialog)
                    ((IdDialog) dialog).dismiss();
                if (sCallbacks != null && dialog != null)
                    sCallbacks.onCanceled(dialog);
            }
        });
    }

    @SuppressWarnings("unused") /* Used from JNI */
    private static void updateProgressFromNative(final Dialog dialog, final float position,
                                                 final String text) {
        sHandler.post(new Runnable() {
            @Override
            public void run() {
                if (dialog.getType() != TYPE_PROGRESS)
                    throw new IllegalArgumentException("dialog is not a progress dialog");
                final ProgressDialog progressDialog = (ProgressDialog) dialog;
                progressDialog.update(position, text);
                if (sCallbacks != null)
                    sCallbacks.onProgressUpdate(progressDialog);
            }
        });
    }

    private static native void nativeSetCallbacks(LibVLC libVLC, boolean enabled);
}