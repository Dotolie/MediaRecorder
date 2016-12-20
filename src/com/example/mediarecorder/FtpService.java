package com.example.mediarecorder;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

public class FtpService extends Service {
	private final String TAG = "FTP service";
	
	public static final int MSG_DOWNLOAD = 1;
	public static final int MSG_UPLOAD = 2;
	
	private final Messenger mMessenger = new Messenger(new IncomingHandler());
	private FtpUtil mFtp = null;
	private String mFileNameDown;
	private String mFileNameUp;
	
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_DOWNLOAD:
				mFileNameDown = (String)msg.obj;
				Log.e(TAG, "down file="+mFileNameDown);				
				Download();
				break;
			case MSG_UPLOAD:
				mFileNameUp = (String)msg.obj;
				Log.e(TAG, "up file="+mFileNameUp);
				Upload();
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return mMessenger.getBinder();
	}
	
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		mFtp = new FtpUtil();
		super.onCreate();
	}

	public boolean Download() {
		if ( mFtp != null ) {
			new Thread(mRunDown).start();
		}
		return true;
	}
	
	public boolean Upload() {	
		if( mFtp != null ) {
			new Thread(mRunUp).start();
		}
		return true;
	}

	Runnable mRunDown = new Runnable() {
		@Override
		public void run() {
			try {
				mFtp.DownloadContents(mFileNameDown);
			}
			catch(Exception e) {
				Log.e(TAG, " mRunDown exception error");
			}
		}
	};		
	Runnable mRunUp = new Runnable() {
		@Override
		public void run() {
			try {
				mFtp.UploadContents(mFileNameUp);		
			}
			catch(Exception e) {
				Log.e(TAG, " mRunUp exception error");
			}
		}
	};		

}
