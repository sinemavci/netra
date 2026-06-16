package com.netra.library

import com.netra.library.enums.OfflinePolicyAction
import com.netra.library.enums.SlowNetworkPolicyAction

data class NetraRequestConfig(
    val id: String,
    val url: String,
//    val method: String,
    val body: NetraRequestBody?,
    val headers: Map<String, String>? = emptyMap(),
    val offlinePolicy: OfflinePolicyAction?,
    val slowNetworkPolicy: SlowNetworkPolicyAction?,
    val cancelOnDispose: Boolean,
    val cache: Cache?,

)