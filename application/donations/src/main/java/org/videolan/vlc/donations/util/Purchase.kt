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

import org.json.JSONObject

/**
 * Represents an in-app billing purchase.
 */
class Purchase(var itemType: String, private var originalJson: String, signature: String) {
    var orderId: String
    var packageName: String
    var sku: String
    var purchaseTime: Long
    var purchaseState: Int
    var developerPayload: String
    var token: String
    var signature: String
    var isAutoRenewing: Boolean

    override fun toString(): String {
        return "PurchaseInfo(type:$itemType):$originalJson"
    }

    init {
        val o = JSONObject(originalJson)
        orderId = o.optString("orderId")
        packageName = o.optString("packageName")
        sku = o.optString("productId")
        purchaseTime = o.optLong("purchaseTime")
        purchaseState = o.optInt("purchaseState")
        developerPayload = o.optString("developerPayload")
        token = o.optString("token", o.optString("purchaseToken"))
        isAutoRenewing = o.optBoolean("autoRenewing")
        this.signature = signature
    }
}