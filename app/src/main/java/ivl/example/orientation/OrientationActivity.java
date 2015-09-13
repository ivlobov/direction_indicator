/*
 * Copyright (c) 2014, Lobov I.V.
 */

package ivl.example.orientation;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import Jama.Matrix;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

public class OrientationActivity extends Activity {

	private static final String O_FILE_NAME = "matrix.dat";
	
	private SensorManager sensorManager;
	private Sensor accelerometer;
	private Sensor magnetic;
		
	private OrientationView orientSurface;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		orientSurface = new OrientationView(this);
		setContentView(orientSurface);
		
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		
		accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		magnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
	}
	 
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	protected void onResume() {
		try {
			FileInputStream fis = openFileInput(O_FILE_NAME);
			ObjectInputStream ois = new ObjectInputStream(fis); 
			Matrix matr = (Matrix) ois.readObject();
			ois.close();
			if (matr != null) orientSurface.setScrollMatrix(matr);
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		
		super.onResume();
		orientSurface.onResume();
		
		sensorManager.registerListener(orientSurface, accelerometer, SensorManager.SENSOR_DELAY_GAME);
		sensorManager.registerListener(orientSurface, magnetic, SensorManager.SENSOR_DELAY_GAME);
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		sensorManager.unregisterListener(orientSurface);
		orientSurface.onPause();
		
		try {
			FileOutputStream fos = openFileOutput(O_FILE_NAME, Context.MODE_PRIVATE);
			ObjectOutputStream oos = new ObjectOutputStream(fos); 
			oos.writeObject(orientSurface.getScrollMatrix());
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void showAbout() {
		Toast.makeText(this, getString(R.string.about_text), Toast.LENGTH_LONG).show();
	}

	public void showCloseDialog() {
		AlertDialog closeDialog; 
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle(R.string.close_dialog_title);
        builder.setInverseBackgroundForced(true);
        builder.setNegativeButton(R.string.no, null);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				OrientationActivity.this.finish();
			}
        });
        
        closeDialog = builder.create();			
		closeDialog.show();
	}
	
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.close: {
				showCloseDialog();
				break;
			}
			case R.id.about: {
				showAbout();
				break;
			}
			case R.id.reset: {
				resetDirection();
				break;
			}
		}
		return true;
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)  {
	    if (keyCode == KeyEvent.KEYCODE_BACK) {
	        showCloseDialog();
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
	}
	

	private void resetDirection() {
		orientSurface.resetScrollMatrix();
	}
	

}
