package org.videolan.vlc.gui.dialogs

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.color.MaterialColors
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DialogDuplicationWarningBinding

class DuplicationWarningDialog : VLCBottomSheetDialogFragment(), View.OnClickListener {

    override fun getDefaultState(): Int = STATE_EXPANDED

    override fun needToManageOrientation(): Boolean = false

    private lateinit var binding: DialogDuplicationWarningBinding

    private var duplicationMessages: ArrayList<String> = arrayListOf()
    private var finalMessage: SpannableString = SpannableString("")
    private var playlistTitles: ArrayList<String> = arrayListOf()
    private var shouldShowThreeOptions: Boolean = false

    override fun initialFocusedView(): View {
        return if (shouldShowThreeOptions)
            binding.addNewButton
        else
            binding.cancelButton
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        duplicationMessages = arguments?.getStringArrayList(DUPLICATION_MESSAGES_KEY)!!
        shouldShowThreeOptions = arguments?.getBoolean(SHOW_OPTIONS_KEY)!!
        playlistTitles = arguments?.getStringArrayList(TITLE_KEY)!!
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogDuplicationWarningBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (shouldShowThreeOptions) {
            binding.addAllButton.setOnClickListener(this)
            binding.addNewButton.setOnClickListener(this)
            binding.cancelButton.setOnClickListener(this)
        } else {
            binding.addNewButton.visibility = View.GONE
            binding.addAllButton.text = resources.getString(R.string.add_button)
            binding.addAllButton.setOnClickListener(this)
            binding.cancelButton.setOnClickListener(this)
        }
        for (i in 0 until duplicationMessages.size) {
            setupSecondaryText(duplicationMessages[i], playlistTitles[i])
        }
        binding.secondaryTextview.text = finalMessage
    }

    override fun onClick(view: View) {
        val option = when (view.id) {
            R.id.add_all_button -> {
                ADD_ALL
            }
            R.id.add_new_button -> {
                ADD_NEW
            }
            R.id.cancel_button -> {
                CANCEL
            }
            else -> {
                NO_OPTION
            }
        }
        val bundle = bundleOf(OPTION_KEY to option)
        setFragmentResult(REQUEST_KEY, bundle)
        dismiss()
    }

    private fun setupSecondaryText(secondaryMessage: String, playlistTitle: String) {
        val searchTitle = "\"$playlistTitle\""
        val styledText = SpannableString.valueOf(secondaryMessage)
        val startIndex = styledText.indexOf(searchTitle)
        val endIndex = startIndex + searchTitle.length
        styledText.setSpan(StyleSpan(Typeface.BOLD), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        styledText.setSpan(ForegroundColorSpan(MaterialColors.getColor(requireContext(), R.attr.font_default, Color.BLACK)), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        finalMessage = SpannableString.valueOf(finalMessage.toString() + styledText.toString()+"\n")
    }

    companion object {

        const val REQUEST_KEY = "REQUEST_KEY"
        const val OPTION_KEY = "option"

        const val NO_OPTION = -1
        const val ADD_ALL = 0
        const val ADD_NEW = 1
        const val CANCEL = 2

        private const val TITLE_KEY = "playlist_title"
        private const val DUPLICATION_MESSAGES_KEY = "duplication_messages"
        private const val SHOW_OPTIONS_KEY = "show_three_options"

        fun newInstance(shouldShowThreeOptions: Boolean, playlistTitle: ArrayList<String>, duplicationMessages: ArrayList<String>) : DuplicationWarningDialog {
            return DuplicationWarningDialog().apply {
                val args = Bundle()
                args.putStringArrayList(TITLE_KEY, playlistTitle)
                args.putStringArrayList(DUPLICATION_MESSAGES_KEY, duplicationMessages)
                args.putBoolean(SHOW_OPTIONS_KEY, shouldShowThreeOptions)
                arguments = args
            }
        }
    }
}
