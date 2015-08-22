package kr.gonigoni.cameramoving;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;

import java.io.File;

/**
 * Created by Yungon on 2015-06-18.
 * 사진이 찍히면 앨범에서 안 보이는 문제가 있음. 이를 해결하기 위함.
 */
public class MediaScanning implements MediaScannerConnectionClient {
    private MediaScannerConnection mConnection;
    private File mTargetFile;

    public MediaScanning (Context mContext, File mFile) {
        this.mTargetFile = mFile;

        mConnection = new MediaScannerConnection(mContext, this);
        mConnection.connect();
    }

    @Override
    public void onMediaScannerConnected() {
        mConnection.scanFile(mTargetFile.getAbsolutePath(), null);
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        mConnection.disconnect();
    }
}
