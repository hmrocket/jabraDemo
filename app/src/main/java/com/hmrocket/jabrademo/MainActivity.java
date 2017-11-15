package com.hmrocket.jabrademo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.jabra.sdk.api.Callback;
import com.jabra.sdk.api.JabraDevice;
import com.jabra.sdk.api.JabraError;
import com.jabra.sdk.api.Listener;
import com.jabra.sdk.api.basic.BatteryStatus;
import com.jabra.sdk.api.sensor.SensorData;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

	private static final String TAG = MainActivity.class.getSimpleName();
	private static final int REQUEST_STORAGE  = 1;

	private DeviceConnector mDeviceConnector;

	private TextView tvHr;
	private TextView tvStepRate;
	private TextView tvRri;
	private TextView tvStatus;
	private TextView tvVersion;
	private TextView tvName;
	private TextView tvBattery;
	private FileOutputStream outStream;
	private PrintWriter pw;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mDeviceConnector = DeviceConnector.getInstance(this);

		tvHr = (TextView) findViewById(R.id.hr);
		tvStepRate = (TextView) findViewById(R.id.steprate);
		tvRri = (TextView) findViewById(R.id.rri);
		tvStatus = (TextView) findViewById(R.id.status);
		tvName = findViewById(R.id.name);
		tvVersion = findViewById(R.id.version);
		tvBattery = findViewById(R.id.battery);

		if(ContextCompat.checkSelfPermission(this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
			ActivityCompat.requestPermissions(this,
					new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
					REQUEST_STORAGE);
		else
			createFile();
	}

	private void createFile() {
		try {
			File file = new File (Environment.getExternalStorageDirectory() +  File.separator + "jabra");
			if (!file.exists())
				file.mkdirs();

			file = new File( file,
					"/HR_" + Calendar.getInstance().getTime().toString() + ".txt");
			Log.e("Jabra", "path file " + file.getAbsolutePath());

			boolean newFile = file.createNewFile();
			if (!newFile)
				Toast.makeText(getApplicationContext(), "couldn't create a new file!", Toast.LENGTH_LONG).show();
			outStream = new FileOutputStream(file);
			pw = new PrintWriter(outStream);


		} catch (FileNotFoundException e) {
			outStream = null;
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == REQUEST_STORAGE && grantResults.length == permissions.length)
			createFile();
		else {
			Toast.makeText(getApplicationContext(), "without Storage permission data is not saved", Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		mDeviceConnector.registerPresenter(mPresenter);

		JabraDevice device = mDeviceConnector.getConnectedDevice();
		if (device == null || !device.isConnected()) {
			finish();
		} else {
			subscribeToEvents(device);
			//getDeviceInfo(device);
		}
	}

	private void getDeviceInfo(JabraDevice device) {
		// Synchronous calls
		tvName.setText(device.getNameFromTransport());

		device.getVersion(new Callback<String>() {
			@Override
			public void onProvided(String value) {
				tvVersion.setText(value);
			}

			@Override
			public void onError(JabraError error, Bundle params) {
				tvVersion.setText("??");
			}
		});
		device.getBatteryStatus(1, new Listener<BatteryStatus>() {
			@Override
			public void onProvided(BatteryStatus value) {
				tvBattery.setText(value.getLevel() + "%" + (value.isCharging() ? " CHG " : "") + (value.isLow() ? " LOW " : ""));
			}

			@Override
			public void onError(JabraError error, Bundle params) {
				tvBattery.setText("??");
			}
		});
	}

	private void subscribeToEvents(JabraDevice device) {
		Set<SensorData.DataType> data = new HashSet<>();
		Collections.addAll(data, SensorData.DataType.values());
		device.subscribeToSensorData(data, mEventListener);
	}

	private void unsubscribeToEvents(JabraDevice device) {
		device.unsubscribeFromSensorData(mEventListener);
	}


	@Override
	protected void onStop() {
		super.onStop();
		mDeviceConnector.unregisterPresenter(mPresenter);
		if (mDeviceConnector.getConnectedDevice() != null && mDeviceConnector.getConnectedDevice().isConnected())
		unsubscribeToEvents(mDeviceConnector.getConnectedDevice());
		if (outStream != null && pw != null)
			try {
				pw.flush();
				pw.close();
				outStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}


	private Listener<SensorData> mEventListener = new Listener<SensorData>() {
		@Override
		public void onProvided(SensorData value) {
			String hr = String.valueOf(value.getHeartRate());
			tvHr.setText(hr);
			tvStepRate.setText(String.valueOf(value.getStepRate()));
			Log.d("RRI", value.getRRIdata().toString());
			tvRri.setText(value.getRRIdata().toString());

			if (outStream != null && pw != null) {
				pw.println(hr);
			}
		}

		@Override
		public void onError(JabraError error, Bundle params) {
			String msg = error.name();
			if (error == JabraError.BUSY && params != null) {
				int uid = params.getInt(com.jabra.sdk.api.Callback.Keys.UID.name(), 0);
				String[] names = getPackageManager().getPackagesForUid(uid);
				StringBuilder sb = new StringBuilder();
				sb.append("Device sensors owned by ");
				sb.append(Arrays.toString(names));
				sb.append(" - you may listen to whatever data is produced, but you cannot change the setup");
				msg += ": " + sb.toString();
			}

			Log.d(TAG, "onError() called with: error = [" + error + "], params = [" + params + "]   (" + msg + ")");
			tvHr.setText(null);
			tvStepRate.setText(null);
			tvRri.setText(null);
			tvStatus.setText(msg);
		}
	};

	private DeviceConnector.Presenter2 mPresenter = new DeviceConnector.Presenter2() {
		@Override
		public void showMessage(String message, boolean loading) {
			Log.w(TAG, "showMessage() called with: message = [" + message + "], loading = [" + loading + "]");
		}

		@Override
		public void noDevice() {
			finish();
		}

		@Override
		public void updateConnectionStatus(boolean connected) {
			if (!connected) {
				finish();
			}
		}
	};
}
