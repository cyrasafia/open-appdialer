package com.cyrasafia.openappdialer

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cyrasafia.openappdialer.data.AppOrdering.orderForEmptyState
import com.cyrasafia.openappdialer.data.AppRepository
import com.cyrasafia.openappdialer.data.AppsCache
import com.cyrasafia.openappdialer.data.PackageEventReceiver
import com.cyrasafia.openappdialer.ui.theme.OpenAppDialerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * 浮窗形态主界面（spec F8）。
 * M1 阶段：临时列表用于验证数据层（缓存加载/包事件/空态排序），M3 替换为键盘 + 分页结果。
 */
class MainActivity : ComponentActivity() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val repository by lazy {
        AppRepository(
            pm = packageManager,
            cache = AppsCache(File(filesDir, AppsCache.FILE_NAME)),
            scope = appScope,
        )
    }

    private val packageReceiver = PackageEventReceiver { action, pkg ->
        repository.onPackageEvent(action, pkg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository.ensureLoaded()
        setContent {
            OpenAppDialerTheme {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .heightIn(max = 560.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    val apps by repository.apps.collectAsState()
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            text = "M1 · ${apps.size} apps",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LazyColumn {
                            items(
                                apps.orderForEmptyState(System.currentTimeMillis()),
                                key = { it.packageName },
                            ) { app ->
                                Text(
                                    text = app.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addDataScheme("package")
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        repository.ensureLoaded() // resume 兜底（spec F1：防漏广播）
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(packageReceiver)
    }
}
