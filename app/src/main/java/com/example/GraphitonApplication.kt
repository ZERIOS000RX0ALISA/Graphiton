package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.GraphitonRepositoryImpl
import com.example.domain.repository.GraphitonRepository

class GraphitonApplication : Application(), ImageLoaderFactory {

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var repository: GraphitonRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        repository = GraphitonRepositoryImpl(
            context = this,
            preferencesManager = preferencesManager,
            database = database
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }
}
