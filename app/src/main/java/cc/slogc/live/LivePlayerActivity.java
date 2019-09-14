package cc.slogc.live;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.*;
import com.tencent.rtmp.ITXLivePlayListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePlayConfig;
import com.tencent.rtmp.TXLivePlayer;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.ugc.TXRecordCommon;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 腾讯云 {@link TXLivePlayer} 直播播放器使用参考 Demo
 * <p>
 * 有以下功能参考 ：
 * <p>
 * 1. 基本功能参考： 启动推流 {@link #startPlay()}与 结束推流 {@link #stopPlay()}
 * <p>
 * 2. 硬件加速： 使用硬解码
 * <p>
 * 3. 性能数据查看参考： {@link #onNetStatus(Bundle)}
 * <p>
 * 5. 处理 SDK 回调事件参考： {@link #onPlayEvent(int, Bundle)}
 * <p>
 * 6. 渲染角度、渲染模式切换： 横竖屏渲染、铺满与自适应渲染
 * <p>
 * 7. 缓存策略选择：{@link #setCacheStrategy} 缓存策略：自动、极速、流畅。 极速模式：时延会尽可能低、但抗网络抖动效果不佳；流畅模式：时延较高、抗抖动能力较强
 */
public class LivePlayerActivity extends Activity implements ITXLivePlayListener, OnClickListener {
    private static final String TAG = LivePlayerActivity.class.getSimpleName();

    private static final int CACHE_STRATEGY_FAST = 1;  //极速
    private static final int CACHE_STRATEGY_SMOOTH = 2;  //流畅
    private static final int CACHE_STRATEGY_AUTO = 3;  //自动

    private static final float CACHE_TIME_FAST = 1.0f;
    private static final float CACHE_TIME_SMOOTH = 5.0f;

    public static final int ACTIVITY_TYPE_PUBLISH = 1;
    public static final int ACTIVITY_TYPE_LIVE_PLAY = 2;
    public static final int ACTIVITY_TYPE_VOD_PLAY = 3;
    public static final int ACTIVITY_TYPE_LINK_MIC = 4;
    public static final int ACTIVITY_TYPE_REALTIME_PLAY = 5;

    private static final String NORMAL_PLAY_URL = "http://5815.liveplay.myqcloud.com/live/5815_89aad37e06ff11e892905cb9018cf0d4_900.flv";

    /**
     * SDK player 相关
     */
    private TXLivePlayer mLivePlayer = null;
    private TXLivePlayConfig mPlayConfig;
    private TXCloudVideoView mPlayerView;

    /**
     * 相关控件
     */
    private ImageView mLoadingView;
    private LinearLayout mRootView;
    private Button mBtnLog;
    private Button mBtnPlay;
    private Button mBtnRenderRotation;
    private Button mBtnRenderMode;
    private Button mBtnHWDecode;
    private Button mBtnAcc;
    private Button mBtnCacheStrategy;
    private Button mRatioFast;
    private Button mRatioSmooth;
    private Button mRatioAuto;
    private ProgressBar mRecordProgressBar;
    private TextView mRecordTimeTV;
    private LinearLayout mLayoutCacheStrategy;
    private EditText mRtmpUrlView;


    private int mPlayType = TXLivePlayer.PLAY_TYPE_LIVE_RTMP; // player 播放链接类型
    private int mCurrentRenderMode;                           // player 渲染模式
    private int mCurrentRenderRotation;                       // player 渲染角度
    private boolean mHWDecode = false;                          // 是否使用硬解码
    private int mCacheStrategy = 0;                           // player 缓存策略
    private boolean mIsAcc = false;                               // 播放加速流地址 (用于测试

    private boolean mIsPlaying;
    private long mStartPlayTS = 0;
    private int mActivityType;
    private boolean mRecordFlag = false;
    private boolean mCancelRecordFlag = false;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCurrentRenderMode = TXLiveConstants.RENDER_MODE_ADJUST_RESOLUTION;
        mCurrentRenderRotation = TXLiveConstants.RENDER_ROTATION_PORTRAIT;

        mActivityType = getIntent().getIntExtra("PLAY_TYPE", ACTIVITY_TYPE_LIVE_PLAY);

        mPlayConfig = new TXLivePlayConfig();

        setContentView();

    }


    private void setContentView() {
        setContentView(R.layout.activity_play);
        mIsPlaying = false;

//        开始播放
        mBtnPlay = (Button) findViewById(R.id.btnPlay);

        mBtnPlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "click playbtn isplay:" + mIsPlaying + " playtype:" + mPlayType);
                if (mIsPlaying) {
//                    stopPlay();
                } else {
                    mIsPlaying = startPlay();
                }
            }
        });

        mPlayerView = (TXCloudVideoView) findViewById(R.id.video_view);
        mPlayerView.setLogMargin(12, 12, 110, 60);
        mPlayerView.showLog(false);
        if (mLivePlayer == null) {
            mLivePlayer = new TXLivePlayer(this);
        }

    }

    private void startLoadingAnimation() {
        if (mLoadingView != null) {
            mLoadingView.setVisibility(View.VISIBLE);
            ((AnimationDrawable) mLoadingView.getDrawable()).start();
        }
    }

    private void stopLoadingAnimation() {
        if (mLoadingView != null) {
            mLoadingView.setVisibility(View.GONE);
            ((AnimationDrawable) mLoadingView.getDrawable()).stop();
        }
    }


    /////////////////////////////////////////////////////////////////////////////////
    //
    //                      Player 相关
    //
    /////////////////////////////////////////////////////////////////////////////////
    private boolean checkPlayUrl(final String playUrl) {
        if (TextUtils.isEmpty(playUrl) || (!playUrl.startsWith("http://") && !playUrl.startsWith("https://") && !playUrl.startsWith("rtmp://") && !playUrl.startsWith("/"))) {
            Toast.makeText(getApplicationContext(), "播放地址不合法，直播目前仅支持rtmp,flv播放方式!", Toast.LENGTH_SHORT).show();
            return false;
        }

        switch (mActivityType) {
            case ACTIVITY_TYPE_LIVE_PLAY: {
                if (playUrl.startsWith("rtmp://")) {
                    mPlayType = TXLivePlayer.PLAY_TYPE_LIVE_RTMP;
                } else if ((playUrl.startsWith("http://") || playUrl.startsWith("https://")) && playUrl.contains(".flv")) {
                    mPlayType = TXLivePlayer.PLAY_TYPE_LIVE_FLV;
                } else {
                    Toast.makeText(getApplicationContext(), "播放地址不合法，直播目前仅支持rtmp,flv播放方式!", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            break;
            case ACTIVITY_TYPE_REALTIME_PLAY: {
                if (!playUrl.startsWith("rtmp://")) {
                    Toast.makeText(getApplicationContext(), "低延时拉流仅支持rtmp播放方式", Toast.LENGTH_SHORT).show();
                    return false;
                } else if (!playUrl.contains("txSecret")) {
                    new AlertDialog.Builder(this)
                            .setTitle("播放出错")
                            .setMessage("低延时拉流地址需要防盗链签名，详情参考 https://cloud.tencent.com/document/product/454/7880#RealTimePlay!")
                            .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            }).setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Uri uri = Uri.parse("https://cloud.tencent.com/document/product/454/7880#RealTimePlay!");
                            startActivity(new Intent(Intent.ACTION_VIEW, uri));
                            dialog.dismiss();
                        }
                    }).show();
                    return false;
                }

                mPlayType = TXLivePlayer.PLAY_TYPE_LIVE_RTMP_ACC;
                break;
            }
            default:
                Toast.makeText(getApplicationContext(), "播放地址不合法，目前仅支持rtmp,flv播放方式!", Toast.LENGTH_SHORT).show();
                return false;
        }
        return true;
    }

    /**
     * 开始播放
     *
     * @return
     */
    private boolean startPlay() {
//        String playUrl = mRtmpUrlView.getText().toString();rtmp://liveplay.slogc.cc/live/123
        String playUrl = "rtmp://liveplay.slogc.cc/live/123";
        if (!checkPlayUrl(playUrl)) {
            Bundle params = new Bundle();
            params.putString(TXLiveConstants.EVT_DESCRIPTION, "检查地址合法性");
            return false;
        }
        Bundle params = new Bundle();
        params.putString(TXLiveConstants.EVT_DESCRIPTION, "检查地址合法性");

//        mRootView.setBackgroundColor(0xff000000);

        Log.i("mLivePlayer","mLivePlayer:"+mLivePlayer);
        Log.i("mPlayerView","mPlayerView:"+mPlayerView);


        mLivePlayer.setPlayerView(mPlayerView);

        mLivePlayer.setPlayListener(this);
        // 硬件加速在1080p解码场景下效果显著，但细节之处并不如想象的那么美好：
        // (1) 只有 4.3 以上android系统才支持
        // (2) 兼容性我们目前还仅过了小米华为等常见机型，故这里的返回值您先不要太当真
        mLivePlayer.enableHardwareDecode(mHWDecode);
        mLivePlayer.setRenderRotation(mCurrentRenderRotation);
        mLivePlayer.setRenderMode(mCurrentRenderMode);
        //设置播放器缓存策略
        //这里将播放器的策略设置为自动调整，调整的范围设定为1到4s，您也可以通过setCacheTime将播放器策略设置为采用
        //固定缓存时间。如果您什么都不调用，播放器将采用默认的策略（默认策略为自动调整，调整范围为1到4s）
        //mLivePlayer.setCacheTime(5);
        // HashMap<String, String> headers = new HashMap<>();
        // headers.put("Referer", "qcloud.com");
        // mPlayConfig.setHeaders(headers);
        mLivePlayer.setConfig(mPlayConfig);
        int result = mLivePlayer.startPlay(playUrl, mPlayType); // result返回值：0 success;  -1 empty url; -2 invalid url; -3 invalid playType;
        if (result != 0) {
//            mBtnPlay.setBackgroundResource(R.drawable.play_start);
//            mRootView.setBackgroundResource(R.drawable.main_bkg);
            Toast.makeText(getApplicationContext(), "拉流失败，", Toast.LENGTH_LONG).show();
            return false;
        }

        Log.w("video render", "timetrack start play");

    startLoadingAnimation();

//        enableQRCodeBtn(false);

    mStartPlayTS = System.currentTimeMillis();
        Toast.makeText(getApplicationContext(), "拉流成功，赶紧开始跳舞吧", Toast.LENGTH_LONG).show();
        return true;
}


    /////////////////////////////////////////////////////////////////////////////////
    //
    //                      权限检测相关
    //
    /////////////////////////////////////////////////////////////////////////////////
    private boolean checkPublishPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
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
    //                      Activity 声明周期相关
    //
    /////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBackPressed() {
