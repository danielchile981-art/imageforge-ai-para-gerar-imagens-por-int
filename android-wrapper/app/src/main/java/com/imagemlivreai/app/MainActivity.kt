package com.imagemlivreai.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.imagemlivreai.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("imagemlivre_settings", MODE_PRIVATE) }
    private var targetUrl: String = BuildConfig.DEFAULT_GRADIO_URL

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        targetUrl = prefs.getString("backend_url", BuildConfig.DEFAULT_GRADIO_URL)
            ?: BuildConfig.DEFAULT_GRADIO_URL
        binding.urlInput.setText(targetUrl)

        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.statusText.text = "Conectando ao gerador…"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.statusText.text = "Conectado • gerador pronto"
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        binding.progressBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        showConnectionHelp(error?.description?.toString())
                    }
                }
            }
        }

        binding.swipeRefresh.setOnRefreshListener { binding.webView.reload() }
        binding.btnReload.setOnClickListener { binding.webView.reload() }
        binding.btnHome.setOnClickListener { showHome() }
        binding.btnInfo.setOnClickListener { showInfoDialog() }
        binding.btnConnect.setOnClickListener { connectToGenerator() }
    }

    private fun connectToGenerator() {
        var url = binding.urlInput.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            binding.urlInputLayout.error = "Digite o endereço do gerador"
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
            binding.urlInput.setText(url)
        }
        binding.urlInputLayout.error = null
        targetUrl = url
        prefs.edit().putString("backend_url", targetUrl).apply()
        showBrowser()
        binding.webView.loadUrl(targetUrl)
    }

    private fun showBrowser() {
        binding.homeContainer.visibility = View.GONE
        binding.browserContainer.visibility = View.VISIBLE
    }

    private fun showHome() {
        binding.browserContainer.visibility = View.GONE
        binding.homeContainer.visibility = View.VISIBLE
        binding.statusText.text = "Pronto para conectar"
    }

    private fun showInfoDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("ImagemLivre AI")
            .setMessage(
                "O Stable Diffusion 2.1-base continua rodando no projeto Python original, " +
                    "com o safety checker obrigatório. Este APK é a interface Android do gerador.\n\n" +
                    "No computador/servidor, rode: python app.py\n\n" +
                    "Depois informe neste app a URL exibida pelo Gradio. Para uso na mesma rede, " +
                    "use o IP do computador, por exemplo: http://192.168.0.10:7860"
            )
            .setPositiveButton("Entendi", null)
            .show()
    }

    private fun showConnectionHelp(errorMessage: String?) {
        val error = if (errorMessage.isNullOrBlank()) "" else "\n\nDetalhe: $errorMessage"
        MaterialAlertDialogBuilder(this)
            .setTitle("Não consegui abrir o gerador")
            .setMessage(
                "Confira se o app.py está rodando e se o endereço informado está correto. " +
                    "Se o gerador estiver em outro aparelho/computador, ambos devem conseguir se comunicar pela rede.$error"
            )
            .setPositiveButton("Editar endereço") { _, _ -> showHome() }
            .setNegativeButton("Tentar novamente") { _, _ -> binding.webView.reload() }
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            binding.browserContainer.visibility == View.VISIBLE && binding.webView.canGoBack() -> binding.webView.goBack()
            binding.browserContainer.visibility == View.VISIBLE -> showHome()
            else -> super.onBackPressed()
        }
    }
}
