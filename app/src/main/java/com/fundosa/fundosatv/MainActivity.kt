package com.fundosa.fundosatv // 确保包名与你的项目一致

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog // 建议使用 AppCompatAlertDialog 以获得更好的样式
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope // 注意：对于更复杂的应用，建议使用 lifecycle-aware coroutine scopes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : FragmentActivity() {

    // !!! 重要：请将下面的 URL 替换为你公司图片的真实网络地址 !!!
    private val imageUrl = "https://www.fundosa.com/img/fundosaTV/public/TV1.jpg"

    // !!! 重要：请将下面的 URL 替换为你的版本信息文件的真实网络地址 !!!
    private val versionInfoUrl = "http://www.fundosa.com/updates/version_info.json"

    private lateinit var imageViewDisplay: ImageView
    private var downloadId: Long = -1L
    private var newApkPath: String? = null
    private lateinit var downloadCompleteReceiver: BroadcastReceiver

    // 用于处理“安装未知应用”权限请求的结果
    private val requestInstallPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> // result not directly used here, we re-check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (packageManager.canRequestPackageInstalls()) {
                    newApkPath?.let { installApk(it) } // 权限已授予，尝试安装
                } else {
                    Toast.makeText(this, "更新失败：未授予安装应用权限", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemUI()
        setContentView(R.layout.activity_main)

        imageViewDisplay = findViewById(R.id.imageView) // 确保 ID 与 XML 中的一致

        loadAndDisplayImage() // 加载图片
        checkForUpdates()     // 检查更新
    }

    private fun loadAndDisplayImage() {
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(android.R.drawable.screen_background_light)
                .error(R.drawable.spare) // 你指定的备用图片
                .into(imageViewDisplay)
        } else {
            imageViewDisplay.setImageResource(R.drawable.spare) // URL 无效时也显示备用图片
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }

    private fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            -1L // 发生错误时返回一个无效值
        }
    }

    private fun checkForUpdates() {
        GlobalScope.launch(Dispatchers.IO) { // 在后台线程执行网络请求
            try {
                Log.d("UpdateCheck", "开始检查更新，URL: $versionInfoUrl")
                val url = URL(versionInfoUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000 // 10秒连接超时
                connection.readTimeout = 10000    // 10秒读取超时

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }
                    Log.d("UpdateCheck", "服务器版本信息: $response")
                    val json = JSONObject(response)

                    val latestVersionCode = json.getLong("latestVersionCode")
                    val apkUrl = json.getString("apkUrl")
                    val releaseNotes = json.optString("releaseNotes", "发现新版本，建议更新！") // 可选的更新日志

                    val currentVersionCode = getCurrentVersionCode()
                    Log.d("UpdateCheck", "当前版本: $currentVersionCode, 最新版本: $latestVersionCode")

                    if (latestVersionCode > currentVersionCode) {
                        withContext(Dispatchers.Main) { // 切换回主线程显示对话框
                            showUpdateDialog(apkUrl, latestVersionCode.toString(), releaseNotes)
                        }
                    } else {
                        Log.d("UpdateCheck", "当前已是最新版本")
                        // 你可以在这里选择性地用 Toast 提示用户已是最新版本
                        // withContext(Dispatchers.Main) {
                        //     Toast.makeText(this@MainActivity, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                        // }
                    }
                } else {
                    Log.e("UpdateCheck", "检查更新失败，服务器响应码: ${connection.responseCode}")
                    // withContext(Dispatchers.Main) {
                    //     Toast.makeText(this@MainActivity, "检查更新失败: ${connection.responseCode}", Toast.LENGTH_LONG).show()
                    // }
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("UpdateCheck", "检查更新异常: ${e.message}", e)
                // withContext(Dispatchers.Main) {
                //    Toast.makeText(this@MainActivity, "检查更新异常: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                // }
            }
        }
    }

    private fun showUpdateDialog(apkUrl: String, newVersionName: String, releaseNotes: String) {
        AlertDialog.Builder(this)
            .setTitle("发现新版本 $newVersionName")
            .setMessage(releaseNotes)
            .setPositiveButton("立即更新") { dialog, _ ->
                downloadAndInstallApk(apkUrl)
                dialog.dismiss()
            }
            .setNegativeButton("暂不更新") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false) // 可以根据需要设置为 true 或 false
            .show()
    }

    private fun downloadAndInstallApk(apkUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                promptToAllowInstallUnknownApps()
                // 将apkUrl暂存，以便在用户授予权限后继续下载
                // 这里的逻辑可以优化，比如先提示，用户同意后再下载，或者下载后提示权限
                // 为了简化，我们先请求权限，如果权限通过，后续的 installApk 会直接安装
                // 如果你希望在权限授予后自动开始下载，需要更复杂的逻辑
                newApkPath = null // 清除旧路径，因为我们还没下载
                Toast.makeText(this, "请先允许安装未知应用权限后再尝试更新", Toast.LENGTH_LONG).show()
                return // 暂时返回，等待用户操作权限设置
            }
        }

        Toast.makeText(this, "开始下载更新...", Toast.LENGTH_SHORT).show()
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("应用更新")
            .setDescription("正在下载新版本 FundosaTV...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "FundosaTV_update.apk")
            // 或者使用缓存目录，这样卸载应用时会被清除
            // .setDestinationInExternalCacheDir(this, "apk_downloads/FundosaTV_update.apk")
            .setMimeType("application/vnd.android.package-archive")
            .setAllowedOverMetered(true) // 允许在计费网络下载
            .setAllowedOverRoaming(true) // 允许在漫游网络下载

        downloadId = downloadManager.enqueue(request)

        // 注册广播接收器以监听下载完成
        registerDownloadReceiver()
    }

    private fun promptToAllowInstallUnknownApps() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = Uri.parse("package:$packageName")
            try {
                requestInstallPermissionLauncher.launch(intent) // 使用 ActivityResultLauncher
            } catch (e: Exception) {
                Log.e("InstallPermission", "无法启动安装未知应用设置", e)
                Toast.makeText(this, "无法打开权限设置页面", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun registerDownloadReceiver() {
        downloadCompleteReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(id)
                    val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val downloadedApkUriString = cursor.getString(uriIndex)
                            if (downloadedApkUriString != null) {
                                val downloadedApkUri = Uri.parse(downloadedApkUriString)
                                // 从 content URI 获取真实文件路径
                                // 对于 DownloadManager 下载到 ExternalFilesDir 的文件，可以直接从 URI 获取 path
                                val file = File(Uri.parse(downloadedApkUriString).path!!) // 假设uri格式为 file:///...
                                newApkPath = file.absolutePath
                                Log.d("Download", "APK 下载成功，路径: $newApkPath")
                                installApk(newApkPath!!)
                            } else {
                                Toast.makeText(this@MainActivity, "下载成功但无法获取文件路径", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = cursor.getInt(reasonIndex)
                            Log.e("Download", "下载失败，状态: $status, 原因: $reason")
                            Toast.makeText(this@MainActivity, "下载更新失败，原因代码: $reason", Toast.LENGTH_LONG).show()
                        }
                    }
                    cursor.close()
                    unregisterReceiver(this) // 下载完成或失败后注销接收器
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadCompleteReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }


    private fun installApk(apkPath: String) {
        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            Toast.makeText(this, "APK 文件不存在: $apkPath", Toast.LENGTH_LONG).show()
            Log.e("InstallApk", "APK 文件不存在: $apkPath")
            return
        }

        // 再次检查安装权限 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                promptToAllowInstallUnknownApps() // 如果还没有权限，再次提示
                return
            }
        }

        val intent = Intent(Intent.ACTION_VIEW)
        val apkUri: Uri

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", apkFile)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            apkUri = Uri.fromFile(apkFile)
        }

        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 对于从非 Activity 上下文启动安装是必需的

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("InstallApk", "启动安装失败: ${e.message}", e)
            Toast.makeText(this, "启动安装失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 如果注册了下载接收器且没有在下载完成时注销，确保在这里注销
        // 但通常下载接收器应该在下载完成或失败后自行注销
        // if (::downloadCompleteReceiver.isInitialized) { // 检查是否已初始化
        //     try {
        //         unregisterReceiver(downloadCompleteReceiver)
        //     } catch (e: IllegalArgumentException) {
        //         // Receiver not registered
        //     }
        // }
    }
}
