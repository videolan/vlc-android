/*
 * ************************************************************************
 *  VLCBillingDialog.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
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
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.dialogs

import android.os.Bundle
import android.os.Handler
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.coroutineScope
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size
import org.videolan.tools.*
import org.videolan.vlc.R
import org.videolan.vlc.databinding.DonationsBinding
import org.videolan.vlc.donations.VLCBilling
import org.videolan.vlc.donations.util.IabHelper
import org.videolan.vlc.donations.util.IabResult
import org.videolan.vlc.donations.util.Purchase
import org.videolan.vlc.gui.view.DonationSkuView

class VLCBillingDialog : VLCBottomSheetDialogFragment() {
    private var inThankYouMode: Boolean = false
    private lateinit var binding: DonationsBinding
    private lateinit var vlcBilling: VLCBilling
    private lateinit var donationEntries: Array<DonationSkuView>

    override fun getDefaultState() = BottomSheetBehavior.STATE_EXPANDED

    override fun needToManageOrientation() = false

    override fun initialFocusedView() = binding.donateLogo

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Settings.showTvUi) {
            requireActivity().setTheme(R.style.Theme_VLC_Black)
        }
        super.onCreate(savedInstanceState)
        vlcBilling = VLCBilling.getInstance(requireActivity().application)
        vlcBilling.reloadSkus()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DonationsBinding.inflate(layoutInflater, container, false)
        donationEntries = arrayOf(binding.donationTier1, binding.donationTier2, binding.donationTier3, binding.donationTier4, binding.donationTier5)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        if (DeviceInfo(requireActivity()).isAndroidTv) {
            binding.cancelSubscription.text = getString(R.string.subscription_cancel_tv)
            val url = "g.co/play/subscriptions"
            val string = getString(R.string.donate_unsubscribe_tv, url)
            val spanned = SpannableString(string)
            val start = string.indexOf(url)
            val end = string.indexOf(url) + url.length
            spanned.setSpan(UnderlineSpan(), start, end, 0)
            spanned.setSpan(ForegroundColorSpan(ContextCompat.getColor(requireActivity(), R.color.orange500)), start,end, 0)
            binding.tvUnsubscribeDescription.text = spanned
            binding.cancelSubscription.setOnClickListener {
                if (binding.tvUnsubscribe.visibility == View.VISIBLE) binding.tvUnsubscribe.setGone() else binding.tvUnsubscribe.setVisible()
            }
        }

        val onClickListener = View.OnClickListener { view ->
            donationEntries.forEach { donationSkuView ->
                donationSkuView.isSelected = donationSkuView == view
                binding.sendDonation.isEnabled = true
            }
        }
        donationEntries.forEach {
            it.setOnClickListener(onClickListener)
        }

        binding.subscriptionCheckbox.setOnCheckedChangeListener { _, isChecked ->

            populatePrices()
            unselectAll()
        }
        populatePrices()

        binding.sendDonation.setOnClickListener {
            val donationview = arrayOf(binding.donationTier1, binding.donationTier2, binding.donationTier3, binding.donationTier4, binding.donationTier5).first { it.isSelected }
            when {
                binding.subscriptionCheckbox.isChecked -> vlcBilling.iabHelper.launchSubscriptionPurchaseFlow(requireActivity(), donationview.skuDetail.sku, 10001,
                        object : IabHelper.OnIabPurchaseFinishedListener {
                            override fun onIabPurchaseFinished(result: IabResult?, purchase: Purchase?) {
                                when {
                                    result == null || result.isFailure || purchase == null -> {
                                        Log.w("VLCBilling", "Error purchasing: $result")
                                        return
                                    }
                                    else -> {
                                        switchThankYou()
                                        vlcBilling.reloadSkus()
                                    }
                                }
                            }
                        }, "vWGpkjYIj2IYUQ88ciNuPOItF1g7vS"
                )
                else -> vlcBilling.iabHelper.launchPurchaseFlow(requireActivity(), donationview.skuDetail.sku, 10001,
                        object : IabHelper.OnIabPurchaseFinishedListener {
                            override fun onIabPurchaseFinished(result: IabResult?, purchase: Purchase?) {
                                when {
                                    result == null || result.isFailure || purchase == null -> {
                                        Log.w("VLCBilling", "Error purchasing: $result")
                                        return
                                    }
                                    else -> {
                                        vlcBilling.iabHelper.consumeAsync(purchase) { _, _ -> }
                                        switchThankYou()
                                    }
                                }
                            }
                        }, "jT6RGlEhthMbZ9xu17fmRQ4eJNT3Ea"
                )
            }
        }

        binding.anotherDonation.setOnClickListener { switchThankYou() }
        super.onViewCreated(view, savedInstanceState)
    }

    private fun unselectAll() {
        donationEntries.forEach {
            it.isSelected = false
            binding.sendDonation.isEnabled = false
        }
    }

    private fun populatePrices() {
        val skuDetails = if (binding.subscriptionCheckbox.isChecked) vlcBilling.subsDetails else vlcBilling.skuDetails
        donationEntries.forEachIndexed { index, donationSkuView ->
            if (skuDetails.size > index) {
                val skuDetail = skuDetails[index]
                donationSkuView.setText(skuDetail.price)
                val purchase = vlcBilling.purchases.firstOrNull { it.sku == skuDetail.sku }
                donationSkuView.isChecked = purchase != null && purchase.isAutoRenewing
                donationSkuView.skuDetail = skuDetail
                donationSkuView.skuPurchased = purchase != null  && purchase.isAutoRenewing
                donationSkuView.isClickable = purchase == null  || !purchase.isAutoRenewing
            }

        }
        if (binding.subscriptionCheckbox.isChecked && vlcBilling.purchases.isNotEmpty() && vlcBilling.purchases.any { it.isAutoRenewing }){
            binding.alreadySubscribed.setVisible()
            binding.cancelSubscription.setVisible()
        } else{
            binding.alreadySubscribed.setGone()
            binding.cancelSubscription.setGone()
        }
    }

    val fireworksHandler = Handler()

    private fun switchThankYou() {
        inThankYouMode = !inThankYouMode
        val donationViews = arrayOf(binding.donationTier1, binding.donationTier2, binding.donationTier3, binding.donationTier4, binding.donationTier5, binding.donationEmojiTier1, binding.donationEmojiTier2, binding.donationEmojiTier3, binding.donationEmojiTier4, binding.donationEmojiTier5, binding.subscriptionCheckbox, binding.alreadySubscribed)
        if (inThankYouMode) {
            binding.donationsTitle.text = requireActivity().getText(R.string.thank_you)
            binding.donationsDescription.text = requireActivity().getText(R.string.thank_you_desc)
            binding.anotherDonation.setVisible()
            binding.sendDonation.setGone()
            donationViews.forEach { it.setGone() }
            val fireworksRunnable =object :Runnable {
                var currentIndex = 0
                override fun run() {
                    var x = binding.donateLogo.x + binding.donateLogo.width / 2
                    var y = binding.donateLogo.y + binding.donateLogo.height / 2
                    var size = 12
                    when (currentIndex) {
                        1 -> {
                            x = binding.donateLogo.x / 2
                            y -= 18.dp
                            size = 10
                        }
                        2 -> {
                            x = binding.donateLogo.x + binding.donateLogo.width + binding.donateLogo.x / 2
                            y -= 18.dp
                            size = 10
                        }
                    }
                    lifecycleScope.launchWhenStarted {
                        binding.konfetti.build()
                                .addColors(ContextCompat.getColor(requireActivity(), R.color.orange100),ContextCompat.getColor(requireActivity(), R.color.orange500),ContextCompat.getColor(requireActivity(), R.color.orange900))
                                .setDirection(0.0, 359.0)
                                .setSpeed(1f, 9f)
                                .setFadeOutEnabled(true)
                                .setTimeToLive(2000L)
                                .addShapes(Shape.Circle)
                                .addSizes(Size(size))
                                .setPosition(x, y)
                                .setRotationEnabled(false)
                                .burst(50)
                    }
                    if (currentIndex > 1) currentIndex = 0 else currentIndex++
                    fireworksHandler.postDelayed(this, 1500)
                }
            }
            fireworksHandler.post(fireworksRunnable)


        } else {
            binding.donationsTitle.text = requireActivity().getText(R.string.tip_jar)
            binding.donationsDescription.text = requireActivity().getText(R.string.donation_description)
            binding.anotherDonation.setGone()
            binding.sendDonation.setVisible()
            donationViews.forEach { it.setVisible() }
            populatePrices()
            unselectAll()
            binding.konfetti.stopGracefully()
            fireworksHandler.removeCallbacksAndMessages(null)
        }
    }
}