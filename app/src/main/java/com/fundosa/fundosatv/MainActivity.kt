package com.fundosa.fundosatv // 确保包名与你的项目一致

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class MainActivity : FragmentActivity() {

    // ImageView 控件，声明为 lateinit，在 onCreate 中初始化
    private lateinit var imageViewDisplay: ImageView

    companion object {
        private const val PREFS_NAME = "fundosa_tv_prefs"
        private const val KEY_LOCAL_IMAGE_VERSION = "local_image_version"
        // !!! 请确保这里的 URL 是你的版本日志文件的真实可访问 URL !!!
        private const val IMAGE_VERSION_LOG_URL = "https://www.fundosa.com/fundosaTV/img/version.json"
        // !!! 默认图片 URL，当版本日志不可用或日志中未指定 URL 时使用 !!!
        private const val DEFAULT_IMAGE_URL = "https://www.fundosa.com/fundosaTV/img/spare.jpg"
        private const val TAG = "MainActivityFundosa" // 日志标签
    }

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 设置全屏
        hideSystemUI()

        // 2. 设置布局文件
        setContentView(R.layout.activity_main)

        // 3. 初始化 ImageView 控件
        imageViewDisplay = findViewById(R.id.imageView) // 确保 ID 与 XML 中的一致

        // 4. 初始化 SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 5. 启动协程检查并更新图片
        lifecycleScope.launch {
            checkAndUpdateImages()
        }
    }

    private suspend fun checkAndUpdateImages() {
        val localVersion = getLocalImageVersion()
        var serverImageUrl = DEFAULT_IMAGE_URL // 默认为 DEFAULT_IMAGE_URL
        var serverVersion = localVersion // 默认为本地版本，防止网络请求失败

        Log.d(TAG, "Starting image version check. Local version: $localVersion")

        try {
            val versionJsonString = fetchVersionLog() // 这是一个挂起函数
            if (versionJsonString != null) {
                Log.d(TAG, "Fetched version log: $versionJsonString")
                val jsonObject = JSONObject(versionJsonString)
                serverVersion = jsonObject.optString("currentImageVersion", localVersion ?: "unknown") // 如果本地版本为null，提供一个默认值
                // 从JSON中获取图片URL，如果不存在，则使用DEFAULT_IMAGE_URL
                serverImageUrl = jsonObject.optString("imageUrl", DEFAULT_IMAGE_URL)
                if (serverImageUrl.isEmpty()) { // 以防 "imageUrl": "" 的情况
                    serverImageUrl = DEFAULT_IMAGE_URL
                }
                Log.d(TAG, "Parsed server version: $serverVersion, Server image URL: $serverImageUrl")
            } else {
                Log.w(TAG, "Failed to fetch version log, using default image URL and local version.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching or parsing version log. Using default image URL.", e)
            // 网络错误或解析错误，使用默认图片URL和之前的本地版本（或触发更新如果本地版本为空）
        }

        // 条件判断逻辑：
        // 1. 本地版本为空 (首次启动或清除缓存后) -> 需要加载
        // 2. 服务器版本与本地版本不同 -> 需要更新
        if (localVersion == null || serverVersion != localVersion) {
            Log.i(TAG, "New image version detected ('$serverVersion') or first run. Updating image from: $serverImageUrl")
            loadImageWithGlide(serverImageUrl, serverVersion) // 使用从服务器获取的或默认的 URL
            if (serverVersion != localVersion) { // 只有当版本确实变化时才保存，避免首次启动localVersion为null时错误保存
                saveLocalImageVersion(serverVersion.toString())
            }
        } else {
            Log.i(TAG, "Image version ('$localVersion') is up to date. Loading image from: $serverImageUrl")
            // 版本相同，也加载一次图片（确保图片已显示），使用本地版本号和服务器（或默认）图片URL
            // 这样可以确保即使没有版本变化，图片也会被加载（例如，应用被杀死后重启）
            loadImageWithGlide(serverImageUrl, localVersion)
        }
    }

    private suspend fun fetchVersionLog(): String? {
        return withContext(Dispatchers.IO) { // 切换到 IO 线程执行网络请求
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(IMAGE_VERSION_LOG_URL)
                .header("Cache-Control", "no-cache, no-store, must-revalidate") // 强制不缓存版本文件本身
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        Log.e(TAG, "Failed to fetch version log, response code: ${response.code}")
                        null
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error fetching version log", e)
                null
            }
        }
    }

    private fun saveLocalImageVersion(version: String) {
        sharedPreferences.edit().putString(KEY_LOCAL_IMAGE_VERSION, version).apply()
        Log.d(TAG, "Saved local image version: $version")
    }

    private fun getLocalImageVersion(): String? {
        val version = sharedPreferences.getString(KEY_LOCAL_IMAGE_VERSION, null)
        Log.d(TAG, "Retrieved local image version: $version")
        return version
    }

    private fun loadImageWithGlide(baseUrl: String, version: String?) {
        // 确保 Activity 仍然有效
        if (isDestroyed || isFinishing) {
            Log.w(TAG, "Activity is finishing or destroyed, skipping Glide load.")
            return
        }

        // 通过在 URL 后附加版本号作为查询参数，让 Glide 将其视为不同的 URL
        // 这样，当版本号改变时，Glide 会重新下载图片而不是使用旧缓存
        val imageUrlWithVersion = if (version != null && version != "unknown" && baseUrl.startsWith("http")) {
            // 只有当 version 有效且 baseUrl 是网络路径时才添加版本参数
            if (baseUrl.contains("?")) "$baseUrl&v=$version" else "$baseUrl?v=$version"
        } else {
            baseUrl // 如果没有版本号，或不是有效版本，或不是网络图片，使用基础 URL
        }

        Log.d(TAG, "Glide loading image from: $imageUrlWithVersion")

        Glide.with(this)
            .load(imageUrlWithVersion)
            .placeholder(R.drawable.spare) // 加载时的占位图 (你的备用图)
            .error(R.drawable.spare)       // 加载失败时显示的图片 (你的备用图)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC) // 自动选择缓存策略，通常是 RESOURCE 或 DATA
            // DiskCacheStrategy.NONE - 完全不缓存到磁盘
            // DiskCacheStrategy.DATA - 只缓存原始下载数据
            // DiskCacheStrategy.RESOURCE - 只缓存解码后的资源
            // DiskCacheStrategy.ALL - 缓存所有版本（源数据和解码后数据）
            // DiskCacheStrategy.AUTOMATIC - Glide 自动选择
            // 对于版本控制的 URL，AUTOMATIC 通常意味着如果 URL 变了，它会重新下载。
            .skipMemoryCache(false) // 是否跳过内存缓存，通常设为 false 以获得更好性能
            .into(imageViewDisplay)
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
}
