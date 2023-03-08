/* Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.videolan.vlc.donations.util

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.ServiceConnection
import android.os.*
import android.text.TextUtils
import android.util.Log
import com.android.vending.billing.IInAppBillingService
import org.json.JSONException
import org.videolan.vlc.donations.util.IabException
import org.videolan.vlc.donations.util.Security.verifyPurchase
import java.util.*

/**
 * Provides convenience methods for in-app billing. You can create one instance of this
 * class for your application and use it to process in-app billing operations.
 * It provides synchronous (blocking) and asynchronous (non-blocking) methods for
 * many common in-app billing operations, as well as automatic signature
 * verification.
 *
 * After instantiating, you must perform setup in order to start using the object.
 * To perform setup, call the [.startSetup] method and provide a listener;
 * that listener will be notified when setup is complete, after which (and not before)
 * you may call other methods.
 *
 * After setup is complete, you will typically want to request an inventory of owned
 * items and subscriptions. See [.queryInventory], [.queryInventoryAsync]
 * and related methods.
 *
 * When you are done with this object, don't forget to call [.dispose]
 * to ensure proper cleanup. This object holds a binding to the in-app billing
 * service, which will leak unless you dispose of it correctly. If you created
 * the object on an Activity's onCreate method, then the recommended
 * place to dispose of it is the Activity's onDestroy method.
 *
 * A note about threading: When using this object from a background thread, you may
 * call the blocking versions of methods; when using from a UI thread, call
 * only the asynchronous versions and handle the results via callbacks.
 * Also, notice that you can only call one asynchronous operation at a time;
 * attempting to start a second asynchronous operation while the first one
 * has not yet completed will result in an exception being thrown.
 *
 */
class IabHelper(ctx: Context, base64PublicKey: String?) {
    // Is debug logging enabled?
    var mDebugLog = false
    var mDebugTag = "IabHelper"

    // Is setup done?
    var mSetupDone = false

    // Has this object been disposed of? (If so, we should ignore callbacks, etc)
    var mDisposed = false

    // Are subscriptions supported?
    var mSubscriptionsSupported = false

    // Is subscription update supported?
    var mSubscriptionUpdateSupported = false

    // Is an asynchronous operation in progress?
    // (only one at a time can be in progress)
    var mAsyncInProgress = false

    // (for logging/debugging)
    // if mAsyncInProgress == true, what asynchronous operation is in progress?
    var mAsyncOperation = ""

    // Context we were passed during initialization
    var mContext: Context?

    // Connection to the service
    var mService: IInAppBillingService? = null
    var mServiceConn: ServiceConnection? = null

    // The request code used to launch purchase flow
    var mRequestCode = 0

    // The item type of the current purchase flow
    var mPurchasingItemType: String? = null

    // Public key for verifying signature, in base64 encoding
    var mSignatureBase64: String? = null

    /**
     * Enables or disable debug logging through LogCat.
     */
    fun enableDebugLogging(enable: Boolean, tag: String) {
        checkNotDisposed()
        mDebugLog = enable
        mDebugTag = tag
    }

    fun enableDebugLogging(enable: Boolean) {
        checkNotDisposed()
        mDebugLog = enable
    }


