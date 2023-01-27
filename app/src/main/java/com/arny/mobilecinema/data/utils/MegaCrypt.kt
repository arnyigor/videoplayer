package com.arny.mobilecinema.data.utils

import java.nio.ByteBuffer
import java.util.Arrays

object MegaCrypt {
    private val CA =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()
    private val IA = IntArray(256)

    init {
        Arrays.fill(IA, -1)
        var i = 0
        val iS = CA.size
        while (i < iS) {
            IA[CA[i].code] = i
            i++
        }
        IA['='.code] = 0
    }

    fun aIntToAByte(vararg intKey: Int): ByteArray {
        val buffer = ByteArray(intKey.size * 4)
        val bb = ByteBuffer.wrap(buffer)
        for (i in intKey.indices) {
            bb.putInt(intKey[i])
        }
        return bb.array()
    }

    fun aByteToAInt(bytes: ByteArray?): IntArray {
        val bb = ByteBuffer.wrap(bytes!!)
        val res = IntArray(bytes.size / 4)
        for (i in res.indices) {
            res[i] = bb.getInt(i * 4)
        }
        return res
    }

    fun base64UrlDecodeByte(strIn: String?): ByteArray? {
        var str = strIn
        str += "==".substring(2 - str!!.length * 3 and 3)
        str = str.replace("-", "+").replace("_", "/").replace(",", "")
        // Check special case
        val sLen = str.length
        if (sLen == 0) {
            return ByteArray(0)
        }
        // Count illegal characters (including '\r', '\n') to know what size the returned array will be,
        // so we don't have to reallocate & copy it later.
        var sepCnt =
            0 // Number of separator characters. (Actually illegal characters, but that's a bonus...)
        for (i in 0 until sLen) {
            // If input is "pure" (I.e. no line separators or illegal chars) base64 this loop can be commented out.
            if (IA[str[i].code] < 0) sepCnt++
        }
        // Check so that legal chars (including '=') are evenly divideable by 4 as specified in RFC 2045.
        if ((sLen - sepCnt) % 4 != 0) {
            return null
        }
        // Count '=' at end
        var pad = 0
        var i = sLen
        while (i > 1 && IA[str[--i].code] <= 0) {
            if (str[i] == '=') pad++
        }
        val len = ((sLen - sepCnt) * 6 shr 3) - pad
        val dArr = ByteArray(len) // Preallocate byte[] of exact length
        var s = 0
        var d = 0
        while (d < len) {
            var i = 0
            var j = 0
            while (j < 4) {
                // j only increased if a valid char was found.
                val c = IA[str[s++].code]
                if (c >= 0) i = i or (c shl 18 - j * 6) else j--
                j++
            }
            // Add the bytes
            dArr[d++] = (i shr 16).toByte()
            if (d < len) {
                dArr[d++] = (i shr 8).toByte()
                if (d < len) dArr[d++] = i.toByte()
            }
        }
        return dArr
    }

}