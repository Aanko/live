package cc.slogc.live;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.tencent.rtmp.ITXLivePushListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;
import com.tencent.rtmp.ui.TXCloudVideoView;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CameraPusherActivity extends Activity implements ITXLivePushListener, TXLivePusher.OnBGMNotify {
    private static final String TAG = CameraPusherActivity.class.getSimpleName();
    /**
     * SDK 提供的类
     */
    private TXLivePushConfig mLivePushConfig;                // SDK 推流 config
    private TXLivePusher mLivePusher;                    // SDK 推流类
    private TXCloudVideoView mPusherView;                    // SDK 推流本地预览类

    /**
     * 控件
     */
    private TextView mTvNetBusyTips;                 // 网络繁忙Tips
    private EditText mEtRTMPURL;                     // RTMP URL链接的View
    private Button mBtnStartPush;                  // 开启推流的按钮
    private Button mBtnShowQRCode;                 // 显示播放二维码的按钮
    private LinearLayout mLlQrCode;                      // 二维码的布局
    private ImageView mIvRTMP, mIvFlv, mIvHls, mIvAccRTMP;                  // RTMP、FLV、HLS、ACCRTMP 二维码地址控件

    /**
     * 默认美颜参数
     */
    private int mBeautyLevel = 5;            // 美颜等级
    private int mBeautyStyle = TXLiveConstants.BEAUTY_STYLE_SMOOTH; // 美颜样式
    private int mWhiteningLevel = 3;            // 美白等级
    private int mRuddyLevel = 2;            // 红润等级

    /**
     * 其他参数
     */
    private int mCurrentVideoResolution = TXLiveConstants.VIDEO_RESOLUTION_TYPE_540_960;   // 当前分辨率
    private boolean mIsPushing;                     // 当前是否正在推流
    private Bitmap mWaterMarkBitmap;               // 水印


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera_pusher);
        checkPublishPermission();  // 检查权限
        initPusher();// 初始化 SDK 推流器
        initToolBottom();          // 初始化底部工具栏
        initMainView();
    }


    /**
     * 初始化 SDK 推流器
     */
    private void initPusher() {
        mLivePusher = new TXLivePusher(this);
        mLivePushConfig = new TXLivePushConfig();
        mLivePushConfig.setVideoEncodeGop(5);
        mLivePusher.setConfig(mLivePushConfig);
    }


    /////////////////////////////////////////////////////////////////////////////////
    //
    //                      View初始化相关
    //
    /////////////////////////////////////////////////////////////////////////////////


    /**
     * 初始化底部工具栏
     */
    private void initToolBottom() {
        mBtnStartPush = (Button) findViewById(R.id.pusher_btn_start);
        mBtnStartPush.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsPushing) {
                    boolean isSuccess = startRTMPPush();
                    Log.i("开始", "开始");
                } else {

                }
            }
        });
    }

    /**
     * 初始化 美颜、log、二维码 等 view
     */
    private void initMainView() {
        mPusherView = (TXCloudVideoView) findViewById(R.id.pusher_tx_cloud_view);
    }


    /**
     * 显示网络繁忙的提示
     */
    private void showNetBusyTips() {
        if (mTvNetBusyTips.isShown()) {
            return;
        }
        mTvNetBusyTips.setVisibility(View.VISIBLE);
        mTvNetBusyTips.postDelayed(new Runnable() {
            @Override
            public void run() {
                mTvNetBusyTips.setVisibility(View.GONE);
            }
        }, 5000);
    }


    /////////////////////////////////////////////////////////////////////////////////
    //
    //                      权限相关回调接口
    //
    /////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 100:
                for (int ret : grantResults) {
                    if (ret != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                }
                break;
            default:
                break;
        }
    }

    private boolean checkPublishPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)) {
                permissions.add(Manifest.permission.READ_PHONE_STATE);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(this,
                        permissions.toArray(new String[0]),
                        100);
                return false;
            }
        }
        return true;
    }


    /////////////////////////////////////////////////////////////////////////////////
    //
    //                      SDK 推流相关
    //
    /////////////////////////////////////////////////////////////////////////////////

    /**
     * 开始 RTMP 推流
     * <p>
     * 推荐使用方式：
     * 1. 配置好 {@link TXLivePushConfig} ， 配置推流参数
     * 2. 调用 {@link TXLivePusher#setConfig(TXLivePushConfig)} ，设置推流参数
     * 3. 调用 {@link TXLivePusher#startCameraPreview(TXCloudVideoView)} ， 开始本地预览
     * 4. 调用 {@link TXLivePusher#startPusher(String)} ， 发起推流
     * <p>
     * 注：步骤 3 要放到 2 之后，否则横屏推流、聚焦曝光、画面缩放功能配置不生效
     *
     * @return
     */
    private boolean startRTMPPush() {
        String tRTMPURL = "rtmp://livepush.slogc.cc/live/123?txSecret=9e94611969cb1658a924c51826aa8f30&txTime=5D7919FF";
//        String inputUrl = mEtRTMPURL.getText().toString();
//        if (!TextUtils.isEmpty(inputUrl)) {
//            String url[] = inputUrl.split("###");
//            if (url.length > 0) {
//                tRTMPURL = url[0];
//            }
//        }
//
//        if (TextUtils.isEmpty(tRTMPURL) || (!tRTMPURL.trim().toLowerCase().startsWith("rtmp://"))) {
//            Toast.makeText(getApplicationContext(), "推流地址不合法，目前支持rtmp推流!", Toast.LENGTH_SHORT).show();
//
//            // 输出状态log
//            Bundle params = new Bundle();
//            params.putString(TXLiveConstants.EVT_DESCRIPTION, "检查地址合法性");
//            return false;
//        }
        // 显示本地预览的View
        mPusherView.setVisibility(View.VISIBLE);

        // 输出状态log
        Bundle params = new Bundle();
        params.putString(TXLiveConstants.EVT_DESCRIPTION, "检查地址合法性");


        // 添加播放回调
        mLivePusher.setPushListener(this);
        mLivePusher.setBGMNofify(this);

        // 添加后台垫片推流参数
        mLivePushConfig.setPauseImg(300, 5);
        mLivePushConfig.setPauseFlag(TXLiveConstants.PAUSE_FLAG_PAUSE_VIDEO);// 设置暂停时，只停止画面采集，不停止声音采集。

        // 设置推流分辨率
        mLivePushConfig.setVideoResolution(mCurrentVideoResolution);

        // 设置美颜
        mLivePusher.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);

        // 设置推流配置
        mLivePusher.setConfig(mLivePushConfig);


        // 设置本地预览View
        mLivePusher.startCameraPreview(mPusherView);
        // 发起推流

        Log.i("tRTMPURL.trim()","tRTMPURL.trim():"+tRTMPURL.trim());

        int ret = mLivePusher.startPusher(tRTMPURL.trim());
        Log.i("啦啦啦啦啦啦啦啦","ret:"+ret);

        if (ret == -5) {
            String errInfo = "License 校验失败";
            int start = (errInfo + " 详情请点击[").length();
            int end = (errInfo + " 详情请点击[License 使用指南").length();
            SpannableStringBuilder spannableStrBuidler = new SpannableStringBuilder(errInfo + " 详情请点击[License 使用指南]");
            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent();
                    intent.setAction("android.intent.action.VIEW");
                    Uri content_url = Uri.parse("https://cloud.tencent.com/document/product/454/34750");
                    intent.setData(content_url);
                    startActivity(intent);
                }
            };
            spannableStrBuidler.setSpan(new ForegroundColorSpan(Color.BLUE), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannableStrBuidler.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView tv = new TextView(this);
            tv.setMovementMethod(LinkMovementMethod.getInstance());
            tv.setText(spannableStrBuidler);
            tv.setPadding(20, 0, 20, 0);
            AlertDialog.Builder dialogBuidler = new AlertDialog.Builder(this);
            dialogBuidler.setTitle("推流失败").setView(tv).setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            dialogBuidler.show();
            return false;
        }


        return true;
    }


    @Override
    public void onPushEvent(int i, Bundle bundle) {

    }

    @Override
    public void onNetStatus(Bundle bundle) {

    }

    @Override
    public void onBGMStart() {

    }

    @Override
    public void onBGMProgress(long l, long l1) {

    }

    @Override
    public void onBGMComplete(int i) {

    }
}
