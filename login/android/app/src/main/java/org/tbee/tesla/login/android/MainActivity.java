package org.tbee.tesla.login.android;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import java.io.UnsupportedEncodingException;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class MainActivity extends AppCompatActivity {

    @SuppressLint("JavascriptInterface")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AtomicReference<String> authCodeRef = new AtomicReference<>(null);

        WebView webview = (WebView)findViewById(R.id.webView);

        Button loginButton = (Button)findViewById(R.id.login);
        loginButton.setOnClickListener(v -> webview.loadUrl("https://tesla.com/teslaaccount"));

        Button logoutButton = (Button)findViewById(R.id.logout);
        logoutButton.setOnClickListener(v -> webview.loadUrl("https://auth.tesla.com/oauth2/v1/logout?post_logout_redirect_uri=https://www.tesla.com/user/logout&client_id=ownership&locale=en-us&_ga=2.7382130.1721079298.1634815986-1444979.1620560111"));

        Button authTokensButton = (Button)findViewById(R.id.authTokens);
        authTokensButton.setOnClickListener(v -> {
            String url = "https://auth.tesla.com/oauth2/v3/token";
            // request body is JSON
            JsonObject requestJsonObject = new JsonObject();
            requestJsonObject.addProperty("grant_type", "authorization_code");
            requestJsonObject.addProperty("client_id", "ownerapi");
            //requestJsonObject.addProperty("code_verifier", codeVerifier);
            requestJsonObject.addProperty("code", authCodeRef.get());
            requestJsonObject.addProperty("redirect_uri", "https://auth.tesla.com/void/callback");
            String postData = requestJsonObject.toString();
            Log.i("tbeernot", postData);
            webview.postUrl(url,postData.getBytes());
        });

        // https://developer.android.com/reference/android/webkit/WebView
        getApplicationContext().deleteDatabase("webview.db");
        getApplicationContext().deleteDatabase("webviewCache.db");
        WebView.setWebContentsDebuggingEnabled(true);
        webview.getSettings().setJavaScriptEnabled(true);
        //webview.addJavascriptInterface(new MyJavaScriptInterface(this), "HtmlViewer");
        webview.getSettings().setBlockNetworkLoads(false);
        //webview.getSettings().setUserAgentString("Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.0.4) Gecko/20100101 Firefox/4.0");
        webview.clearFormData();
        webview.clearHistory();
        webview.clearMatches();
        webview.clearCache(true);

        webview.setWebViewClient(new WebViewClient(){

            @Override
            public void onLoadResource(WebView view, String url) {
                //Log.i("tbeernot", "onLoadResource: " + url);
                super.onLoadResource(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url){
                Log.i("tbeernot", "shouldOverrideUrlLoading1:" + url);
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest webResourceRequest){
                String url = "" + webResourceRequest.getUrl();
                Log.i("tbeernot", "shouldOverrideUrlLoading2:" + webResourceRequest.getMethod() + " " + url);
                Log.i("tbeernot", "shouldOverrideUrlLoading2:" + webResourceRequest.getRequestHeaders());
                //Log.i("tbeernot", "shouldOverrideUrlLoading2:" + url.isRedirect());

                // https://www.tesla.com/teslaaccount/owner-xp/auth/callback?code=fb05a21f7ebb35206af2b2337770380326da8992
                if (url.startsWith("https://www.tesla.com/teslaaccount/owner-xp/auth/callback?code=")) {
                    Matcher matcher = Pattern.compile("code=([^&]*)&").matcher(url);
                    String authorizationCode = (matcher.find() ? matcher.group().substring(5).replace("&", "") : "");
                    Log.i("tbeernot", "authorizationCode=" + authorizationCode);
                    authCodeRef.set(authorizationCode);
                }

                return super.shouldOverrideUrlLoading(view, webResourceRequest);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i("tbeernot", "onPageFinished:" + url);
                //webview.loadUrl("javascript:window.HtmlViewer.showHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');");
                webview.evaluateJavascript(
                        "(function() { return ('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'); })();",
                        new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String html) {
                                try {
                                    Log.i("tbeernot", html);
//                                    html = java.net.URLDecoder.decode(html, "UTF-8");
                                    // str = org.apache.commons.lang3.StringEscapeUtils.unescapeJava(str);
//                                    Log.i("tbeernot", html);
                                }
                                catch (Throwable e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                super.onPageFinished(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.i("tbeernot", "onPageStarted:" + url);
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.i("tbeernot", "boolean:" + detail);
                return super.onRenderProcessGone(view, detail);
            }
        });
        webview.loadUrl("https://www.tesla.com/?redirect=no");

//        webView.loadUrl("https://tesla.com/teslaaccount");
    }

    class MyJavaScriptInterface {

        private Context ctx;

        MyJavaScriptInterface(Context ctx) {
            this.ctx = ctx;
        }

        public void showHTML(String html) {
            Log.i("tbeernot", "showHTML:" + html);
        }
    }
}