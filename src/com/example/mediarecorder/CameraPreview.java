package com.example.mediarecorder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, MediaRecorder.OnInfoListener {
	private static final String TAG = "CameraPreview";

	private SurfaceHolder mHolder;
    private Camera mCamera;
	private Context mContext;
	// 레코더 객체 생성
	private MediaRecorder mRecorder = null;
	// 녹화 시간 - 10초
	private static final int RECORDING_TIME = 10000;
	private static final int RECORDING_SIZE = 512000;
	private final int NO_MAX_FILES = 10;
	private String mCreateFileName;
	private int nCount = 0;
	private final int PERIOD_UPLOAD = 6;
    
    public CameraPreview(Context context) {
        super(context);
        mCamera = Camera.open();
        mContext = context;
        
        // SurfaceHolder 가 가지고 있는 하위 Surface가 파괴되거나 업데이트 될경우 받을 콜백을 세팅한다 
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated 되었지만 3.0 이하 버젼에서 필수 메소드라서 호출해둠.
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }
    
    @Override
	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub		    	
        // Surface가 생성되었으니 프리뷰를 어디에 띄울지 지정해준다. (holder 로 받은 SurfaceHolder에 뿌려준다. 
        try {
			Camera.Parameters parameters = mCamera.getParameters();
			if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
				parameters.set("orientation", "portrait");
				mCamera.setDisplayOrientation(90);
				parameters.setRotation(90);
			} else {
				parameters.set("orientation", "landscape");
				mCamera.setDisplayOrientation(0);
				parameters.setRotation(0);
			}
			mCamera.setParameters(parameters);
			// 프리뷰 콜백을 설정한다 - 프레임 설정이 가능하다
			mCamera.setPreviewDisplay(holder);
//            mCamera.startPreview();
        } catch (Exception e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }		
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// TODO Auto-generated method stub
		// 서피스 변경되었을 때의 대응 루틴
		Log.d(TAG, "window size = "+width+ " x "+height);

		if (holder.getSurface() == null){
            // 프리뷰가 존재하지 않을때
            return;
          }
		
		if (mCamera != null) {
			Camera.Parameters parameters = mCamera.getParameters();
			// 프리뷰 사이즈 값 재조정
			parameters.setPreviewSize(1280, 1200);
			mCamera.setParameters(parameters);
			// 프리뷰 다시 시작
			mCamera.startPreview();
		}		
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
        // 프리뷰 제거시 카메라 사용도 끝났다고 간주하여 리소스를 전부 반환한다
		if (mRecorder != null) {
			mRecorder.stop();
			mRecorder.reset();
			mRecorder.release();
			mRecorder = null;
			mCamera.lock();
		}		
		
    	if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;            
        }		

		
        Log.e(TAG, "CameraPreview : surfaceDestroyed");

	}

	public void endRecording() {
		if (mRecorder != null) {
			mRecorder.stop();
			mRecorder.reset();
			mRecorder.release();
			mRecorder = null;
			mCamera.lock();
		}		
	}
	public void beginRecording() {
		// 레코더 객체 초기화
		if (mRecorder != null) {
			mRecorder.stop();
			mRecorder.reset();
			mRecorder.release();
			mRecorder = null;
			mCamera.lock();
		}
		
		try {
			mRecorder = new MediaRecorder();
			// Video/Audio 소스 설정
			mCamera.unlock();
			mRecorder.setCamera(mCamera);
			mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
			// mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			mRecorder.setVideoSize(1280, 1200);
			mRecorder.setVideoFrameRate(30);

			// Video/Audio 인코더 설정
			mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
			// mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			// 녹화 시간 한계 , 10초
			mRecorder.setMaxDuration(RECORDING_TIME);
			// 녹화 시간 한계 , 512KB			
//			mRecorder.setMaxFileSize(RECORDING_SIZE);
			// 프리뷰를 보여줄 서피스 설정
			mRecorder.setPreviewDisplay(mHolder.getSurface());
			// 녹화할 대상 파일 설정
			mCreateFileName = getOutputMediaFileName();
			String stFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + File.separator
					+ mCreateFileName;

			mRecorder.setOutputFile(stFilePath);
			mRecorder.setOnInfoListener(this);
			mRecorder.prepare();
			mRecorder.start();

		} catch (Exception e) {
			// TODO: handle exception
			Log.e("CAM TEST", "Error Occur???!!!");
			e.printStackTrace();
		}

	}
	
	
	/** 이미지를 저장할 파일 객체를 생성합니다 */
	private String getOutputMediaFileName(){
		String timeStamp;
		final String FileName;

	    // SD카드가 마운트 되어있는지 먼저 확인해야합니다
	    // Environment.getExternalStorageState() 로 마운트 상태 확인 가능합니다 
	    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
	              Environment.DIRECTORY_MOVIES), "");
	    // 굳이 이 경로로 하지 않아도 되지만 가장 안전한 경로이므로 추천함.

	    // 없는 경로라면 따로 생성한다.
	    if (! mediaStorageDir.exists()){
	        if (! mediaStorageDir.mkdirs()){
	            Log.d(TAG, "failed to create directory");
	            return null;
	        }
	    }

	    // 파일 개수를 확인하고 오래된 파일을 지운다.
	    File[] listFiles = mediaStorageDir.listFiles();
	    Arrays.sort(listFiles);
	    int nlen = listFiles.length;
	    if( nlen > NO_MAX_FILES) {
	    	for(int i=0;i<nlen-NO_MAX_FILES;i++){
	    		listFiles[i].delete();
	    	}
	    }
	    
		// 파일명을 적당히 생성. 여기선 시간으로 파일명 중복을 피한다.
		timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		FileName = "MOV_" + timeStamp + ".mp4";
		
		MainActivity.mTvOutputFile.post( new Runnable() {
			@Override
			public void run() {
				MainActivity.mTvOutputFile.setText(FileName);		
			}
		});
		
	    Log.i(TAG, "Saved at = "+ FileName);

	    return FileName;
	}
	
	public void onInfo(MediaRecorder mr, int what, int extra) {
		switch (what) {
		case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
		case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
			endRecording();
			if( (nCount % PERIOD_UPLOAD) ==  0 ) {
				if (MainActivity.mBound) {
					Message msg = Message.obtain(null, FtpService.MSG_UPLOAD, mCreateFileName);
					try {
						MainActivity.mFtpService.send(msg);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			nCount++;
			
			beginRecording();
			break;
		}
	}
	

	
}
