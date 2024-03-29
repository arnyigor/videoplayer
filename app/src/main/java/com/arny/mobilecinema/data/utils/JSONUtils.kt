package com.arny.mobilecinema.data.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

fun Any?.toJson(gson: Gson = GsonBuilder().setLenient().create()): String? {
    return if (this != null) gson.toJson(this) else null
}

fun <T> Any?.fromTypedJson(): T? {
    val typeToken = object : TypeToken<List<T>>() {}.type
    return Gson().fromJson(this.toString(), typeToken)
}

inline fun <reified T> String?.fromJson(cls: Class<T>, gson: Gson = GsonBuilder().setLenient().create()): T {
    return gson.fromJson(this, cls)
}

inline fun <reified T> String?.fromJsonToList(gson: Gson = GsonBuilder().setLenient().create()): List<T> {
    return gson.fromJson<List<T>>(this, ArrayList::class.java).orEmpty()
}

fun <T> String?.fromJson(clazz: Class<*>, deserialize: (JsonElement) -> T): T {
    val create = GsonBuilder()
        .setLenient()
        .registerTypeAdapter(
            clazz,
            JsonDeserializer { json, _, _ -> deserialize.invoke(json) }
        )
        .create()
    return create.fromJson<T>(this, clazz)
}

fun JSONObject.toMap(): HashMap<String, Any> {
    val mapParams = HashMap<String, Any>()
    try {
        for ((key, value) in jsonToMap(this)) {
            mapParams[key] = value
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return mapParams
}

@Throws(JSONException::class)
fun jsonToMap(json: JSONObject): Map<String, Any> {
    var retMap: Map<String, Any> = HashMap()
    if (json !== JSONObject.NULL) {
        retMap = toMap(json)
    }
    return retMap
}

@Throws(JSONException::class)
private fun toMap(obj: JSONObject): Map<String, Any> {
    val map = HashMap<String, Any>()
    val keysItr = obj.keys()
    while (keysItr.hasNext()) {
        val key = keysItr.next()
        map[key] = getMapValue(obj.get(key))
    }
    return map
}

@Throws(JSONException::class)
private fun toList(array: JSONArray): List<Any> {
    val list = ArrayList<Any>()
    for (i in 0 until array.length()) {
        list.add(getMapValue(array.get(i)))
    }
    return list
}

private fun getMapValue(value: Any): Any {
    return when (value) {
        is JSONArray -> toList(value)
        is JSONObject -> toMap(value)
        else -> value
    }
}