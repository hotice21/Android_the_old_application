package com.eligo.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Eligo 主入口：用 WebView 加载本地打包的 H5 资源（assets/h5/index.html）。
 *
 * 设计说明：
 * 1. 离线打包阶段（当前）：所有 UI 资源内置 APK，无需网络即可查看适老化效果。
 * 2. 微信登录：现阶段通过 [WechatBridge] 暴露给 H5，调用 loginWithWechat() 会
 *    弹提示框告知"开发模式跳过"，并回写一个 mock 的 token 给 H5，
 *    方便手动测试整体流程。后续接入原生 SDK 时替换该桥实现即可。
 * 3. 返回键：拦截返回键交由 H5 路由处理（uni-app 内部 router.back）。
 */
class MainActivity : ComponentActivity() {

    internal lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        setContentView(webView)

        configureWebView()
        attachBridge()
        loadLocalAsset()
    }

    private fun configureWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = LocalAssetWebViewClient()
        webView.webChromeClient = DebugChromeClient()
        WebView.setWebContentsDebuggingEnabled(true)
    }

    private fun attachBridge() {
        webView.addJavascriptInterface(WechatBridge(this), "EligoBridge")
    }

    private fun loadLocalAsset() {
        val url = "file:///android_asset/h5/index.html"
        webView.loadUrl(url)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.apply {
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}

/**
 * 拦截 /api 请求以避免 file:// 协议下相对路径解析失败。
 *
 * 离线阶段所有 API 直接返回空 200，让 H5 的 mock 模式或错误处理生效，
 * 不影响 UI 浏览。
 */
private class LocalAssetWebViewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        // 让站内跳转留在 WebView
        return false
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return null
        val path = url.path ?: return null

        // 离线阶段仅拦截 file:// 协议下的相对 /api 请求并返回空 JSON 200；
        // http/https 指向真实后端（如 http://10.0.2.2:8080）的请求必须放行
        if (url.scheme == "file" && path.startsWith("/api/")) {
            val emptyJson = "{\"status\":\"UP\",\"mock\":true}".toByteArray()
            return WebResourceResponse(
                "application/json",
                "UTF-8",
                java.io.ByteArrayInputStream(emptyJson)
            )
        }
        return null
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        Log.e("EligoWebView", "load error: ${error?.description} ${request?.url}")
    }
}

private class DebugChromeClient : WebChromeClient() {
    override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
        Log.d("EligoConsole", "${message?.message()} [${message?.sourceId()}:${message?.lineNumber()}]")
        return true
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
        Log.d("EligoAlert", message ?: "")
        return false
    }
}

/**
 * 暴露给 H5 的桥接对象：
 *
 * 调用方式（在 H5 JS 里）：
 *   EligoBridge.loginWithWechat('{\"returnUrl\":\"/pages/plaza/index\"}')
 *
 * 现阶段：弹出原生对话框告知"开发模式跳过登录"，然后回调 mock 结果。
 */
class WechatBridge(private val activity: MainActivity) {
    @JavascriptInterface
    fun loginWithWechat(payloadJson: String) {
        android.util.Log.d("EligoBridge", "loginWithWechat called: $payloadJson")
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("开发模式")
                .setMessage("已跳过微信登录，直接以访客身份继续。\n\n后续接入微信 SDK 后，此处会唤起微信客户端完成授权。")
                .setPositiveButton("继续") { _, _ ->
                    activity.webView.post {
                        activity.webView.evaluateJavascript(
                            "window.dispatchEvent(new Event('eligo:wechat-login-skipped'))",
                            null
                        )
                    }
                }
                .show()
        }
    }

    @JavascriptInterface
    fun isNativeBridgeAvailable(): Boolean = true
}
