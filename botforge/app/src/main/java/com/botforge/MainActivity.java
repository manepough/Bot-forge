package com.botforge;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start 24/7 foreground service immediately
        startBotService();

        webView = findViewById(R.id.webView);
        setupWebView();
    }

    private void startBotService() {
        Intent serviceIntent = new Intent(this, BotService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Allow JavaScript to call Android methods
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());

        // Load the app UI
        webView.loadUrl("file:///android_asset/index.html");
    }

    // Bridge: JavaScript <-> Android
    public class AndroidBridge {

        @JavascriptInterface
        public void deployBot(String botJson) {
            try {
                JSONObject bot = new JSONObject(botJson);
                String token  = bot.getString("token");
                String name   = bot.getString("name");
                // Pass to BotService
                Intent intent = new Intent(MainActivity.this, BotService.class);
                intent.setAction("ACTION_DEPLOY");
                intent.putExtra("token", token);
                intent.putExtra("name", name);
                startService(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void stopBot(String name) {
            Intent intent = new Intent(MainActivity.this, BotService.class);
            intent.setAction("ACTION_STOP");
            intent.putExtra("name", name);
            startService(intent);
        }

        @JavascriptInterface
        public String getRunningBots() {
            return BotService.getRunningBotsJson();
        }

        @JavascriptInterface
        public void sendUdpPing(String host, int port) {
            UdpKeepAlive.ping(host, port);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        // Service keeps running even if activity is destroyed
        super.onDestroy();
    }
}
