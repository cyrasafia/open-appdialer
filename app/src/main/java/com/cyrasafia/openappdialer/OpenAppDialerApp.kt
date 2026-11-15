package com.cyrasafia.openappdialer

import android.app.Application
import com.cyrasafia.openappdialer.data.AppIconCache
import com.cyrasafia.openappdialer.data.AppRepository
import com.cyrasafia.openappdialer.data.AppsCache
import com.cyrasafia.openappdialer.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** 应用级依赖容器：Activity 与 PanelService 共用同一份数据与缓存 */
class OpenAppDialerApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: AppRepository by lazy {
        AppRepository(
            pm = packageManager,
            cache = AppsCache(File(filesDir, AppsCache.FILE_NAME)),
            scope = appScope,
            excludePackage = packageName,
        )
    }

    val iconCache: AppIconCache by lazy { AppIconCache(packageManager) }

    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        repository.ensureLoaded()
    }
}
