package org.tbee.tesla.login.android;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WebView webView = (WebView)findViewById(R.id.webView);
        Button button = (Button)findViewById(R.id.login);

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                webView.loadUrl("https://tesla.com/teslaaccount");
            }
        });


        // https://developer.android.com/reference/android/webkit/WebView
        WebView.setWebContentsDebuggingEnabled(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBlockNetworkLoads (false);
        webView.clearCache(true);
        webView.setWebViewClient(new WebViewClient(){

//            @Override
//            public void onLoadResource(WebView view, String url) {
//                Log.i("tbeernot", "onLoadResource: " + url);
//                super.onLoadResource(view, url);
//            }
//
//            @Override
//            public boolean shouldOverrideUrlLoading(WebView view, String url){
//                Log.i("tbeernot", "shouldOverrideUrlLoading1:" + url);
//                return super.shouldOverrideUrlLoading(view, url);
//            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest url){
                Log.i("tbeernot", "shouldOverrideUrlLoading2:" + url.getUrl());
                Log.i("tbeernot", "shouldOverrideUrlLoading2:" + url.getRequestHeaders());
                //Log.i("tbeernot", "shouldOverrideUrlLoading2:" + url.isRedirect());
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i("tbeernot", "onPageFinished:" + url);

//                if (url.equals("https://www.tesla.com/?redirect=no")) {
//                    webView.loadUrl("https://tesla.com/teslaaccount");
//                }
                super.onPageFinished(view, url);
            }

//            @Override
//            public void onPageStarted(WebView view, String url, Bitmap favicon) {
//                Log.i("tbeernot", "onPageStarted:" + url);
//                super.onPageStarted(view, url, favicon);
//            }
//
//            @Override
//            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
//                Log.i("tbeernot", "boolean:" + detail);
//                return super.onRenderProcessGone(view, detail);
//            }
        });
        webView.loadUrl("https://www.tesla.com/?redirect=no");

//        webView.loadUrl("https://tesla.com/teslaaccount");
    }
}