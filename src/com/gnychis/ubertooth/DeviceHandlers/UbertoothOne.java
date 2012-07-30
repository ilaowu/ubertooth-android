package com.gnychis.ubertooth.DeviceHandlers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Message;
import android.util.Log;

import com.gnychis.ubertooth.UbertoothMain;
import com.gnychis.ubertooth.UbertoothMain.ThreadMessages;
import com.stericson.RootTools.RootTools;

public class UbertoothOne {
	private static final String TAG = "UbertoothOneDev";
	private static final boolean VERBOSE = true;
	
	public static final int BT_LOW_FREQ=2402;
	public static final int BT_HIGH_FREQ=2480;

	public static final int UBERTOOTH_CONNECT = 400;
	public static final int UBERTOOTH_DISCONNECT = 401;
	public static final String UBERTOOTH_SCAN_RESULT = "com.gnychis.coexisyst.UBERTOOTH_SCAN_RESULT";
	public static final int POLLS_IN_MAX = 10;
	
	UbertoothMain _mainActivity;
	public String _firmware_version;
	
	UbertoothOneScan _scan_thread;
	
	public boolean _device_connected;
	
	ArrayList<Integer> _scan_result;
	
	public UbertoothOne(UbertoothMain c) {
		_mainActivity = c;
	}
	
	public boolean isConnected() {
		return _device_connected;
	}
	
	public void connected() {
		_mainActivity.buttonScanSpectrum.setEnabled(true);
		_device_connected=true;
		UbertoothOneInit wsi = new UbertoothOneInit();
		wsi.execute(_mainActivity);
	}
	
	public void disconnected() {
		_mainActivity.buttonScanSpectrum.setEnabled(false);
		_device_connected=false;
	}
	
	protected class UbertoothOneInit extends AsyncTask<Context, Integer, String>
	{
		Context parent;
		UbertoothMain mainActivity;
		
		// Used to send messages to the main Activity (UI) thread
		protected void sendMainMessage(UbertoothMain.ThreadMessages t, Object obj) {
			Message msg = new Message();
			msg.what = t.ordinal();
			msg.obj = obj;
			mainActivity._handler.sendMessage(msg);
		}
		
		@Override
		protected String doInBackground( Context ... params )
		{
			parent = params[0];
			mainActivity = (UbertoothMain) params[0];
			
			// To use the WiSpy device, we need to give the USB device the application's permissions
			runCommand("find /dev/bus -exec chown " + mainActivity.getAppUser() + " {} \\;");
			
			// Get the firmware version for fun and demonstration
			_firmware_version = runCommand("/data/data/com.gnychis.ubertooth/files/ubertooth_util -v").get(0);
			_scan_result = new ArrayList<Integer>();
			
			// Try to initialize the Ubertooth One
			if(startUbertooth()==1)
				sendMainMessage(ThreadMessages.UBERTOOTH_INITIALIZED,null);
			else
				sendMainMessage(ThreadMessages.UBERTOOTH_FAILED,null);

			return "OK";
		}
		
		public ArrayList<String> runCommand(String c) {
			ArrayList<String> res = new ArrayList<String>();
			try {
				// First, run the command push the result to an ArrayList
				List<String> res_list = RootTools.sendShell(c,0);
				Iterator<String> it=res_list.iterator();
				while(it.hasNext()) 
					res.add((String)it.next());
				
				res.remove(res.size()-1);
				
				// Trim the ArrayList of an extra blank lines at the end
				while(true) {
					int index = res.size()-1;
					if(index>=0 && res.get(index).length()==0)
						res.remove(index);
					else
						break;
				}
				return res;
				
			} catch(Exception e) {
				Log.e("WifiDev", "error writing to RootTools the command: " + c, e);
				return null;
			}
		}
	}
	
	// This starts the scan thread, passing the main activity and beginning the spectrum scan.
	public boolean scanStart() {
		_scan_result.clear();
		_scan_thread = new UbertoothOneScan();
		_scan_thread.execute(_mainActivity);
		return true;  // in scanning state, and channel hopping
	}
	
	// This is a thread to perform the actual scan (blocking and waiting for it), rather
	// than blocking the main activity.  When it is complete, it sends the results to the
	// main activity.
	protected class UbertoothOneScan extends AsyncTask<Context, Integer, String>
	{
		Context parent;
		UbertoothMain mainActivity;
		
		// Used to send messages to the main Activity (UI) thread
		protected void sendMainMessage(UbertoothMain.ThreadMessages t, Object obj) {
			Message msg = new Message();
			msg.what = t.ordinal();
			msg.obj = obj;
			mainActivity._handler.sendMessage(msg);
		}
		
		@Override
		protected String doInBackground( Context ... params )
		{
			parent = params[0];
			mainActivity = (UbertoothMain) params[0];
			
			// Perform the scan, specify the low and high freqs as well as
			// the number of sweeps to perform (this is a "max hold").
			int[] scan_res = scanSpectrum(BT_LOW_FREQ, BT_HIGH_FREQ, 100);
			
			if(scan_res==null) {
				sendMainMessage(ThreadMessages.UBERTOOTH_SCAN_FAILED, null);
				return "NOPE";
			}
			
			_scan_result = new ArrayList<Integer>();
			for(int i=0; i<scan_res.length; i++)
				_scan_result.add(scan_res[i]);
				
			sendMainMessage(ThreadMessages.UBERTOOTH_SCAN_COMPLETE, _scan_result);
			
			return "PASS";
		}
		
	}
	
	public native int startUbertooth();
	public native int stopUbertooth();
	public native int[] scanSpectrum(int low_freq, int high_freq, int sweeps);
}
