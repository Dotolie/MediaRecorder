package com.mvtech.devices;

import java.io.FileDescriptor;

import android.util.Log;

public class Scaler {
	static {
		System.loadLibrary("scaler");
	}
	public interface EventListner {
		void onDetected(byte cChannels);
	}
	private static EventListner mDetectionCb;
	
	public void setOnEventListner( EventListner callback) {
		mDetectionCb = callback;
	}
	public native static FileDescriptor open();
	public native static void SetOSD(byte cOnOff);
	public native static void SetDetection(byte cOnOff);
	public native static void SetPSensitivity(int nSens);
	public native static void SetTSensitivity(int nSens);
	public native static byte GetMotionSens();
	public native static byte GetVideoInputs();
	public native static void SetEventEnable(int nOnOff);
	public native static byte SetEventClear();
	public native static void close();
	public static void onMotionEvent() {
		byte cCH = 0;
		cCH = GetMotionSens();
//		cCH = SetEventClear();
		
		Log.e("onMotion", "cDetect="+cCH);
		mDetectionCb.onDetected(cCH);
	}
}
