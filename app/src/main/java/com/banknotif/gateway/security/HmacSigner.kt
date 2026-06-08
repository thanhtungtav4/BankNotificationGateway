package com.banknotif.gateway.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HmacSigner {
    fun sign(timestamp: String, rawBody: String, secret: String): String {
        val data = "$timestamp.$rawBody"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
