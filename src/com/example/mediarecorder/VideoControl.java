package com.example.mediarecorder;

import java.io.FileDescriptor;

import com.mvtech.devices.Scaler;

import android.content.Context;
import android.util.Log;

public class VideoControl {
	private final String TAG = "VideoControl";
	
	
	private Context mContext;
	private Scaler mScaler = null;
	private int nVideoSignals = 0;
	private int nMotions = 0;
	private FileDescriptor mFd;
	private static byte mcOSDonOff = 0x00;
	private static byte mLastMdOnOff = 0x00;
	public static final int MASK_CH1 = 0x01;
	public static final int MASK_CH3 = 0x04;
	public static final int MASK_CH4 = 0x08;
	
	public VideoControl(Context context ) {
		mContext = context;
		
		mScaler = new Scaler();
		Scaler.EventListner callback = new Scaler.EventListner() {
			
			@Override
			public void onDetected(byte cDetect) {
				// TODO Auto-generated method stub
				Log.w(TAG, "Motion detected !! channels="+cDetect);
				
				if ((cDetect & 0x01) > 0) {
					MainActivity.mTvDetect01.post(new Runnable() {
						@Override
						public void run() {
//							MainActivity.mTgbEvent.performClick();
							MainActivity.mTvDetect01.setBackgroundColor(0xff00f0f0);
						}
					});
				}
				if ((cDetect & 0x04) > 0) {
					MainActivity.mTvDetect03.post(new Runnable() {
						@Override
						public void run() {
//							MainActivity.mTgbMDetect03.performClick();
							MainActivity.mTvDetect03.setBackgroundColor(0xff00f0f0);
						}
					});
				}
				if ((cDetect & 0x08) > 0) {
					MainActivity.mTvDetect04.post(new Runnable() {
						@Override
						public void run() {
//							MainActivity.mTgbMDetect04.performClick();
							MainActivity.mTvDetect04.setBackgroundColor(0xff00f0f0);
						}
					});
				}
			}
		};
		mScaler.setOnEventListner(callback);
		mFd = mScaler.open();
	}
	
	public void SetOSD( byte cOnOff) {
		
		if( mScaler != null && cOnOff != mcOSDonOff) {
			mScaler.SetOSD(cOnOff);
			mcOSDonOff = cOnOff;
		}		
	}
	
	public byte GetVideoInputs() {
		byte cRet = 0;
		if( mScaler != null ) {
			cRet = mScaler.GetVideoInputs();
		}
		return cRet;
	}
	
	public byte GetMotions() {
		byte cRet = 0;
		if( mScaler != null ) {
			cRet = mScaler.GetMotionSens();
		}
		return cRet;
	}
	
	public void SetMotionDetection( int nMask, boolean bOn ) {
		byte cOnOff = mLastMdOnOff;
		if( mScaler != null ) {

			if( (nMask & MASK_CH1) > 0) {
				if( bOn ) 	cOnOff |= MASK_CH1;
				else		cOnOff &= ~MASK_CH1;
			}
			if( (nMask & MASK_CH3)> 0 ) {
				if( bOn ) 	cOnOff |= MASK_CH3;
				else		cOnOff &= ~MASK_CH3;
			}
			if( (nMask & MASK_CH4) > 0 ) {
				if( bOn ) 	cOnOff |= MASK_CH4;
				else		cOnOff &= ~MASK_CH4;
			}
			
			if( cOnOff != mLastMdOnOff ) {
				mScaler.SetDetection(cOnOff);
			}	
				
			mLastMdOnOff = cOnOff;		// save old status			
		}
	}
	
	public void SetPSensitivity( int nSensitivity ) {
		if( mScaler != null ) {
			mScaler.SetPSensitivity( nSensitivity );
		}
	}

	public void SetTSensitivity( int nSensitivity ) {
		if( mScaler != null ) {
			mScaler.SetTSensitivity( nSensitivity );
		}
	}

	public void SetEventEnable( int nOnOff ) {
		if( mScaler != null ) {
			mScaler.SetEventEnable( nOnOff );
		}
	}

	public void SetEventClear() {
		if( mScaler != null) {
			mScaler.SetEventClear();
		}
	}
	public void close() {
		if( mScaler != null ) {
			mScaler.close();
		}
	}

}
