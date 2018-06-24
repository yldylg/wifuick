package com.yl.wifuick;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;


public class WebActivity extends Activity {
    private WebView mWeb;
    private LuaSupport lua;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);
        mWeb = (WebView) findViewById(R.id.wv);

        Handler hdl = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == LuaSupport.MSG_TYPE_LUA) {
                    mWeb.loadUrl("javascript:postMessage('" + msg.obj + "','*');");
                }
            }
        };
        lua = new LuaSupport(this, hdl);
        new Thread(lua).start();

        mWeb.setWebViewClient(new WebViewClient());
        mWeb.setWebChromeClient(new WebChromeClient());
        mWeb.addJavascriptInterface(this, "app");

        WebSettings settings = mWeb.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        String appFilePath = getFilesDir().getPath();
        settings.setGeolocationEnabled(true);
        settings.setGeolocationDatabasePath(appFilePath);
        String appCachePath = getCacheDir().getAbsolutePath();
        settings.setAppCacheMaxSize(1024 * 1024 * 8);
        settings.setAppCachePath(appCachePath);
        settings.setAllowFileAccess(true);
        settings.setAppCacheEnabled(true);

        mWeb.loadUrl("file:///android_asset/test_ui/index.html");
        // mWeb.loadUrl("http://192.168.8.179:8080/");
        // lua.eval("require 'import'\nprint(Math:sin(2.3))\n");
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            showQuitMessage("确定要退出吗?");
            return true;
        }
        return true;
    }

    @JavascriptInterface
    public void showQuitMessage(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(WebActivity.this);
        builder.setMessage(msg);
        builder.setTitle("退出提示");
        builder.setPositiveButton("确认",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                });
        builder.setNegativeButton("取消",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        builder.create().show();
    }

    @JavascriptInterface
    public String eval(String s) {
        lua.eval(s);
        return null;
    }

    @JavascriptInterface
    public void toast(String s, int t) {
        Toast.makeText(WebActivity.this, s, t).show();
    }

}
