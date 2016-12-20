package com.example.mediarecorder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import android.util.Log;

public class FtpUtil {
	private final String TAG = "FTP util";
	
	private final String SERVERIP = "61.32.182.251";
	private final int PORT = 20021;
	private final String SERVERID = "mvtech";
	private final String SERVERPW = "1";
	private final String PATH_MOVIE = "/sdcard/Movies/";
	private final String PATH_DOWN = "/sdcard/Download/";
	private final String PATH_SERVER = "/image";
	
	private FTPClient ftpClient = null;

	boolean DownloadContents(String filename) {
		this.ftpClient = new FTPClient();
		this.ftpClient.setConnectTimeout(3 * 1000);
		this.ftpClient.setDefaultTimeout(5 * 1000);
		
		if( login(SERVERID, SERVERPW) ) {
			cd(PATH_SERVER);				// input usr directory
			FTPFile[] files = list();
			if (files == null) {
				return false;
			}
			ArrayList<String> ImageIds_tmp = new ArrayList<String>();

			for (int i = 0; i < files.length; i++) {
				String fileName = files[i].getName();
				String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
				long size = files[i].getSize();
				extension = extension.toUpperCase();
				if (size > 0) {
					// for (int j = 0; j < size; j++)
					{
						if (filename.equalsIgnoreCase(fileName)) {
							get(fileName, fileName);
						}
					}
				}
			}
			logout();
			Log.w(TAG, "download fine");
		}
		disconnect();
		return true;
	}
	
	boolean UploadContents(String filename) {
		boolean bRet=false;
		
		this.ftpClient = new FTPClient();
		this.ftpClient.setConnectTimeout(3 * 1000);
		this.ftpClient.setDefaultTimeout(5 * 1000);
		if (login(SERVERID, SERVERPW)) {
			cd(PATH_SERVER);				// input usr directory
			
			bRet = put(filename, filename);
			if( bRet ) {
				Log.w(TAG, "upload done!");
			}
			else {
				Log.w(TAG,  "upload fail!");
			}

			logout();
		}
		disconnect();
		return true;
	}

	
	public boolean login(String user, String password) {
		try {
			this.connect();
			return this.ftpClient.login(user, password);
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return false;
	}

	private boolean logout() {
		try {
			return this.ftpClient.logout();
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}

		return false;
	}

	public void connect() {
		try {
			this.ftpClient.connect(SERVERIP, PORT);
			int reply;
			reply = this.ftpClient.getReplyCode();
			if (!FTPReply.isPositiveCompletion(reply)) {
				this.ftpClient.disconnect();
			}
		}
		catch (IOException ioe) {
			if (this.ftpClient.isConnected()) {
				try {
					this.ftpClient.disconnect();
				} catch (IOException f) {
					f.printStackTrace();
				}
			}
		}
	}

	public FTPFile[] list() {
		FTPFile[] files = null;
		try {
			files = this.ftpClient.listFiles();
			return files;
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return null;
	}

	public boolean put(String source, String target) {
		InputStream input = null;
		try {
			StringBuffer furl = new StringBuffer(PATH_MOVIE);
			File path = new File(furl.toString());
			if (!path.isDirectory()) {
				return false;
			}
			furl.append(target);
			File local = new File(furl.toString());
			if (!local.isFile()) {
				return false;
			}
			input = new FileInputStream(local);
		}
		catch (FileNotFoundException fnfe) {
			Log.e(TAG, "upload file not found !");
		}

		try {
			if (this.ftpClient.appendFile(target, input)) {
				input.close();
				return true;
			}
		}
		catch (IOException ioe) {
			Log.e(TAG, "ftp append error!");
		}
		return false;
	}

	public File get(String source, String target) {
		OutputStream output = null;
		try {
			StringBuffer furl = new StringBuffer(PATH_DOWN);
			File path = new File(furl.toString());
			if (!path.isDirectory()) {
				path.mkdirs();
			}
			furl.append(target);
			File local = new File(furl.toString());
			if (local.isFile()) {
				return null;
			}
			output = new FileOutputStream(local);
		}
		catch (FileNotFoundException fnfe) {
			Log.e(TAG, "Download file creation error !");
		}

		File file = new File(source);
		try {
			if (this.ftpClient.retrieveFile(source, output)) {
				output.flush();
				output.close();			
				return file;
			}
		}
		catch (IOException ioe) {
			Log.e(TAG, "Download error!");
		}
		return null;
	}

	public void cd(String path) {
		try {
			this.ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
			this.ftpClient.enterLocalPassiveMode();
			this.ftpClient.setControlEncoding("euc-kr");
			this.ftpClient.changeWorkingDirectory(path);
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	private void disconnect() {
		try {
			this.ftpClient.disconnect();
		}
		catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
}
