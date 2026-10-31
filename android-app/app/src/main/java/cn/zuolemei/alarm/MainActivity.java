package cn.zuolemei.alarm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.webkit.*;
import org.json.*;

public class MainActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        web=new WebView(this);
        web.setBackgroundColor(0xff111111);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.addJavascriptInterface(new Bridge(),"AlarmApp");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if(!"https://app.local/index.html".equals(r.getUrl().toString()))
                    return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
                try {return new WebResourceResponse("text/html","UTF-8",getAssets().open("index.html"));}
                catch(java.io.IOException e){throw new IllegalStateException(e);}
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsConfirm(WebView v,String url,String message,JsResult result){
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton("确认",(d,w)->result.confirm())
                    .setNegativeButton("取消",(d,w)->result.cancel())
                    .setOnCancelListener(d->result.cancel()).show();return true;
            }
        });
        setContentView(web);
        // Keep content clear of Android 15 edge-to-edge system bars.
        web.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        web.loadUrl("https://app.local/index.html");
    }
    @Override protected void onResume(){super.onResume(); if(web!=null)web.evaluateJavascript("if(window.refresh)refresh()",null);}
    @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("AlarmApp");web.destroy();}super.onDestroy();}
    private String result(Exception e){return e==null?"":(e.getMessage()==null?"操作失败":e.getMessage());}
    public class Bridge {
        @JavascriptInterface public String state(){
            try {
                JSONObject d=Store.read(MainActivity.this);
                d.put("exactAllowed",Store.allowed(MainActivity.this));
                NotificationManager nm=getSystemService(NotificationManager.class);
                d.put("notificationsAllowed",nm.areNotificationsEnabled());
                d.put("fullScreenAllowed",Build.VERSION.SDK_INT<34 || nm.canUseFullScreenIntent());
                return d.toString();
            }catch(Exception e){return "{\"error\":\"本地记录读取失败\"}";}
        }
        @JavascriptInterface public String add(String title,int minutes){
            try{
                if(!getSystemService(NotificationManager.class).areNotificationsEnabled())return "请先开启通知权限";
                Store.add(MainActivity.this,title,minutes);return "";
            }catch(Exception e){return result(e);}
        }
        @JavascriptInterface public String action(String id,int minutes,String activity){
            try {Store.act(MainActivity.this,id,minutes,activity);runOnUiThread(()->startService(new Intent(MainActivity.this,RingService.class).setAction("refresh")));return "";}
            catch(Exception e){return result(e);}
        }
        @JavascriptInterface public String remove(String id){
            try {Store.remove(MainActivity.this,id);runOnUiThread(()->startService(new Intent(MainActivity.this,RingService.class).setAction("refresh")));return "";}
            catch(Exception e){return result(e);}
        }
        @JavascriptInterface public void permissions(String kind){runOnUiThread(()->{
            try{
                if("notification".equals(kind)){
                    if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
                    else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
                }else if("exact".equals(kind) && Build.VERSION.SDK_INT>=31)
                    startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));
                else if("fullscreen".equals(kind) && Build.VERSION.SDK_INT>=34)
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));
            }catch(Exception e){new AlertDialog.Builder(MainActivity.this).setMessage("请到系统设置中的应用权限页面开启权限。").setPositiveButton("知道了",null).show();}
        });}
    }
}
