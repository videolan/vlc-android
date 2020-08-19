/*
 * ************************************************************************
 *  VLCBilling.kt
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

package org.videolan.vlc.donations

import android.app.Application
import android.content.IntentFilter
import android.util.Log
import org.videolan.tools.SingletonHolder
import org.videolan.vlc.donations.util.*
import java.util.concurrent.atomic.AtomicBoolean

private const val DONATION_TIER_1 = "donation_tier_1"
private const val DONATION_TIER_2 = "donation_tier_2"
private const val DONATION_TIER_3 = "donation_tier_3"
private const val DONATION_TIER_4 = "donation_tier_4"
private const val DONATION_TIER_5 = "donation_tier_5"
private const val SUBSCRIPTION_TIER_1 = "subscription_tier_1"
private const val SUBSCRIPTION_TIER_2 = "subscription_tier_2"
private const val SUBSCRIPTION_TIER_3 = "subscription_tier_3"
private const val SUBSCRIPTION_TIER_4 = "subscription_tier_4"
private const val SUBSCRIPTION_TIER_5 = "subscription_tier_5"

class VLCBilling private constructor(private val context: Application) : IabBroadcastReceiver.IabBroadcastListener, IabHelper.QueryInventoryFinishedListener {

    private var debug = false
    private var alreadySetup = false
    private lateinit var mBroadcastReceiver: IabBroadcastReceiver
    val iabHelper by lazy { IabHelper(context, BuildConfig.PUBLIC_API_KEY).apply { if (debug) enableDebugLogging(true, "VLCBillingInt") } }
    private val skuList = arrayListOf(DONATION_TIER_1, DONATION_TIER_2, DONATION_TIER_3, DONATION_TIER_4, DONATION_TIER_5, SUBSCRIPTION_TIER_1, SUBSCRIPTION_TIER_2, SUBSCRIPTION_TIER_3, SUBSCRIPTION_TIER_4, SUBSCRIPTION_TIER_5)
    val skuDetails = ArrayList<SkuDetails>()
    val subsDetails = ArrayList<SkuDetails>()
    val purchases = ArrayList<Purchase>()
    public var status = BillingStatus.NONE
        set(value) {
            field = value
            listeners.forEach { it.invoke(value) }
        }
    var listeners: ArrayList<(BillingStatus) -> Unit> = arrayListOf()

    fun addStatusListener(listener: (BillingStatus) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (BillingStatus) -> Unit) {
        listeners.remove(listener)
    }

    fun retrieveSkus() {
        if (alreadySetup) return
        alreadySetup = true

        iabHelper.startSetup { result ->
            status = BillingStatus.CONNECTING
            if (!result.isSuccess) {
                Log.e("VLCBilling", "Problem setting up in-app billing: $result")
                status = BillingStatus.FAILURE
                return@startSetup
            }
            status = BillingStatus.CONNECTED
            mBroadcastReceiver = IabBroadcastReceiver(this)
            val broadcastFilter = IntentFilter(IabBroadcastReceiver.ACTION)
            context.registerReceiver(mBroadcastReceiver, broadcastFilter)

            if (!iabHelper.mAsyncInProgress) iabHelper.queryInventoryAsync(true, skuList, this)
        }
    }

    fun reloadSkus() {
        if (!iabHelper.mAsyncInProgress) iabHelper.queryInventoryAsync(true, skuList, this)
    }

    override fun receivedBroadcast() {}

    override fun onQueryInventoryFinished(result: IabResult?, inventory: Inventory?) {

        // Is it a failure?
        if (result!!.isFailure) {
            Log.e("VLCBilling", "Failed to query inventory: $result")
            status = BillingStatus.FAILURE
            return
        }

        if (debug) Log.d("VLCBilling", "Query inventory was successful.")


        subsDetails.clear()
        subsDetails.clear()
        purchases.clear()
        skuList.forEach { skuDetail ->
            val details = inventory?.getSkuDetails(skuDetail)
            details?.let { if (skuDetail.contains("subscription")) subsDetails.add(it) else skuDetails.add(it) }
            if (skuDetail.contains("donation") && inventory?.hasPurchase(skuDetail) == true) {
                iabHelper.consumeAsync(inventory.getPurchase(skuDetail)!!){ _, _ ->
                    if (debug) Log.d("VLCBilling", "Consumed")
                }
            }
            if (skuDetail.contains("subscription") && inventory?.hasPurchase(skuDetail) == true) purchases.add(inventory.getPurchase(skuDetail)!!)
            if (debug) Log.d("VLCBilling", "${details?.price}")
        }


        status = BillingStatus.SKU_RETRIEVED
    }

    companion object : SingletonHolder<VLCBilling, Application>(::VLCBilling)
}

enum class BillingStatus {
    NONE, CONNECTING, CONNECTED, FAILURE, SKU_RETRIEVED
}