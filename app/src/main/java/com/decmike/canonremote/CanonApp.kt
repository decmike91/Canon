package com.decmike.canonremote

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.decmike.canonremote.data.CameraRepository
import com.decmike.canonremote.jobs.ShootingService
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CanonApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        CameraRepository.init(this)
        com.decmike.canonremote.ble.BleRemote.init(this)
        ShootingService.createChannel(this)
    }

    /** Les appareils supportent peu de requêtes simultanées : on limite à 2. */
    override fun newImageLoader(): ImageLoader {
        val dispatcher = Dispatcher().apply { maxRequestsPerHost = 2 }
        val http = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(http)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("thumbs")).maxSizeBytes(200L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
    }
}