//        stopPlay();
        super.onBackPressed();
    }


    @Override
    public void onStop() {
        super.onStop();
        mCancelRecordFlag = true;
//        streamRecord(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLivePlayer != null) {
            mLivePlayer.stopPlay(true);
            mLivePlayer = null;
        }
        if (mPlayerView != null) {
            mPlayerView.onDestroy();
            mPlayerView = null;
        }
        mPlayConfig = null;
        Log.d(TAG, "vrender onDestroy");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != 100 || data == null || data.getExtras() == null || TextUtils.isEmpty(data.getExtras().getString("result"))) {
            return;
        }
        String result = data.getExtras().getString("result");
        if (mRtmpUrlView != null) {
            mRtmpUrlView.setText(result);
        }
    }

    @Override
    public void onClick(View v) {

    }

    @Override
    public void onPlayEvent(int i, Bundle bundle) {

    }

    @Override
    public void onNetStatus(Bundle bundle) {

    }


    /////////////////////////////////////////////////////////////////////////////////
    //
    //                      缓存策略配置
    //
    /////////////////////////////////////////////////////////////////////////////////
//    public void setCacheStrategy(int nCacheStrategy) {
//        if (mCacheStrategy == nCacheStrategy)   return;
//        mCacheStrategy = nCacheStrategy;
//
//        switch (nCacheStrategy) {
//            case CACHE_STRATEGY_FAST:
//                mPlayConfig.setAutoAdjustCacheTime(true);
//                mPlayConfig.setMaxAutoAdjustCacheTime(CACHE_TIME_FAST);
//                mPlayConfig.setMinAutoAdjustCacheTime(CACHE_TIME_FAST);
//                mLivePlayer.setConfig(mPlayConfig);
//
//                mPlayVisibleLogView.setCacheTime(CACHE_TIME_FAST);
//                break;
//
//            case CACHE_STRATEGY_SMOOTH:
//                mPlayConfig.setAutoAdjustCacheTime(false);
//                mPlayConfig.setMaxAutoAdjustCacheTime(CACHE_TIME_SMOOTH);
//                mPlayConfig.setMinAutoAdjustCacheTime(CACHE_TIME_SMOOTH);
//                mLivePlayer.setConfig(mPlayConfig);
//
//                mPlayVisibleLogView.setCacheTime(CACHE_TIME_SMOOTH);
//                break;
//
//            case CACHE_STRATEGY_AUTO:
//                mPlayConfig.setAutoAdjustCacheTime(true);
//                mPlayConfig.setMaxAutoAdjustCacheTime(CACHE_TIME_SMOOTH);
//                mPlayConfig.setMinAutoAdjustCacheTime(CACHE_TIME_FAST);
//                mLivePlayer.setConfig(mPlayConfig);
//
//                mPlayVisibleLogView.setCacheTime(CACHE_TIME_SMOOTH);
//                break;
//
//            default:
//                break;
//        }
//    }


    /////////////////////////////////////////////////////////////////////////////////
    //
    //                      网络请求 测试代码
    //
    /////////////////////////////////////////////////////////////////////////////////
