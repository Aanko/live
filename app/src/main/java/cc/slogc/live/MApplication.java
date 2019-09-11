package cc.slogc.live;

import android.app.Application;
import com.tencent.rtmp.TXLiveBase;

public class MApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        String licenceURL = "http://license.vod2.myqcloud.com/license/v1/eb7627739eda8227f18cdac2171d7c77/TXLiveSDK.licence"; // 获取到的 licence url
        String licenceKey = "ff89b9fc36d687c0c0f51dd84e6fec69"; // 获取到的 licence key
        TXLiveBase.getInstance().setLicence(this, licenceURL, licenceKey);
    }
}
