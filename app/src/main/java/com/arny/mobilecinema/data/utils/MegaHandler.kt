package com.arny.mobilecinema.data.utils

import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MegaHandler @Inject constructor() {
    private var id: Int

    init {
        id = Random().nextInt(Int.MAX_VALUE)
    }

    private fun apiRequest(data: String): String {
        val connection: HttpURLConnection?
        try {
            val urlString = "https://g.api.mega.co.nz/cs?id=$id"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST" //use post method
            connection.doOutput = true //we will send stuff
            connection.doInput = true //we want feedback
            connection.useCaches = false //no caches
            connection.allowUserInteraction = false
            connection.setRequestProperty("Content-Type", "text/xml")
            val out = connection.outputStream
            try {
                val wr = OutputStreamWriter(out)
                wr.write("[$data]") //data is JSON object containing the api commands
                wr.flush()
                wr.close()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally { //in this case, we are ensured to close the output stream
                out?.close()
            }
            val `in` = connection.inputStream
            val response = StringBuffer()
            try {
                val rd = BufferedReader(InputStreamReader(`in`))
                var line: String?
                while (rd.readLine().also { line = it } != null) {
                    response.append(line)
                }
                rd.close() //close the reader
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {  //in this case, we are ensured to close the input stream
                `in`?.close()
            }
            return response.toString().substring(1, response.toString().length - 1)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return ""
    }

    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        InvalidKeyException::class,
        InvalidAlgorithmParameterException::class,
        IOException::class,
        JSONException::class
    )
    fun download(path: String, fileId: String?, linkPart2: String?) {
        id = Random().nextInt(Int.MAX_VALUE)
        println("Download started")
        val fileKey = MegaCrypt.base64UrlDecodeByte(linkPart2)
        val intKey = MegaCrypt.aByteToAInt(fileKey)
        val json = JSONObject()
        try {
            json.put("a", "g")
            json.put("g", "1")
            json.put("p", fileId)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        val fileData = JSONObject(apiRequest(json.toString()))
        val keyNOnce = intArrayOf(
            intKey[0] xor intKey[4],
            intKey[1] xor intKey[5],
            intKey[2] xor intKey[6],
            intKey[3] xor intKey[7],
            intKey[4],
            intKey[5]
        )
        val key = MegaCrypt.aIntToAByte(keyNOnce[0], keyNOnce[1], keyNOnce[2], keyNOnce[3])
        val iiv = intArrayOf(keyNOnce[4], keyNOnce[5], 0, 0)
        val iv = MegaCrypt.aIntToAByte(*iiv)
        val ivSpec = IvParameterSpec(iv)
        val skeySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CTR/nopadding")
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec)
        var `is`: InputStream? = null
        var fileUrl: String? = null
        try {
            fileUrl = fileData.getString("g")
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        val fos = FileOutputStream(path)
        val cos: OutputStream = CipherOutputStream(fos, cipher)
        val decipher = Cipher.getInstance("AES/CTR/NoPadding")
        decipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec)
        var read: Int
        val buffer = ByteArray(32767)
        try {
            val urlConn = URL(fileUrl).openConnection()
            `is` = urlConn.getInputStream()
            while (`is`.read(buffer, 0, 1024).also { read = it } > 0) {
                cos.write(buffer, 0, read)
            }
        } finally {
            fos.use {
                cos.close()
                `is`?.close()
            }
        }
        println("Download finished")
    }
}