//    private void fetchPushUrl() {
//        if (mFetching) return;
//        mFetching = true;
//        if (mFetchProgressDialog == null) {
//            mFetchProgressDialog = new ProgressDialog(this);
//            mFetchProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);// 设置进度条的形式为圆形转动的进度条
//            mFetchProgressDialog.setCancelable(false);// 设置是否可以通过点击Back键取消
//            mFetchProgressDialog.setCanceledOnTouchOutside(false);// 设置在点击Dialog外是否取消Dialog进度条
//        }
//        mFetchProgressDialog.show();
//        if (mOkHttpClient == null) {
//            mOkHttpClient = new OkHttpClient().newBuilder()
//                    .connectTimeout(10, TimeUnit.SECONDS)
//                    .readTimeout(10, TimeUnit.SECONDS)
//                    .writeTimeout(10, TimeUnit.SECONDS)
//                    .build();
//        }
//
//        String reqUrl = "https://lvb.qcloud.com/weapp/utils/get_test_rtmpaccurl";
//        Request request = new Request.Builder()
//                .url(reqUrl)
//                .addHeader("Content-Type","application/json; charset=utf-8")
//                .build();
//        Log.d(TAG, "start fetch push url");
//        if (mFechCallback == null) {
//            mFechCallback = new TXFechPushUrlCall(this);
//        }
//        mOkHttpClient.newCall(request).enqueue(mFechCallback);
//    }
//
//    private static class TXFechPushUrlCall implements Callback {
//        WeakReference<LivePlayerActivity> mPlayer;
//        public TXFechPushUrlCall(LivePlayerActivity pusher) {
//            mPlayer = new WeakReference<LivePlayerActivity>(pusher);
//        }
//
//        @Override
//        public void onFailure(Call call, IOException e) {
//            final LivePlayerActivity player = mPlayer.get();
//            if (player != null) {
//                player.mFetching = false;
//                player.runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        Toast.makeText(player, "获取测试地址失败。", Toast.LENGTH_SHORT).show();
//                        player.mFetchProgressDialog.dismiss();
//                    }
//                });
//            }
//            Log.e(TAG, "fetch push url failed ");
//        }
//
//        @Override
//        public void onResponse(Call call, Response response) throws IOException {
//            if (response.isSuccessful()) {
//                String rspString = response.body().string();
//                final LivePlayerActivity player = mPlayer.get();
//                if (player != null) {
//                    try {
//                        JSONObject jsonRsp = new JSONObject(rspString);
//                        final String playUrl = jsonRsp.optString("url_rtmpacc");
//                        player.runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                player.mRtmpUrlView.setText(playUrl);
//                                Toast.makeText(player, "测试地址的影像来自在线UTC时间的录屏推流，推流工具采用移动直播 Windows SDK + VCam。", Toast.LENGTH_LONG).show();
//                                player.mFetchProgressDialog.dismiss();
//                            }
//                        });
//
//                    } catch(Exception e){
//                        Log.e(TAG, "fetch push url error ");
//                        Log.e(TAG, e.toString());
//                    }
//                    player.mFetching = false;
//                }
//
//            }
//        }
//    };
//    private TXFechPushUrlCall mFechCallback = null;
//    //获取推流地址
//    private OkHttpClient mOkHttpClient = null;
//    private boolean mFetching = false;
//    private ProgressDialog mFetchProgressDialog;
//
//    private void jumpToHelpPage() {
//        Uri uri = Uri.parse("https://cloud.tencent.com/document/product/454/7886");
//        if (mActivityType == ACTIVITY_TYPE_REALTIME_PLAY) {
//            uri = Uri.parse("https://cloud.tencent.com/document/product/454/7886#RealTimePlay");
//        }
//        startActivity(new Intent(Intent.ACTION_VIEW,uri));
//    }
}