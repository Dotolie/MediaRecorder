package com.mvtech.devices;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import android.content.Context;
import android.util.Log;

public class Temp {
	private static final String TAG = "Temp";
	private Context mContext;
	private SerialPort mSerial;
	private FileDescriptor mFd;
	private FileInputStream mFileInputStream;
	private FileOutputStream mFileOutputStream;
	private final int PERIOD_SEND = 2000;
	private final String DEVICES = "/dev/ttyUSB2";
	private final String REQ_LM53 = "cat /sys/devices/ff140000.i2c/i2c-1/1-0048/temp1_input";
	private final String REQ_CPU = "cat /sys/bus/platform/drivers/tsadc/ff280000.tsadc/temp1_input";
	private final String REQ_GPU = "cat /sys/bus/platform/drivers/tsadc/ff280000.tsadc/temp2_input";
	private final String REQ_TEMP2MODEM = "AT+CPMUTEMP\r\n";

	public static final int TEMP_BOARD = 0;
	public static final int TEMP_MODEM = 1;
	public static final int TEMP_CPU = 2;
	public static final int TEMP_GPU = 3;
	
	public float mfTempModem = 0.0f;
	public float mfTempBoard = 0.0f;
	public float mfTempCpu = 0.0f;
	public float mfTempGpu = 0.0f;

	//sending문자열
	private byte[] mBuffer;	
	//serial sending thread
	private SendingThread mSendingThread = null;
	//serial receiving thread
	private ReadingThread mReadingThread = null;
	private boolean mbRunTh = false;
	
	
	public Temp(Context context) {
		mContext = context;
		
		try {
			mSerial = new SerialPort();
			mFd = mSerial.open(DEVICES, 9600, 0);
			mFileInputStream = new FileInputStream(mFd);
			mFileOutputStream = new FileOutputStream(mFd);
		} catch (Exception e) {
			Log.e(TAG, "serial error");
		}

		SetRunning( true );
		
		// modem 온도 수신
		mReadingThread = new ReadingThread();
		mReadingThread.start();				
			
		mBuffer = new byte[64];
		mBuffer= REQ_TEMP2MODEM.getBytes();

		// modem 온도 요청
		mSendingThread = new SendingThread();
		mSendingThread.start();
		
	}

	private float GetTemp(String cmd) {
		int nTemp = 0;
		float fTemp = 0;
		Runtime runtime = Runtime.getRuntime();
		Process process;
		try {
			process = runtime.exec(cmd);
			BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = br.readLine()) != null) {
				nTemp = Integer.parseInt(line);
				fTemp = nTemp;
				// Log.i(TAG,"test : " + line);
			}
		} catch (Exception e) {
			e.fillInStackTrace();
			Log.e(TAG, "Process Manager : " + "Unable to execute top command");
		}
		return fTemp;
	}
	
	private class SendingThread extends Thread {
		@Override
		public void run() {
			if (mFileOutputStream != null) {
				while (mbRunTh) {
					try {
						mFileOutputStream.write(mBuffer);
						Thread.sleep(PERIOD_SEND);
					} 
					catch (IOException e) {
						e.printStackTrace();
					} 
					catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				Log.e(TAG, "SendigThread: outLoop !");
			}
		}
	}

	private class ReadingThread extends Thread {
		@Override
		public void run() {
			super.run();
			int size = 0;
			byte[] buffer = new byte[64];

			if (mFileInputStream != null) {
				while (mbRunTh) {
					try {
						size = mFileInputStream.read(buffer);
					} catch (IOException e) {
						e.printStackTrace();
					}

					if (size > 0) {
						String strTemp = new String(buffer,0,size);
						String strParsed[] = strTemp.split("\r\n");
						int num = strParsed.length;
						for(int i=0;i<num;i++) {
							if( strParsed[i].contains("+CPMUTEMP")) {
								String strLast[] = strParsed[i].split(":");
								String strLast2 = strLast[1].replace("\r","").trim();
								
								mfTempModem = Float.parseFloat(strLast2);
								break;
							}
						}
						size = 0;
					}
				}
				Log.e(TAG, "ReadigThread: outLoop !");
			}
		}
	}
	private float SendCommand(String cmd) {
		int nTemp = 0;
		float fTemp = 0;
		Runtime runtime = Runtime.getRuntime();
		Process process;
		try {
			process = runtime.exec(cmd);
			BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = br.readLine()) != null) {
				nTemp = Integer.parseInt(line);
				fTemp = nTemp;
				// Log.i(TAG,"test : " + line);
			}
		} catch (Exception e) {
			e.fillInStackTrace();
			Log.e(TAG, "Process Manager : " + "Unable to execute top command");
		}
		return fTemp;
	}
	
	public float GetTemp(int nIndex) {
		float fResult = 0.0f;
		
		switch (nIndex) {
		case TEMP_BOARD:
			fResult = mfTempBoard  = SendCommand(REQ_LM53)/1000.0f;
			break;
		case TEMP_MODEM:
			fResult = mfTempModem;
			break;
		case TEMP_CPU:
			fResult = mfTempCpu = SendCommand(REQ_CPU);
			break;
		case TEMP_GPU:
			fResult = mfTempGpu = SendCommand(REQ_GPU);
			break;
		}
		return fResult;
	}

	public void SetRunning(boolean bEnable) {
		mbRunTh = bEnable;
	}
}
