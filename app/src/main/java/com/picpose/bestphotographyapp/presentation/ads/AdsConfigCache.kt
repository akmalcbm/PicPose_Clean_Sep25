package com.picpose.bestphotographyapp.presentation.ads

import android.content.Context
import com.google.gson.Gson
import com.picpose.bestphotographyapp.data.remote.AdsConfigResponse

class AdsConfigCache(context: Context) {

    private val prefs = context.getSharedPreferences("ads_config", Context.MODE_PRIVATE)

    fun save(config: AdsConfigResponse) {
        prefs.edit()
            .putString("json", Gson().toJson(config))
            .putLong("time", System.currentTimeMillis())
            .apply()
    }

    fun get(): AdsConfigResponse? {
        val json = prefs.getString("json", null) ?: return null
        return Gson().fromJson(json, AdsConfigResponse::class.java)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
