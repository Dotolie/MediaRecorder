package com.mvtech.devices;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SerialPort {
	private static final String TAG = "SerialPort";
	/*
	 * Do not remove or rename the field mFd: it is used by native method close();
	 */
	static {
		try {	
			Log.e(TAG, "System.loadLibrary");
			System.loadLibrary("serial_port");
		} catch (UnsatisfiedLinkError e) {
			// only ignore exception in non-android env
			if ("Dalvik".equals(System.getProperty("java.vm.name"))) throw e;
		}
	}
	
	
	// JNI
	public native static FileDescriptor open(String path, int baudrate, int flags);
	public native void close();
        
}