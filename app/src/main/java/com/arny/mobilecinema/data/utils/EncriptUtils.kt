package com.arny.mobilecinema.data.utils

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Base64
import java.io.UnsupportedEncodingException
import java.math.BigInteger
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Security
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.ShortBufferException
import javax.crypto.spec.SecretKeySpec

fun String.cleanAnwapEncryptedData(): String {
    val startData = this.trim()
    val trash = listOf(
        "RXJTdzNBc2k",
        "WXQ2cmpGZA",
        "Rmt",
        "pVTdoRw",
        "UlRk",
        "M1M2NUZn",
    )
    val trashRegex = trash.joinToString("|") { "(//)?$it=*" }
    val hasEndMultiEqual = startData.endsWith("====")
    val hasEndEqual = startData.endsWith("==")
    var replace = startData
    replace = replace.replace("^#[1-3]".toRegex(), "")
    replace = replace.replace(trashRegex.toRegex(), "__")
    replace = replace.replace("__".toRegex(), "")
    replace = replace.replace("//+".toRegex(), "")
    replace = replace.replace("=+\\b".toRegex(), "")
    replace = replace.replace(trashRegex.toRegex(), "")
    if (hasEndMultiEqual) {
        replace = "$replace=="
    }
    if (hasEndEqual && !replace.endsWith("=")) {
        replace = "$replace="
    }
    if (replace.endsWith("NTg4In0")) {
        replace = "$replace="
    }
    return replace
}

fun String.cleanEmptySymbols(): String {
    return this.replace("\t+".toRegex(), "")
        .replace("\n+".toRegex(), "")
        .replace("\\s+".toRegex(), " ")
        .trim()
}

fun String.getDecodedData(): String = try {
    String(Base64.decode(this))
} catch (e: Exception) {
    ""
}

fun md5(input: String): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
}

fun encrypt(strToEncrypt: String, secretKey: String): String? {
    Security.addProvider(BouncyCastleProvider())
    val keyBytes: ByteArray
    try {
        keyBytes = secretKey.toByteArray(charset("UTF8"))
        val skey = SecretKeySpec(keyBytes, "AES")
        val input = strToEncrypt.toByteArray(charset("UTF8"))
        synchronized(Cipher::class.java) {
            val cipher = Cipher.getInstance("AES/ECB/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, skey)

            val cipherText = ByteArray(cipher.getOutputSize(input.size))
            var ctLength = cipher.update(
                input, 0, input.size,
                cipherText, 0
            )
            ctLength += cipher.doFinal(cipherText, ctLength)
            return String(
                Base64.encode(cipherText)
            )
        }
    } catch (uee: UnsupportedEncodingException) {
        uee.printStackTrace()
    } catch (ibse: IllegalBlockSizeException) {
        ibse.printStackTrace()
    } catch (bpe: BadPaddingException) {
        bpe.printStackTrace()
    } catch (ike: InvalidKeyException) {
        ike.printStackTrace()
    } catch (nspe: NoSuchPaddingException) {
        nspe.printStackTrace()
    } catch (nsae: NoSuchAlgorithmException) {
        nsae.printStackTrace()
    } catch (e: ShortBufferException) {
        e.printStackTrace()
    }
    return null
}

fun decryptWithAES(key: String, strToDecrypt: String?): String? {
    Security.addProvider(BouncyCastleProvider())
    val keyBytes: ByteArray

    try {
        keyBytes = key.toByteArray(charset("UTF8"))
        val skey = SecretKeySpec(keyBytes, "AES")
        val input = Base64
            .decode(strToDecrypt?.trim { it <= ' ' }?.toByteArray(charset("UTF8")))

        synchronized(Cipher::class.java) {
            val cipher = Cipher.getInstance("AES/ECB/PKCS7Padding")
            cipher.init(Cipher.DECRYPT_MODE, skey)

            val plainText = ByteArray(cipher.getOutputSize(input.size))
            var ptLength = cipher.update(input, 0, input.size, plainText, 0)
            ptLength += cipher.doFinal(plainText, ptLength)
            val decryptedString = String(plainText)
            return decryptedString.trim { it <= ' ' }
        }
    } catch (uee: UnsupportedEncodingException) {
        uee.printStackTrace()
    } catch (ibse: IllegalBlockSizeException) {
        ibse.printStackTrace()
    } catch (bpe: BadPaddingException) {
        bpe.printStackTrace()
    } catch (ike: InvalidKeyException) {
        ike.printStackTrace()
    } catch (nspe: NoSuchPaddingException) {
        nspe.printStackTrace()
    } catch (nsae: NoSuchAlgorithmException) {
        nsae.printStackTrace()
    } catch (e: ShortBufferException) {
        e.printStackTrace()
    }
    return null
}