    /**
     * Starts the setup process. This will start up the setup process asynchronously.
     * You will be notified through the listener when the setup process is complete.
     * This method is safe to call from a UI thread.
     *
     * @param listener The listener to notify when the setup process is complete.
     */
    fun startSetup(listener: (IabResult)-> Unit) {
        // If already set up, can't do it again.
        checkNotDisposed()
        check(!mSetupDone) { "IAB helper is already set up." }

        // Connection to IAB service
        logDebug("Starting in-app billing setup.")
        mServiceConn = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName) {
                logDebug("Billing service disconnected.")
                mService = null
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (mDisposed) return
                logDebug("Billing service connected.")
                mService = IInAppBillingService.Stub.asInterface(service)
                val packageName = mContext!!.packageName
                try {
                    logDebug("Checking for in-app billing 3 support.")

                    // check for in-app billing v3 support
                    var response = mService!!.isBillingSupported(3, packageName, ITEM_TYPE_INAPP)
                    if (response != BILLING_RESPONSE_RESULT_OK) {
                        listener.invoke(IabResult(response,
                                "Error checking for billing v3 support."))

                        // if in-app purchases aren't supported, neither are subscriptions
                        mSubscriptionsSupported = false
                        mSubscriptionUpdateSupported = false
                        return
                    } else {
                        logDebug("In-app billing version 3 supported for $packageName")
                    }

                    // Check for v5 subscriptions support. This is needed for
                    // getBuyIntentToReplaceSku which allows for subscription update
                    response = mService!!.isBillingSupported(5, packageName, ITEM_TYPE_SUBS)
                    mSubscriptionUpdateSupported = if (response == BILLING_RESPONSE_RESULT_OK) {
                        logDebug("Subscription re-signup AVAILABLE.")
                        true
                    } else {
                        logDebug("Subscription re-signup not available.")
                        false
                    }
                    if (mSubscriptionUpdateSupported) {
                        mSubscriptionsSupported = true
                    } else {
                        // check for v3 subscriptions support
                        response = mService!!.isBillingSupported(3, packageName, ITEM_TYPE_SUBS)
                        if (response == BILLING_RESPONSE_RESULT_OK) {
                            logDebug("Subscriptions AVAILABLE.")
                            mSubscriptionsSupported = true
                        } else {
                            logDebug("Subscriptions NOT AVAILABLE. Response: $response")
                            mSubscriptionsSupported = false
                            mSubscriptionUpdateSupported = false
                        }
                    }
                    mSetupDone = true
                } catch (e: RemoteException) {
                    listener.invoke(IabResult(IABHELPER_REMOTE_EXCEPTION,
                            "RemoteException while setting up in-app billing."))
                    e.printStackTrace()
                    return
                }
                listener.invoke(IabResult(BILLING_RESPONSE_RESULT_OK, "Setup successful."))
            }
        }
        val serviceIntent = Intent("com.android.vending.billing.InAppBillingService.BIND")
        serviceIntent.setPackage("com.android.vending")
        if (mContext!!.packageManager.queryIntentServices(serviceIntent, 0).isNullOrEmpty()) {
            // no service available to handle that Intent
            listener.invoke(
                    IabResult(BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE,
                            "Billing service unavailable on device."))
        } else {
            // service available to handle that Intent
            mContext!!.bindService(serviceIntent, mServiceConn!!, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Dispose of object, releasing resources. It's very important to call this
     * method when you are done with this object. It will release any resources
     * used by it such as service connections. Naturally, once the object is
     * disposed of, it can't be used again.
     */
    fun dispose() {
        logDebug("Disposing.")
        mSetupDone = false
        if (mServiceConn != null) {
            logDebug("Unbinding from service.")
            if (mContext != null) mContext!!.unbindService(mServiceConn!!)
        }
        mDisposed = true
        mContext = null
        mServiceConn = null
        mService = null
        mPurchaseListener = null
    }

    private fun checkNotDisposed() {
        check(!mDisposed) { "IabHelper was disposed of, so it cannot be used." }
    }

    /** Returns whether subscriptions are supported.  */
    fun subscriptionsSupported(): Boolean {
        checkNotDisposed()
        return mSubscriptionsSupported
    }

    /**
     * Callback that notifies when a purchase is finished.
     */
    interface OnIabPurchaseFinishedListener {
        /**
         * Called to notify that an in-app purchase finished. If the purchase was successful,
         * then the sku parameter specifies which item was purchased. If the purchase failed,
         * the sku and extraData parameters may or may not be null, depending on how far the purchase
         * process went.
         *
         * @param result The result of the purchase.
         * @param info The purchase information (null if purchase failed)
         */
        fun onIabPurchaseFinished(result: IabResult?, info: Purchase?)
    }

    // The listener registered on launchPurchaseFlow, which we have to call back when
    // the purchase finishes
    var mPurchaseListener: OnIabPurchaseFinishedListener? = null

    @JvmOverloads
    fun launchPurchaseFlow(act: Activity, sku: String, requestCode: Int,
                           listener: OnIabPurchaseFinishedListener?, extraData: String? = "") {
        launchPurchaseFlow(act, sku, ITEM_TYPE_INAPP, null, requestCode, listener, extraData)
    }

    @JvmOverloads
    fun launchSubscriptionPurchaseFlow(act: Activity, sku: String, requestCode: Int,
                                       listener: OnIabPurchaseFinishedListener?, extraData: String? = "") {
        launchPurchaseFlow(act, sku, ITEM_TYPE_SUBS, null, requestCode, listener, extraData)
    }

    /**
     * Initiate the UI flow for an in-app purchase. Call this method to initiate an in-app purchase,
     * which will involve bringing up the Google Play screen. The calling activity will be paused
     * while the user interacts with Google Play, and the result will be delivered via the
     * activity's [Activity.onActivityResult] method, at which point you must call
     * this object's [.handleActivityResult] method to continue the purchase flow. This method
     * MUST be called from the UI thread of the Activity.
     *
     * @param act The calling activity.
     * @param sku The sku of the item to purchase.
     * @param itemType indicates if it's a product or a subscription (ITEM_TYPE_INAPP or
     * ITEM_TYPE_SUBS)
     * @param oldSkus A list of SKUs which the new SKU is replacing or null if there are none
     * @param requestCode A request code (to differentiate from other responses -- as in
     * [Activity.startActivityForResult]).
     * @param listener The listener to notify when the purchase process finishes
     * @param extraData Extra data (developer payload), which will be returned with the purchase
     * data when the purchase completes. This extra data will be permanently bound to that
     * purchase and will always be returned when the purchase is queried.
     */
    fun launchPurchaseFlow(act: Activity, sku: String, itemType: String, oldSkus: List<String?>?,
                           requestCode: Int, listener: OnIabPurchaseFinishedListener?, extraData: String?) {
        checkNotDisposed()
        checkSetupDone("launchPurchaseFlow")
        flagStartAsync("launchPurchaseFlow")
        var result: IabResult
        if (itemType == ITEM_TYPE_SUBS && !mSubscriptionsSupported) {
            val r = IabResult(IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE,
                    "Subscriptions are not available.")
            flagEndAsync()
            listener?.onIabPurchaseFinished(r, null)
            return
        }
        try {
            logDebug("Constructing buy intent for $sku, item type: $itemType")
            val buyIntentBundle: Bundle
            buyIntentBundle = if (oldSkus == null || oldSkus.isEmpty()) {
                // Purchasing a new item or subscription re-signup
                mService!!.getBuyIntent(3, mContext!!.packageName, sku, itemType,
                        extraData)
            } else {
                // Subscription upgrade/downgrade
                if (!mSubscriptionUpdateSupported) {
                    val r = IabResult(IABHELPER_SUBSCRIPTION_UPDATE_NOT_AVAILABLE,
                            "Subscription updates are not available.")
                    flagEndAsync()
                    listener?.onIabPurchaseFinished(r, null)
                    return
                }
                mService!!.getBuyIntentToReplaceSkus(5, mContext!!.packageName,
                        oldSkus, sku, itemType, extraData)
            }
            val response = getResponseCodeFromBundle(buyIntentBundle)
            if (response != BILLING_RESPONSE_RESULT_OK) {
                logError("Unable to buy item, Error response: " + getResponseDesc(response))
                flagEndAsync()
                result = IabResult(response, "Unable to buy item")
                listener?.onIabPurchaseFinished(result, null)
                return
            }
            val pendingIntent = buyIntentBundle.getParcelable<PendingIntent>(RESPONSE_BUY_INTENT)
            logDebug("Launching buy intent for $sku. Request code: $requestCode")
            mRequestCode = requestCode
            mPurchaseListener = listener
            mPurchasingItemType = itemType
            act.startIntentSenderForResult(pendingIntent!!.intentSender,
                    requestCode, Intent(),
                    Integer.valueOf(0), Integer.valueOf(0),
                    Integer.valueOf(0))
        } catch (e: SendIntentException) {
            logError("SendIntentException while launching purchase flow for sku $sku")
            e.printStackTrace()
            flagEndAsync()
            result = IabResult(IABHELPER_SEND_INTENT_FAILED, "Failed to send intent.")
            listener?.onIabPurchaseFinished(result, null)
        } catch (e: RemoteException) {
            logError("RemoteException while launching purchase flow for sku $sku")
            e.printStackTrace()
            flagEndAsync()
            result = IabResult(IABHELPER_REMOTE_EXCEPTION, "Remote exception while starting purchase flow")
            listener?.onIabPurchaseFinished(result, null)
        }
    }

    /**
     * Handles an activity result that's part of the purchase flow in in-app billing. If you
     * are calling [.launchPurchaseFlow], then you must call this method from your
     * Activity's [@onActivityResult][Activity] method. This method
     * MUST be called from the UI thread of the Activity.
     *
     * @param requestCode The requestCode as you received it.
     * @param resultCode The resultCode as you received it.
     * @param data The data (Intent) as you received it.
     * @return Returns true if the result was related to a purchase flow and was handled;
     * false if the result was not related to a purchase, in which case you should
     * handle it normally.
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        var result: IabResult
        if (requestCode != mRequestCode) return false
        checkNotDisposed()
        checkSetupDone("handleActivityResult")

        // end of async purchase operation that started on launchPurchaseFlow
        flagEndAsync()
        if (data == null) {
            logError("Null data in IAB activity result.")
            result = IabResult(IABHELPER_BAD_RESPONSE, "Null data in IAB result")
            if (mPurchaseListener != null) mPurchaseListener!!.onIabPurchaseFinished(result, null)
            return true
        }
        val responseCode = getResponseCodeFromIntent(data)
        val purchaseData = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA)
        val dataSignature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE)
        if (resultCode == Activity.RESULT_OK && responseCode == BILLING_RESPONSE_RESULT_OK) {
            logDebug("Successful resultcode from purchase activity.")
            logDebug("Purchase data: $purchaseData")
            logDebug("Data signature: $dataSignature")
            logDebug("Extras: " + data.extras)
            logDebug("Expected item type: $mPurchasingItemType")
            if (purchaseData == null || dataSignature == null) {
                logError("BUG: either purchaseData or dataSignature is null.")
                logDebug("Extras: " + data.extras.toString())
                result = IabResult(IABHELPER_UNKNOWN_ERROR, "IAB returned null purchaseData or dataSignature")
                if (mPurchaseListener != null) mPurchaseListener!!.onIabPurchaseFinished(result, null)
                return true
            }
            var purchase: Purchase? = null
            try {
                purchase = Purchase(mPurchasingItemType!!, purchaseData, dataSignature)
                val sku = purchase.sku

                // Verify signature
                if (!verifyPurchase(mSignatureBase64, purchaseData, dataSignature)) {
                    logError("Purchase signature verification FAILED for sku $sku")
                    result = IabResult(IABHELPER_VERIFICATION_FAILED, "Signature verification failed for sku $sku")
                    if (mPurchaseListener != null) mPurchaseListener!!.onIabPurchaseFinished(result, purchase)
                    return true
                }
                logDebug("Purchase signature successfully verified.")
            } catch (e: JSONException) {
                logError("Failed to parse purchase data.")
                e.printStackTrace()
                result = IabResult(IABHELPER_BAD_RESPONSE, "Failed to parse purchase data.")
                if (mPurchaseListener != null) mPurchaseListener!!.onIabPurchaseFinished(result, null)
                return true
            }
            if (mPurchaseListener != null) {
                mPurchaseListener!!.onIabPurchaseFinished(IabResult(BILLING_RESPONSE_RESULT_OK, "Success"), purchase)
            }
        } else if (resultCode == Activity.RESULT_OK) {
            // result code was OK, but in-app billing response was not OK.
            logDebug("Result code was OK but in-app billing response was not OK: " + getResponseDesc(responseCode))
            if (mPurchaseListener != null) {
                result = IabResult(responseCode, "Problem purchashing item.")
                mPurchaseListener!!.onIabPurchaseFinished(result, null)
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            logDebug("Purchase canceled - Response: " + getResponseDesc(responseCode))
            result = IabResult(IABHELPER_USER_CANCELLED, "User canceled.")
            if (mPurchaseListener != null) mPurchaseListener!!.onIabPurchaseFinished(result, null)
        } else {
            logError("Purchase failed. Result code: " + resultCode.toString()
                    + ". Response: " + getResponseDesc(responseCode))
            result = IabResult(IABHELPER_UNKNOWN_PURCHASE_RESPONSE, "Unknown purchase response.")
            if (mPurchaseListener != null) mPurchaseListener!!.onIabPurchaseFinished(result, null)
        }
        return true
    }

    @Throws(IabException::class)
    fun queryInventory(querySkuDetails: Boolean, moreSkus: List<String?>?): Inventory {
        return queryInventory(querySkuDetails, moreSkus, null)
    }

    /**
     * Queries the inventory. This will query all owned items from the server, as well as
     * information on additional skus, if specified. This method may block or take long to execute.
     * Do not call from a UI thread. For that, use the non-blocking version [.queryInventoryAsync].
     *
     * @param querySkuDetails if true, SKU details (price, description, etc) will be queried as well
     * as purchase information.
     * @param moreItemSkus additional PRODUCT skus to query information on, regardless of ownership.
     * Ignored if null or if querySkuDetails is false.
     * @param moreSubsSkus additional SUBSCRIPTIONS skus to query information on, regardless of ownership.
     * Ignored if null or if querySkuDetails is false.
     * @throws IabException if a problem occurs while refreshing the inventory.
     */
    @Throws(IabException::class)
    fun queryInventory(querySkuDetails: Boolean, moreItemSkus: List<String?>?,
                       moreSubsSkus: List<String?>?): Inventory {
        checkNotDisposed()
        checkSetupDone("queryInventory")
        return try {
            val inv = Inventory()
            var r = queryPurchases(inv, ITEM_TYPE_INAPP)
            if (r != BILLING_RESPONSE_RESULT_OK) {
                throw IabException(r, "Error refreshing inventory (querying owned items).")
            }
            if (querySkuDetails) {
                r = querySkuDetails(ITEM_TYPE_INAPP, inv, moreItemSkus)
                if (r != BILLING_RESPONSE_RESULT_OK) {
                    throw IabException(r, "Error refreshing inventory (querying prices of items).")
                }
            }

            // if subscriptions are supported, then also query for subscriptions
            if (mSubscriptionsSupported) {
                r = queryPurchases(inv, ITEM_TYPE_SUBS)
                if (r != BILLING_RESPONSE_RESULT_OK) {
                    throw IabException(r, "Error refreshing inventory (querying owned subscriptions).")
                }
                if (querySkuDetails) {
                    r = querySkuDetails(ITEM_TYPE_SUBS, inv, moreItemSkus)
                    if (r != BILLING_RESPONSE_RESULT_OK) {
                        throw IabException(r, "Error refreshing inventory (querying prices of subscriptions).")
                    }
                }
            }
            inv
        } catch (e: RemoteException) {
            throw IabException(IABHELPER_REMOTE_EXCEPTION, "Remote exception while refreshing inventory.", e)
        } catch (e: JSONException) {
            throw IabException(IABHELPER_BAD_RESPONSE, "Error parsing JSON response while refreshing inventory.", e)
        }
    }

    /**
     * Listener that notifies when an inventory query operation completes.
     */
    interface QueryInventoryFinishedListener {
        /**
         * Called to notify that an inventory query operation completed.
         *
         * @param result The result of the operation.
         * @param inv The inventory.
         */
        fun onQueryInventoryFinished(result: IabResult?, inv: Inventory?)
    }

    /**
     * Asynchronous wrapper for inventory query. This will perform an inventory
     * query as described in [.queryInventory], but will do so asynchronously
     * and call back the specified listener upon completion. This method is safe to
     * call from a UI thread.
     *
     * @param querySkuDetails as in [.queryInventory]
     * @param moreSkus as in [.queryInventory]
     * @param listener The listener to notify when the refresh operation completes.
     */
    fun queryInventoryAsync(querySkuDetails: Boolean, moreSkus: List<String?>?,
                            listener: QueryInventoryFinishedListener?) {
        val handler = Handler(Looper.getMainLooper())
        checkNotDisposed()
        checkSetupDone("queryInventory")
        flagStartAsync("refresh inventory")
        Thread {
            var result = IabResult(BILLING_RESPONSE_RESULT_OK, "Inventory refresh successful.")
            var inv: Inventory? = null
            try {
                inv = queryInventory(querySkuDetails, moreSkus)
            } catch (ex: IabException) {
                result = ex.result
            }
            flagEndAsync()
            val result_f = result
            val inv_f = inv
            if (!mDisposed && listener != null) {
                handler.post { listener.onQueryInventoryFinished(result_f, inv_f) }
            }
        }.start()
    }

    fun queryInventoryAsync(listener: QueryInventoryFinishedListener?) {
        queryInventoryAsync(true, null, listener)
    }

    fun queryInventoryAsync(querySkuDetails: Boolean, listener: QueryInventoryFinishedListener?) {
        queryInventoryAsync(querySkuDetails, null, listener)
    }

    /**
     * Consumes a given in-app product. Consuming can only be done on an item
     * that's owned, and as a result of consumption, the user will no longer own it.
     * This method may block or take long to return. Do not call from the UI thread.
     * For that, see [.consumeAsync].
     *
     * @param itemInfo The PurchaseInfo that represents the item to consume.
     * @throws IabException if there is a problem during consumption.
     */
    @Throws(IabException::class)
    fun consume(itemInfo: Purchase) {
        checkNotDisposed()
        checkSetupDone("consume")
        if (itemInfo.itemType != ITEM_TYPE_INAPP) {
            throw IabException(IABHELPER_INVALID_CONSUMPTION,
                    "Items of type '" + itemInfo.itemType + "' can't be consumed.")
        }
        try {
            val token = itemInfo.token
            val sku = itemInfo.sku
            if (token == null || token == "") {
                logError("Can't consume $sku. No token.")
                throw IabException(IABHELPER_MISSING_TOKEN, "PurchaseInfo is missing token for sku: "
                        + sku + " " + itemInfo)
            }
            logDebug("Consuming sku: $sku, token: $token")
            val response = mService!!.consumePurchase(3, mContext!!.packageName, token)
            if (response == BILLING_RESPONSE_RESULT_OK) {
                logDebug("Successfully consumed sku: $sku")
            } else {
                logDebug("Error consuming consuming sku " + sku + ". " + getResponseDesc(response))
                throw IabException(response, "Error consuming sku $sku")
            }
        } catch (e: RemoteException) {
            throw IabException(IABHELPER_REMOTE_EXCEPTION, "Remote exception while consuming. PurchaseInfo: $itemInfo", e)
        }
    }

    /**
     * Callback that notifies when a consumption operation finishes.
     */
    interface OnConsumeFinishedListener {
        /**
         * Called to notify that a consumption has finished.
         *
         * @param purchase The purchase that was (or was to be) consumed.
         * @param result The result of the consumption operation.
         */
        fun onConsumeFinished(purchase: Purchase?, result: IabResult?)
    }

    /**
     * Callback that notifies when a multi-item consumption operation finishes.
     */
    interface OnConsumeMultiFinishedListener {
        /**
         * Called to notify that a consumption of multiple items has finished.
         *
         * @param purchases The purchases that were (or were to be) consumed.
         * @param results The results of each consumption operation, corresponding to each
         * sku.
         */
        fun onConsumeMultiFinished(purchases: List<Purchase>?, results: List<IabResult>?)
    }

    /**
     * Asynchronous wrapper to item consumption. Works like [.consume], but
     * performs the consumption in the background and notifies completion through
     * the provided listener. This method is safe to call from a UI thread.
     *
     * @param purchase The purchase to be consumed.
     * @param listener The listener to notify when the consumption operation finishes.
     */
    fun consumeAsync(purchase: Purchase, listener: (purchase: Purchase?, result: IabResult?)-> Unit) {
        checkNotDisposed()
        checkSetupDone("consume")
        val purchases: MutableList<Purchase> = ArrayList()
        purchases.add(purchase)
        consumeAsyncInternal(purchases, listener, null)
    }

    /**
     * Same as [.consumeAsync], but for multiple items at once.
     * @param purchases The list of PurchaseInfo objects representing the purchases to consume.
     * @param listener The listener to notify when the consumption operation finishes.
     */
    fun consumeAsync(purchases: List<Purchase>, listener: OnConsumeMultiFinishedListener?) {
        checkNotDisposed()
        checkSetupDone("consume")
        consumeAsyncInternal(purchases, null, listener)
    }

    // Checks that setup was done; if not, throws an exception.
    fun checkSetupDone(operation: String) {
        if (!mSetupDone) {
            logError("Illegal state for operation ($operation): IAB helper is not set up.")
            throw IllegalStateException("IAB helper is not set up. Can't perform operation: $operation")
        }
    }

    // Workaround to bug where sometimes response codes come as Long instead of Integer
    fun getResponseCodeFromBundle(b: Bundle): Int {
        val o = b[RESPONSE_CODE]
        return if (o == null) {
            logDebug("Bundle with null response code, assuming OK (known issue)")
            BILLING_RESPONSE_RESULT_OK
        } else if (o is Int) o.toInt() else if (o is Long) o.toLong().toInt() else {
            logError("Unexpected type for bundle response code.")
            logError(o.javaClass.name)
            throw RuntimeException("Unexpected type for bundle response code: " + o.javaClass.name)
        }
    }

    // Workaround to bug where sometimes response codes come as Long instead of Integer
    fun getResponseCodeFromIntent(i: Intent): Int {
        val o = i.extras!![RESPONSE_CODE]
        return if (o == null) {
            logError("Intent with no response code, assuming OK (known issue)")
            BILLING_RESPONSE_RESULT_OK
        } else if (o is Int) o.toInt() else if (o is Long) o.toLong().toInt() else {
            logError("Unexpected type for intent response code.")
            logError(o.javaClass.name)
            throw RuntimeException("Unexpected type for intent response code: " + o.javaClass.name)
        }
    }

    fun flagStartAsync(operation: String) {
        check(!mAsyncInProgress) {
            "Can't start async operation (" +
                    operation + ") because another async operation(" + mAsyncOperation + ") is in progress."
        }
        mAsyncOperation = operation
        mAsyncInProgress = true
        logDebug("Starting async operation: $operation")
    }

    fun flagEndAsync() {
        logDebug("Ending async operation: $mAsyncOperation")
        mAsyncOperation = ""
        mAsyncInProgress = false
    }

    @Throws(JSONException::class, RemoteException::class)
    fun queryPurchases(inv: Inventory, itemType: String): Int {
        // Query purchases
        logDebug("Querying owned items, item type: $itemType")
        logDebug("Package name: " + mContext!!.packageName)
        var verificationFailed = false
        var continueToken: String? = null
        do {
            logDebug("Calling getPurchases with continuation token: $continueToken")
            val ownedItems = mService!!.getPurchases(3, mContext!!.packageName,
                    itemType, continueToken)
            val response = getResponseCodeFromBundle(ownedItems)
            logDebug("Owned items response: $response")
            if (response != BILLING_RESPONSE_RESULT_OK) {
                logDebug("getPurchases() failed: " + getResponseDesc(response))
                return response
            }
            if (!ownedItems.containsKey(RESPONSE_INAPP_ITEM_LIST)
                    || !ownedItems.containsKey(RESPONSE_INAPP_PURCHASE_DATA_LIST)
                    || !ownedItems.containsKey(RESPONSE_INAPP_SIGNATURE_LIST)) {
                logError("Bundle returned from getPurchases() doesn't contain required fields.")
                return IABHELPER_BAD_RESPONSE
            }
            val ownedSkus = ownedItems.getStringArrayList(
                    RESPONSE_INAPP_ITEM_LIST)
            val purchaseDataList = ownedItems.getStringArrayList(
                    RESPONSE_INAPP_PURCHASE_DATA_LIST)
            val signatureList = ownedItems.getStringArrayList(
                    RESPONSE_INAPP_SIGNATURE_LIST)
            for (i in purchaseDataList!!.indices) {
                val purchaseData = purchaseDataList[i]
                val signature = signatureList!![i]
                val sku = ownedSkus!![i]
                if (verifyPurchase(mSignatureBase64, purchaseData, signature)) {
                    logDebug("Sku is owned: $sku")
                    val purchase = Purchase(itemType, purchaseData, signature)
                    if (TextUtils.isEmpty(purchase.token)) {
                        logWarn("BUG: empty/null token!")
                        logDebug("Purchase data: $purchaseData")
                    }

                    // Record ownership and token
                    inv.addPurchase(purchase)
                } else {
                    logWarn("Purchase signature verification **FAILED**. Not adding item.")
                    logDebug("   Purchase data: $purchaseData")
                    logDebug("   Signature: $signature")
                    verificationFailed = true
                }
            }
            continueToken = ownedItems.getString(INAPP_CONTINUATION_TOKEN)
            logDebug("Continuation token: $continueToken")
        } while (!TextUtils.isEmpty(continueToken))
        return if (verificationFailed) IABHELPER_VERIFICATION_FAILED else BILLING_RESPONSE_RESULT_OK
    }

    @Throws(RemoteException::class, JSONException::class)
    fun querySkuDetails(itemType: String?, inv: Inventory, moreSkus: List<String?>?): Int {
        logDebug("Querying SKU details.")
        val skuList = ArrayList<String?>()
        skuList.addAll(inv.getAllOwnedSkus(itemType!!))
        if (moreSkus != null) {
            for (sku in moreSkus) {
                if (!skuList.contains(sku)) {
                    skuList.add(sku)
                }
            }
        }
        if (skuList.size == 0) {
            logDebug("queryPrices: nothing to do because there are no SKUs.")
            return BILLING_RESPONSE_RESULT_OK
        }

        // Split the sku list in blocks of no more than 20 elements.
        val packs = ArrayList<ArrayList<String?>>()
        var tempList: ArrayList<String?>
        val n = skuList.size / 20
        val mod = skuList.size % 20
        for (i in 0 until n) {
            tempList = ArrayList()
            for (s in skuList.subList(i * 20, i * 20 + 20)) {
                tempList.add(s)
            }
            packs.add(tempList)
        }
        if (mod != 0) {
            tempList = ArrayList()
            for (s in skuList.subList(n * 20, n * 20 + mod)) {
                tempList.add(s)
            }
            packs.add(tempList)
        }
        for (skuPartList in packs) {
            val querySkus = Bundle()
            querySkus.putStringArrayList(GET_SKU_DETAILS_ITEM_LIST, skuPartList)
            val skuDetails = mService!!.getSkuDetails(3, mContext!!.packageName,
                    itemType, querySkus)
            if (!skuDetails.containsKey(RESPONSE_GET_SKU_DETAILS_LIST)) {
                val response = getResponseCodeFromBundle(skuDetails)
                return if (response != BILLING_RESPONSE_RESULT_OK) {
                    logDebug("getSkuDetails() failed: " + getResponseDesc(response))
                    response
                } else {
                    logError("getSkuDetails() returned a bundle with neither an error nor a detail list.")
                    IABHELPER_BAD_RESPONSE
                }
            }
            val responseList = skuDetails.getStringArrayList(
                    RESPONSE_GET_SKU_DETAILS_LIST)
            for (thisResponse in responseList!!) {
                val d = SkuDetails(itemType, thisResponse!!)
                logDebug("Got sku details: $d")
                inv.addSkuDetails(d)
            }
        }
        return BILLING_RESPONSE_RESULT_OK
    }

    fun consumeAsyncInternal(purchases: List<Purchase>,
                             singleListener: ((purchase: Purchase?, result: IabResult?)-> Unit)?,
                             multiListener: OnConsumeMultiFinishedListener?) {
        val handler = Handler(Looper.getMainLooper())
        flagStartAsync("consume")
        Thread {
            val results: MutableList<IabResult> = ArrayList()
            for (purchase in purchases) {
                try {
                    consume(purchase)
                    results.add(IabResult(BILLING_RESPONSE_RESULT_OK, "Successful consume of sku " + purchase.sku))
                } catch (ex: IabException) {
                    results.add(ex.result)
                }
            }
            flagEndAsync()
            if (!mDisposed && singleListener != null) {
                handler.post { singleListener.invoke(purchases[0], results[0]) }
            }
            if (!mDisposed && multiListener != null) {
                handler.post { multiListener.onConsumeMultiFinished(purchases, results) }
            }
        }.start()
    }

    fun logDebug(msg: String?) {
        msg?.let { if (mDebugLog) Log.d(mDebugTag, it) }
    }

    fun logError(msg: String) {
        Log.e(mDebugTag, "In-app billing error: $msg")
    }

    fun logWarn(msg: String) {
        Log.w(mDebugTag, "In-app billing warning: $msg")
    }

    companion object {
        // Billing response codes
        const val BILLING_RESPONSE_RESULT_OK = 0
        const val BILLING_RESPONSE_RESULT_USER_CANCELED = 1
        const val BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE = 2
        const val BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3
        const val BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4
        const val BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5
        const val BILLING_RESPONSE_RESULT_ERROR = 6
        const val BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7
        const val BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8

        // IAB Helper error codes
        const val IABHELPER_ERROR_BASE = -1000
        const val IABHELPER_REMOTE_EXCEPTION = -1001
        const val IABHELPER_BAD_RESPONSE = -1002
        const val IABHELPER_VERIFICATION_FAILED = -1003
        const val IABHELPER_SEND_INTENT_FAILED = -1004
        const val IABHELPER_USER_CANCELLED = -1005
        const val IABHELPER_UNKNOWN_PURCHASE_RESPONSE = -1006
        const val IABHELPER_MISSING_TOKEN = -1007
        const val IABHELPER_UNKNOWN_ERROR = -1008
        const val IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE = -1009
        const val IABHELPER_INVALID_CONSUMPTION = -1010
        const val IABHELPER_SUBSCRIPTION_UPDATE_NOT_AVAILABLE = -1011

        // Keys for the responses from InAppBillingService
        const val RESPONSE_CODE = "RESPONSE_CODE"
        const val RESPONSE_GET_SKU_DETAILS_LIST = "DETAILS_LIST"
        const val RESPONSE_BUY_INTENT = "BUY_INTENT"
        const val RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA"
        const val RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE"
        const val RESPONSE_INAPP_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST"
        const val RESPONSE_INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST"
        const val RESPONSE_INAPP_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST"
        const val INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN"

        // Item types
        const val ITEM_TYPE_INAPP = "inapp"
        const val ITEM_TYPE_SUBS = "subs"

        // some fields on the getSkuDetails response bundle
        const val GET_SKU_DETAILS_ITEM_LIST = "ITEM_ID_LIST"
        const val GET_SKU_DETAILS_ITEM_TYPE_LIST = "ITEM_TYPE_LIST"

        /**
         * Returns a human-readable description for the given response code.
         *
         * @param code The response code
         * @return A human-readable string explaining the result code.
         * It also includes the result code numerically.
         */
        fun getResponseDesc(code: Int): String {
            val iab_msgs = ("0:OK/1:User Canceled/2:Unknown/" +
                    "3:Billing Unavailable/4:Item unavailable/" +
                    "5:Developer Error/6:Error/7:Item Already Owned/" +
                    "8:Item not owned").split("/".toRegex()).toTypedArray()
            val iabhelper_msgs = ("0:OK/-1001:Remote exception during initialization/" +
                    "-1002:Bad response received/" +
                    "-1003:Purchase signature verification failed/" +
                    "-1004:Send intent failed/" +
                    "-1005:User cancelled/" +
                    "-1006:Unknown purchase response/" +
                    "-1007:Missing token/" +
                    "-1008:Unknown error/" +
                    "-1009:Subscriptions not available/" +
                    "-1010:Invalid consumption attempt").split("/".toRegex()).toTypedArray()
            return if (code <= IABHELPER_ERROR_BASE) {
                val index = IABHELPER_ERROR_BASE - code
                if (index >= 0 && index < iabhelper_msgs.size) iabhelper_msgs[index] else "$code:Unknown IAB Helper Error"
            } else if (code < 0 || code >= iab_msgs.size) "$code:Unknown" else iab_msgs[code]
        }
    }

    /**
     * Creates an instance. After creation, it will not yet be ready to use. You must perform
     * setup by calling [.startSetup] and wait for setup to complete. This constructor does not
     * block and is safe to call from a UI thread.
     *
     * @param ctx Your application or Activity context. Needed to bind to the in-app billing service.
     * @param base64PublicKey Your application's public key, encoded in base64.
     * This is used for verification of purchase signatures. You can find your app's base64-encoded
     * public key in your application's page on Google Play Developer Console. Note that this
     * is NOT your "developer public key".
     */
    init {
        mContext = ctx.applicationContext
        mSignatureBase64 = base64PublicKey
        logDebug("IAB helper created.")
    }
}