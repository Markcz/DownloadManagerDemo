package com.example.chenzhen.downloadmanagerdemo;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

/**
 * WebView 带下载功能
 */

public class JDActivity extends AppCompatActivity {

    private static final int WRITE_EXTERNAL_STORAGE_CODE = 102;
    WebView webView;
    ProgressBar mProgressBar;
    static String urlString = "http://android.myapp.com/myapp/detail.htm?apkName=com.shoujiduoduo.ringtone"; //铃声多多


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jd);
        init();
    }


    void init() {

        mProgressBar = findViewById(R.id.progress_bar);

        FrameLayout frameLayout = findViewById(R.id.webview_container);
        webView = new WebView(this);
        webView.loadUrl(urlString);
        webView.setWebViewClient(new MyWebClient(this));
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);//设置js可以直接打开窗口，如window.open()，默认为false
        webView.getSettings().setJavaScriptEnabled(true);//是否允许执行js，默认为false。设置true时，会提醒可能造成XSS漏洞
        webView.getSettings().setSupportZoom(true);//是否可以缩放，默认true
        webView.getSettings().setBuiltInZoomControls(true);//是否显示缩放按钮，默认false
        webView.getSettings().setUseWideViewPort(true);//设置此属性，可任意比例缩放。大视图模式
        webView.getSettings().setLoadWithOverviewMode(true);//和setUseWideViewPort(true)一起解决网页自适应问题
        webView.getSettings().setAppCacheEnabled(true);//是否使用缓存
        webView.getSettings().setDomStorageEnabled(true);//DOM Storage
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT
                , FrameLayout.LayoutParams.MATCH_PARENT);
        frameLayout.addView(webView, params);


        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(final String url, String userAgent, final String contentDisposition, String mimetype, final long contentLength) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //使用前先判断是否有读取、写入内存卡权限
                        mUrl = url;
                        if (ContextCompat.checkSelfPermission(JDActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(JDActivity.this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_CODE);
                        } else {

                            Log.e("onDownloadStart", "---- Dispo  " + contentDisposition + " ===== " + contentLength);
                            download(url);

                        }
                    }
                });


            }
        });

    }

    private void download(String url) {

        dispatch();

        downloadAPK(url, System.currentTimeMillis() + ".apk", getString(R.string.app_name));
    }

    private void dispatch() {

    }

    String mUrl;

    //下载apk
    public void downloadAPK(String url, String name, String desc) {

        //创建下载任务
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        //移动网络情况下是否允许漫游
        request.setAllowedOverRoaming(false);

        //在通知栏中显示，默认就是显示的
//        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
//        request.setTitle("下载");
//        request.setDescription(desc + "APP下载中...");
//        request.setVisibleInDownloadsUi(true);

        request.setVisibleInDownloadsUi(false);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);


        //设置下载的路径
        request.setDestinationInExternalPublicDir(getExternalCacheDir().getPath(), name);

        //获取DownloadManager
        downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        //将下载请求加入下载队列，加入下载队列后会给该任务返回一个long型的id，通过该id可以取消任务，重启任务、获取下载的文件等等
        downloadId = downloadManager.enqueue(request);

        //注册广播接收者，监听下载状态
        registerReceiver(receiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));


        listenProgress(); // 开始监听下载进度
    }

    //广播监听下载的各个状态
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            checkStatus();
        }
    };

    //检查下载状态
    private void checkStatus() {
        DownloadManager.Query query = new DownloadManager.Query();
        //通过下载的id查找
        query.setFilterById(downloadId);
        Cursor c = downloadManager.query(query);
        if (c.moveToFirst()) {
            int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
            Log.e("listenProgress"," --  " );
            switch (status) {
                //下载暂停
                case DownloadManager.STATUS_PAUSED:
                    break;
                //下载延迟
                case DownloadManager.STATUS_PENDING:
                    break;
                //正在下载
                case DownloadManager.STATUS_RUNNING:
                    Log.d("STATUS_RUNNING", "%%%%STATUS_RUNNING%%%%");
                    break;
                //下载完成
                case DownloadManager.STATUS_SUCCESSFUL:
                    cancel(); // 取消监听
                    Log.d("STATUS_RUNNING", "%%%%STATUS_SUCCESSFUL%%%%");
                    //下载完成安装APK
                    installAPK();
                    break;
                //下载失败
                case DownloadManager.STATUS_FAILED:
                    Toast.makeText(this, "下载失败", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }


    void cancel(){
        if (timer != null){
            timer.cancel();
            timer = null;
        }if (task != null){
            task.cancel();
            task = null;
        }
        Log.e("cancel"," --  ");
    }
    /***
     * 监视器 监听进度
     */
    Timer timer;
    TimerTask task;
    DownloadProgressListener listener = new DownloadProgressListener() {
        @Override
        public void downloadProgress(float progressPercent) {
            int progress = Math.round(progressPercent*100);
            Log.e("downloadProgress round"," --  " + Math.round(progressPercent*100));
            mProgressBar.setProgress(progress >= 90 ? 100 : progress);
        }
    };
    DownloadManager.Query query = new DownloadManager.Query();
    void listenProgress(){
        timer = new Timer("progress");
        task = new TimerTask() {
            @Override
            public void run() {
                queryDownloadProgress(query,listener);
            }
        };
        timer.schedule(task,0,800);// 每隔800ms查询一次
    }

    /***
     * 查询当前下载进度
     */
    public void queryDownloadProgress(DownloadManager.Query query,DownloadProgressListener listener) {
        if (query == null || listener == null){
            return;
        }
        if (downloadId != -1) {
            query.setFilterById(downloadId);
            query.setFilterByStatus(DownloadManager.STATUS_RUNNING);
            Cursor cur = downloadManager.query(query);
            if (cur != null) {
                if (cur.moveToFirst()) {
                    int currentSizeColumnIndex = cur.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    int totalSizeColumnIndex = cur.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    long currentSizeBytes = cur.getLong(currentSizeColumnIndex);
                    long totalSizeBytes = cur.getLong(totalSizeColumnIndex);
                    Log.e("STATUS_RUNNING ", " ++++++[总的文件大小 --byte]++++++" + totalSizeBytes);
                    Log.e("STATUS_RUNNING ", " ------[当前文件大小 --byte]------" + currentSizeBytes);
                    if (totalSizeBytes > 0) {
                        if (listener != null) {
                            float progressPercent = (float)currentSizeBytes / (float)totalSizeBytes;
                            listener.downloadProgress(progressPercent);
                        }
                    }
                }
                cur.close();
            }
        }
    }

    //下载到本地后执行安装
    private void installAPK() {

        // 判断是否有 安装未知应用权限  8.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean canInstall = getPackageManager().canRequestPackageInstalls();
            if (!canInstall) {
                startInstallPermissionSettingActivity();
            } else {
                install();
            }
        } else {
            install();
        }
    }

    /**
     * 8.0 请求安装权限
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startInstallPermissionSettingActivity() {
        //注意这个是8.0新API
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
        startActivityForResult(intent, 109);
    }


    /**
     * 开始安装
     */
    void install() {
        Intent intent = new Intent();
        File apkFile = queryDownloadedApk();
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {//7.0启动姿势
            uri = FileProvider.getUriForFile(this, Constant.FILE_PROVIDER_AUTHORITIES, apkFile);
            intent.setAction(Intent.ACTION_INSTALL_PACKAGE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);//7.0以后，系统要求授予临时uri读取权限，安装完毕以后，系统会自动收回权限，次过程没有用户交互
        } else {//7.0以下启动姿势
            uri = Uri.fromFile(apkFile);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        startActivity(intent);
    }


    private DownloadManager downloadManager;
    private long downloadId;

    /***
     * 查询 下载的apk文件
     * @return
     */
    public File queryDownloadedApk() {
        File targetApkFile = null;
        if (downloadId != -1) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            Cursor cur = downloadManager.query(query);
            if (cur != null) {
                if (cur.moveToFirst()) {
                    String uriString = cur.getString(cur.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    if (!uriString.isEmpty()) {
                        targetApkFile = new File(Uri.parse(uriString).getPath());
                    }
                }
                cur.close();
            }
        }
        return targetApkFile;
    }


    /**
     * 下载进度监听
     */
    public interface DownloadProgressListener {
        void downloadProgress(float progressPercent);
    }

    private static class MyWebClient extends WebViewClient {

        WeakReference<JDActivity> mWRef;

        public MyWebClient(JDActivity jdActivity) {
            this.mWRef = new WeakReference<>(jdActivity);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                finish();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if ((ContextCompat.checkSelfPermission(this, android.Manifest.permission
                .WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "未能获取到读写sd卡权限!", Toast.LENGTH_SHORT).show();
        } else {
            downloadAPK(mUrl, System.currentTimeMillis() + ".apk", "");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 109) {
            if (resultCode == RESULT_OK) {
                install();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.setVisibility(View.GONE);
            webView.removeAllViews();
            webView = null;
        }if (urlString != null) {
            urlString = null;
        }
        super.onDestroy();
        unregisterReceiver(receiver);
    }


}
