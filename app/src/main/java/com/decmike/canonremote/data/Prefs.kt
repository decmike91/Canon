package com.decmike.canonremote.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("canon_remote", Context.MODE_PRIVATE)

    var lastHost: String
        get() = sp.getString("host", "192.168.1.2") ?: "192.168.1.2"
        set(v) = sp.edit().putString("host", v).apply()

    var port: Int
        get() = sp.getInt("port", 8080)
        set(v) = sp.edit().putInt("port", v).apply()

    var autoDownload: Boolean
        get() = sp.getBoolean("auto_download", false)
        set(v) = sp.edit().putBoolean("auto_download", v).apply()

    var autoDownloadRaw: Boolean
        get() = sp.getBoolean("auto_download_raw", false)
        set(v) = sp.edit().putBoolean("auto_download_raw", v).apply()

    var liveViewSize: String
        get() = sp.getString("lv_size", "small") ?: "small"
        set(v) = sp.edit().putString("lv_size", v).apply()

    var grid: Int  // 0 = aucune, 1 = tiers, 2 = carré 4x4, 3 = diagonales
        get() = sp.getInt("grid", 1)
        set(v) = sp.edit().putInt("grid", v).apply()

    var histogram: Boolean
        get() = sp.getBoolean("histogram", true)
        set(v) = sp.edit().putBoolean("histogram", v).apply()

    var cameraScreenOff: Boolean
        get() = sp.getBoolean("camera_screen_off", false)
        set(v) = sp.edit().putBoolean("camera_screen_off", v).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean("keep_screen_on", true)
        set(v) = sp.edit().putBoolean("keep_screen_on", v).apply()
}
