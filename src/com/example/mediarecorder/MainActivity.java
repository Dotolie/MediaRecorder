package com.example.mediarecorder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.mvtech.devices.Temp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {

	public static final String TAG = "MediaRecord";

	private boolean mbIsRecording = false;
	private CameraPreview mPreview;

	private VideoControl mVideoControl = null;
	private Temp mTemp= null;
	private ReadThread mReadThread = null;
	
	public float mfTempModem = 0.0f;
	public float mfTempBoard = 0.0f;
	public float mfTempCpu = 0.0f;
	public float mfTempGpu = 0.0f;
	
	private TextView mTvBoard;
	private TextView mTvModem;
	private TextView mTvCpu;
	private TextView mTvGpu;
	private TextView mTvLog;
	public static TextView mTvRecordTime;
	public static TextView mTvOutputFile;
	
	public static TextView mTvVinput01;
	public static TextView mTvVinput03;
	public static TextView mTvVinput04;
	
	public static TextView mTvDetect01;
	public static TextView mTvDetect03;
	public static TextView mTvDetect04;

	public static ToggleButton mTgbMDetect01;
	public static ToggleButton mTgbMDetect03;
	public static ToggleButton mTgbMDetect04;
	
	public static ToggleButton mTgbEvent;
	
	private long mStartTime = 0;
	private ToggleButton mTgbRecord;
	
    public static Messenger mFtpService;
    public static boolean mBound = false;
    
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		setContentView(R.layout.activity_main);
		// 세로화면 고정으로 처리한다
		// SCREEN_ORIENTATION_LANDSCAPE - 가로화면 고정
		// SCREEN_ORIENTATION_PORTRAIT - 세로화면 고정
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		
		setScaler();
		// 버튼을 설정한다
		setButtons();

//	자동 녹화 시작 버튼		
//		Handler handle = new Handler();
//		handle.postDelayed(new Runnable() {
//			@Override
//			public void run() {
//				mTgbRecord.performClick();				
//			}
//		}, 3000);
		
	}
	
	private void setScaler() {
		mVideoControl = new VideoControl(this);

		final TextView mTvOsd = (TextView)findViewById(R.id.tvSens2);
		
		Button mBtnOsd = (Button)findViewById(R.id.btnSens2);
		mBtnOsd.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				int nVal = Integer.parseInt( mTvOsd.getText().toString(), 16 );
				mVideoControl.SetPSensitivity(nVal);				
			}
		});
				
		
		final TextView mTvSens = (TextView)findViewById(R.id.tvSens);
		
		Button mBtnSens = (Button)findViewById(R.id.btnSens);
		mBtnSens.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				int nVal = (int)Long.parseLong( mTvSens.getText().toString(),16);
				mVideoControl.SetTSensitivity(nVal);				
			}
		});
	}
	
	// 프리뷰(카메라가 찍고 있는 화상을 보여주는 화면) 설정 함수
	private void setPreview() {
		// 프리뷰창을 생성하고 액티비티의 레이아웃으로 지정합니다
		mPreview = new CameraPreview(this);
		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
		preview.addView(mPreview);

	}

	private void setButtons() {
		// Rec Start 버튼 콜백 설정
		mTgbRecord = (ToggleButton) findViewById(R.id.tgbRecord);
		mTgbRecord.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				if (mTgbRecord.isChecked()) {
					Log.d(TAG, "start Recording !");
					mPreview.beginRecording();
					mbIsRecording = true;
					mStartTime = System.currentTimeMillis();
				} else {
					Log.d(TAG, "stop Record !");
					mPreview.endRecording();
					mbIsRecording = false;
				}
			}
		});
		
		mTvModem = (TextView)findViewById(R.id.tvModem);
		mTvBoard = (TextView)findViewById(R.id.tvBoard);
		mTvCpu = (TextView)findViewById(R.id.tvCpu);
		mTvGpu = (TextView)findViewById(R.id.tvGpu);
		mTvLog = (TextView)findViewById(R.id.tvLog);
		mTvRecordTime = (TextView)findViewById(R.id.tvRecordTime);
		mTvOutputFile = (TextView)findViewById(R.id.tvOutputFile);
		
		mTvVinput01 = (TextView)findViewById(R.id.tvVinput01);
		mTvVinput03 = (TextView)findViewById(R.id.tvVinput03);
		mTvVinput04 = (TextView)findViewById(R.id.tvVinput04);
		
	
		mTvDetect01 = (TextView)findViewById(R.id.tvDetect01);
		mTvDetect03 = (TextView)findViewById(R.id.tvDetect03);
		mTvDetect04 = (TextView)findViewById(R.id.tvDetect04);
	
		mTgbMDetect01 = (ToggleButton)findViewById(R.id.tgbMDetect01);
		mTgbMDetect03 = (ToggleButton)findViewById(R.id.tgbMDetect03);
		mTgbMDetect04 = (ToggleButton)findViewById(R.id.tgbMDetect04);
		
		mTgbMDetect01.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if (mTgbMDetect01.isChecked()) {
					mVideoControl.SetMotionDetection( VideoControl.MASK_CH1, true);
				}
				else {
					mVideoControl.SetMotionDetection( VideoControl.MASK_CH1, false);
				}
			}
		});
		mTgbMDetect03.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {			
				if (mTgbMDetect03.isChecked()) {
					mVideoControl.SetMotionDetection( VideoControl.MASK_CH3, true);
				}
				else {
					mVideoControl.SetMotionDetection( VideoControl.MASK_CH3, false);
				}
			}
		});

		mTgbMDetect04.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if (mTgbMDetect04.isChecked()) {
					mVideoControl.SetMotionDetection( VideoControl.MASK_CH4, true);
				}
				else {
					mVideoControl.SetMotionDetection( VideoControl.MASK_CH4, false);
				}
			}
		});
		
		mTgbEvent = (ToggleButton)findViewById(R.id.tgbEvent);
		mTgbEvent.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				if( mTgbEvent.isChecked()) {
					mVideoControl.SetEventEnable(1);
				}
				else {
					mVideoControl.SetEventEnable(0);
				}
			}
		});
		final Button btnDclear = (Button)findViewById(R.id.btnDclear);
		btnDclear.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				btnDclear.post(new Runnable() {
					@Override
					public void run() {
						mTvDetect01.setBackgroundColor(0xfff07070);
						mTvDetect03.setBackgroundColor(0xfff07070);
						mTvDetect04.setBackgroundColor(0xfff07070);
					}
				});
				
				mVideoControl.SetEventClear();
			}
		});
	}

	private void setTempThread() {
		if( mTemp == null ) {
			mTemp = new Temp(this);
		}
		
		if( mReadThread == null ) {
			mReadThread = new ReadThread();
			mReadThread.start();
		}
	}

	private class ReadThread extends Thread {
		boolean bRunTh = true;
		
		@Override
		public void run() {
			super.run();
			while (bRunTh) {
				mfTempBoard = mTemp.GetTemp(Temp.TEMP_BOARD);
				mfTempModem = mTemp.GetTemp(Temp.TEMP_MODEM);
				mfTempCpu = mTemp.GetTemp(Temp.TEMP_CPU);
				mfTempGpu = mTemp.GetTemp(Temp.TEMP_GPU);
				
				final byte cVin = mVideoControl.GetVideoInputs();
				mVideoControl.SetOSD(cVin);
				
				mTvVinput01.post(new Runnable() {
					@Override
					public void run() {
						int nColor = 0xffff0000;
						if( (cVin & 0x01) == 0 ) {
							nColor = 0xff00ff00;
						}
						mTvVinput01.setBackgroundColor(nColor);
					}
				});
				mTvVinput03.post(new Runnable() {
					@Override
					public void run() {
						int nColor = 0xffff0000;
						if( (cVin & 0x04) == 0 ) {
							nColor = 0xff00ff00;
						}
						mTvVinput03.setBackgroundColor(nColor);
					}
				});				
				mTvVinput04.post(new Runnable() {
					@Override
					public void run() {
						int nColor = 0xffff0000;
						if( (cVin & 0x08) == 0 ) {
							nColor = 0xff00ff00;
						}
						mTvVinput04.setBackgroundColor(nColor);
					}
				});				

				
				
//				final byte cDetect = mVideoControl.GetMotions();
//				if ((cDetect & 0x01) > 0) {
//					mTvDetect01.post(new Runnable() {
//						@Override
//						public void run() {
//							mTvDetect01.setBackgroundColor(0xff00f0f0);
//						}
//					});
//				}
//				if ((cDetect & 0x04) > 0) {
//					mTvDetect03.post(new Runnable() {
//						@Override
//						public void run() {
//							mTvDetect03.setBackgroundColor(0xff00f0f0);
//						}
//					});
//				}
//				if ((cDetect & 0x08) > 0) {
//					mTvDetect04.post(new Runnable() {
//						@Override
//						public void run() {
//							mTvDetect04.setBackgroundColor(0xff00f0f0);
//						}
//					});
//				}
				
				
				
				
				mTvBoard.post(new Runnable() {
					@Override
					public void run() {
						String sTemp = Float.toString(mfTempBoard);
						sTemp += " ºC";
						mTvBoard.setText(sTemp);
					}
				});
				
				mTvModem.post(new Runnable() {
					@Override
					public void run() {
						String sTemp = Float.toString(mfTempModem);
						sTemp += " ºC";
						mTvModem.setText(sTemp);
					}
				});
				mTvCpu.post(new Runnable() {
					@Override
					public void run() {
						String sTemp = Float.toString(mfTempCpu);
						sTemp += " ºC";
						mTvCpu.setText(sTemp);
					}
				});
				mTvGpu.post(new Runnable() {
					@Override
					public void run() {
						String sTemp = Float.toString(mfTempGpu);
						sTemp += " ºC";
						mTvGpu.setText(sTemp);
					}
				});

				SimpleDateFormat sdfNow = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
				String time = sdfNow.format(new Date(System.currentTimeMillis()));
				final String sText = String.format("%s : %02.1f,  %02.1f,  %02.1f,  %02.1f\r\n", time, mfTempBoard, mfTempModem,
						mfTempCpu, mfTempGpu);

				if (mTvLog.getLineCount() > 5) {
					mTvLog.post( new Runnable() {
						@Override
						public void run() {
							mTvLog.setText(sText);
						}
					});
				} else {
					mTvLog.post( new Runnable() {
						@Override
						public void run() {
							mTvLog.append(sText);
						}
					});
				}

				if( mbIsRecording ) {
					long currentTime = System.currentTimeMillis();
					long totalTime = currentTime - mStartTime;
					int nTime = (int)totalTime/1000;
					int nSec = nTime%60;
					int nMin = nTime/60%60;
					int nHour = nTime/3600;
					final String stElapse = String.format("%d:%02d:%02d", nHour, nMin, nSec);
					
//					Log.e(TAG, "recording time="+stElapse);
					mTvRecordTime.post( new Runnable() {
						@Override
						public void run() {
							mTvRecordTime.setText(stElapse);
						}
					});					
				}
				
//				dataSaveLog(sText, "temp");
				
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			Log.e(TAG, "ReadTempThread : outLoop");
		}
		
		public void SetRunning(boolean bEnable) {
			bRunTh = bEnable;
		}
	}

	private void dataSaveLog(String _log, String _fileName)
	{
		String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/LOG/";
		File file = new File(dirPath);
		
		if(!file.exists())
			file.mkdirs();
		
		File saveFile = new File(dirPath + "LOG_" + _fileName + ".txt");
		try {
			BufferedWriter bfw = new BufferedWriter(new FileWriter( dirPath + "LOG_" + _fileName + ".txt", true));
			bfw.write(_log);
			bfw.flush();
			bfw.close();
		}
		catch (IOException e) {
			
		}
	}

	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub

		super.onResume();
		Log.e(TAG, "onResume() ");
		
		startService();
		// 프리뷰를 설정한다
		setPreview();
		setTempThread();

	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onStop();
		
		Log.d(TAG, "onPause() : Begin ");
		

		
		mReadThread.SetRunning(false);
		mTemp.SetRunning(false);
		
		try {
			mReadThread.join(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		stopService();
		
		Log.d(TAG, "onPause() : End ");

		mReadThread = null;
		mTemp = null;
		
	}
	
	private void startService() {
		Intent intent = new Intent(this, FtpService.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}
	
	private void stopService() {
		if( mBound) {
			unbindService(mConnection);
			mBound = false;
		}
	}
	
	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			mFtpService = new Messenger(service);
			mBound = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mFtpService = null;
			mBound = false;
		}
	};	
}
