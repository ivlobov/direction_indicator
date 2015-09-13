/*
 * Copyright (c) 2014, Lobov I.V.
 */
package ivl.example.orientation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import Jama.Matrix;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.view.GestureDetector.OnGestureListener;

public class OrientationView extends GLSurfaceView 
				implements GLSurfaceView.Renderer, SensorEventListener, OnGestureListener{
    
	private GestureDetector gestureDetector;
	private MotionEvent prevEvent = null;
	
	private FloatBuffer triangles;
	private FloatBuffer triangle_normales;
    private FloatBuffer triangle_colors_1;
    private FloatBuffer triangle_colors_2;
    
    private float[] accelerometerValue = null;
    private float[] magneticValue = null;
    
	private int triangleCount = 8;
	
	private Matrix startMatrix = null;
	private Matrix rotationMatrix = Matrix.identity(4, 4);
	private Matrix scrollMatrix = Matrix.identity(4, 4);
	
	private Handler handler = new Handler();
	
	private class FlingRotation implements Runnable {
		private float distX;
		private float distY;
		
		public FlingRotation(float dX, float dY) {
			distX = dX;
			distY = dY;
		}
		
		@Override
		public void run() {
			synchronized (handler) {
				if (Math.sqrt(distX*distX + distY*distY) > 5) {
					doScroll(distX, distY);
					handler.postDelayed(new FlingRotation(0.8f * distX, 0.8f * distY), 20); 
				}
			}
		}
	}
	
	private float size = 1;

	private Point displayOrientationPoint = new Point(SensorManager.AXIS_X, SensorManager.AXIS_Y);
 
	private ToneGenerator tone = null;

	

    public OrientationView(Context context){
        super(context);
        initSurface(context);
    }

	private void initSurface(Context context) {
        prepareData();
		setRenderer(this);
        gestureDetector = new GestureDetector(context, this);
	}
    
    public OrientationView(Context context, AttributeSet attrs){
        super(context, attrs);
        initSurface(context);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
    	boolean result = false;
    	if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
    		if (prevEvent != null) {
    			if (prevEvent.getPointerCount() >= 2 && event.getPointerCount() == 2) { 
    				result = onRotation(prevEvent, event);
    			} else {
    				if (prevEvent.getPointerCount() == 3 && event.getPointerCount() == 3) {
    					result = onScale(prevEvent, event);
    				}
    			}
    		}
			if (prevEvent != null) prevEvent.recycle();
    		prevEvent = MotionEvent.obtain(event);
    	} else {
    		if (prevEvent != null) prevEvent.recycle();
    		prevEvent = null;
    	}
    	return gestureDetector.onTouchEvent(event) || result;
    }    
    
	private void prepareData() {
		triangle_colors_1 = createFloatBuffer(prepareColors(triangleCount, 
    										 new float[]{ 1.0f, 0.1f, 0.1f, 0.0f },
    										 new float[]{ 0.86f, 0.72f, 0.05f, 0.0f }));
    	triangle_colors_2 = createFloatBuffer(prepareColors(triangleCount, 
    			                             new float[]{ 0.1f, 1.0f, 0.1f, 0.0f }, 
											 new float[]{ 0.86f, 0.72f, 0.05f, 0.0f }));
    	
    	float[] trianglesArray = prepareCoords(triangleCount, 0.45f, 0.2f);
    	triangles = createFloatBuffer(trianglesArray);
    	triangle_normales = createFloatBuffer(prepareNormales(triangleCount, trianglesArray));
	}
    
	private float[] calculateNormal(float x0, float y0, float z0, 
			float x2, float y2, float z2, float x1, float y1, float z1 ) {
		float[] result = new float[3];

		float qX = x1 - x0;
		float qY = y1 - y0;
		float qZ = z1 - z0;
		
		float pX = x2 - x0;
		float pY = y2 - y0;
		float pZ = z2 - z0;

		result[0] = pY*qZ - pZ*qY;
		result[1] = pZ*qX - pX*qZ;
		result[2] = pX*qY - pY*qX;
		
		return result;
	}
	
    private float[] prepareNormales(int count, float[] a) {
    	int step = 3 * 3;
    	int elementCount = step * count;
    	
    	float[] result = new float[elementCount];
		for (int i = 0; i < elementCount; i += step) {
			float[] normal = calculateNormal(a[i + 0], a[i + 1], a[i + 2],
												a[i + 3], a[i + 4], a[i + 5], 
												a[i + 6], a[i + 7], a[i + 8]);
			for (int j = 0; j < step; j++) result[i + j] = normal[j % 3];
		}
		return result;
	}

	public float[] prepareCoords(int count, float h, float r) {
    	int elementCount = 3 * 3 * count;
		float dAngle = (float) (2.0 * Math.PI / count); 
    	float[] result = new float[elementCount];
    	float angle = 0;
    	for (int i = 0; i < elementCount; i += 3) {
    		switch (i / 3 % 3) {
	    		case 0: {
	    	    	result[i + 0] = 0.0f; 
	    	    	result[i + 1] = h; 
	    	    	result[i + 2] = 0.0f;
	    	    	angle += dAngle;
	    	    	break;
	    		}
	    		case 1: {
	        		result[i + 0] = (float) (r * Math.cos(angle));
	        		result[i + 1] = 0.0f;
	        		result[i + 2] = (float) (r * Math.sin(angle));
	        		break;
	    		}
	    		case 2: {
	        		result[i + 0] = (float) (r * Math.cos(angle + dAngle));
	        		result[i + 1] = 0.0f;
	        		result[i + 2] = (float) (r * Math.sin(angle + dAngle));
	        		break;
	    		}
    		}
    	}
    	return result;
    }
    
    public float[] prepareColors(int count, float[] color1, float[] color2) {
    	int elementCount = 3 * 4 * count;
    	float[] result = new float[elementCount];
    	 
    	for (int j = 0; j < elementCount; j += 4) {
    		for (int i = 0; i < 4; i++) result[i + j] = ((j / 4 % 3 == 0) ? color1[i] : color2[i]);
    	}
    	return result;
    }
    
    private double[] convertArrayFloatToDouble(float[] array) {
    	double[] result = new double[array.length];
    	for (int i = 0; i < array.length; i++) result[i] = array[i];
    	return result;
    }

    private float[] convertArrayDoubleToFloat(double[] array) {
    	float[] result = new float[array.length];
    	for (int i = 0; i < array.length; i++) result[i] = (float)array[i];
    	return result;
    }
    
    
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
    	displayOrientationPoint = getDisplayOrientation();    	
    	
        gl.glViewport(0, 0, width, height);
        float ratio = (float) width / height;
        gl.glMatrixMode(GL10.GL_PROJECTION);        
        gl.glLoadIdentity();                    
        if (ratio < 1) {
        	gl.glOrthof(-ratio, ratio, -1, 1, 0, 6);
        } else {
        	gl.glOrthof(-1, 1, -1/ratio, 1/ratio, 0, 6);
        }
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glTranslatef(0.0f, 0.0f, -3.0f);
        
        GL11 gl11 = (GL11) gl;
        
        float buffer[] = new float[16];
        gl11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, buffer, 0);

        startMatrix = new Matrix(convertArrayFloatToDouble(buffer), 4);
    }

	private Point getDisplayOrientation() {
		WindowManager winManager = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE));
    	int displayOrientation = winManager.getDefaultDisplay().getRotation();
    	
    	switch (displayOrientation) {
        case Surface.ROTATION_0:
        	return new Point(SensorManager.AXIS_X, SensorManager.AXIS_Y);
        case Surface.ROTATION_90:
        	return new Point(SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X);

        case Surface.ROTATION_180:
        	return new Point(SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y);

        case Surface.ROTATION_270:
        	return new Point(SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X);
    	
    	default:
    		return new Point(SensorManager.AXIS_X, SensorManager.AXIS_Y);
    	}
	}        
    
    

	public static FloatBuffer createFloatBuffer(float[] floatArray) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(floatArray.length * 4);
        byteBuffer.order(ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        floatBuffer.put(floatArray);
        floatBuffer.position(0);
        return floatBuffer;
    }    
    
    public void drawPointer(GL10 gl) {
        gl.glPushMatrix();
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        gl.glEnableClientState(GL10.GL_NORMAL_ARRAY);
        
        gl.glNormalPointer(GL10.GL_FLOAT, 0, triangle_normales);
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, triangle_colors_1);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, triangles);
        gl.glDrawArrays(GL10.GL_TRIANGLES, 0, triangleCount * 3);
        
        gl.glRotatef(180, 0.0f, 0.0f, 1.0f);
        gl.glColorPointer(4, GL10.GL_FLOAT, 0, triangle_colors_2);
        gl.glDrawArrays(GL10.GL_TRIANGLES, 0, triangleCount * 3);
        
        gl.glDisableClientState(GL10.GL_NORMAL_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);       
        gl.glPopMatrix();
    }
    
    
    @Override
    public void onDrawFrame(GL10 gl) {
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
        
        gl.glLoadMatrixf(convertArrayDoubleToFloat(startMatrix.getColumnPackedCopy()), 0);
   	    gl.glMultMatrixf(convertArrayDoubleToFloat(rotationMatrix.getColumnPackedCopy()), 0);
   	    gl.glMultMatrixf(convertArrayDoubleToFloat(scrollMatrix.getColumnPackedCopy()), 0);
        gl.glPushMatrix();
        
        drawPointer(gl); 
        
        gl.glPopMatrix();
    }    

	@Override
    public void onSurfaceCreated(GL10 gl, EGLConfig eglConfig) {
        gl.glClearColor(0.1f, 0.1f, 0.1f, 0.0f);
        gl.glLightModelx(GL10.GL_LIGHT_MODEL_TWO_SIDE, GL10.GL_TRUE);
        gl.glEnable(GL10.GL_LIGHTING);
        gl.glEnable(GL10.GL_LIGHT1);
        gl.glEnable(GL10.GL_COLOR_MATERIAL);
        gl.glEnable(GL10.GL_DEPTH_TEST);
        gl.glEnable(GL10.GL_NORMALIZE);
        prepareLight(gl, GL10.GL_LIGHT1);
    }

    private void prepareLight(GL10 gl, int lightNumber) {
    	gl.glLightfv(lightNumber, GL10.GL_SPECULAR, 
    			createFloatBuffer(new float[]{0.9f, 0.9f, 0.9f, 0.0f}));
    	gl.glLightfv(lightNumber, GL10.GL_AMBIENT, 
    			createFloatBuffer(new float[]{0.1f, 0.1f, 0.1f, 0.0f}));
    	gl.glLightfv(lightNumber, GL10.GL_DIFFUSE, 
    			createFloatBuffer(new float[]{0.7f, 0.7f, 0.7f, 0.0f}));
    	gl.glLightfv(lightNumber, GL10.GL_POSITION, 
    			createFloatBuffer(new float[]{0.0f, 0.0f, 10.0f, 1.0f}));
    	gl.glLightf(lightNumber, GL10.GL_SHININESS, 30.0f);
    }

    private Matrix createSensorRotationMatrix() {
    	float[] R = new float[16];
    	float[] I = new float[16];
    
		if (magneticValue != null && accelerometerValue != null) {
			SensorManager.getRotationMatrix(R, I, accelerometerValue, magneticValue);
			SensorManager.remapCoordinateSystem(R, displayOrientationPoint.x, displayOrientationPoint.y, R);
			Matrix newRotateMatrix = new Matrix(convertArrayFloatToDouble(R), 4);
			return newRotateMatrix;
		}
    	return rotationMatrix;
    }
    
	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
			case Sensor.TYPE_ACCELEROMETER: {
				accelerometerValue = event.values.clone();
				break;
			}
			case Sensor.TYPE_MAGNETIC_FIELD: {
				magneticValue = event.values.clone();
				break;
			}
		}
		rotationMatrix = createSensorRotationMatrix();
		invalidate();
	}

	protected Matrix createScrollRotationMatrix(double angle, double x, double y, double z) {
		double radAngle = Math.PI * angle / 180.0;
		double cosA = Math.cos(radAngle); 
		double sinA = Math.sin(radAngle);
		
		return new Matrix(new double[]
			{ cosA + (1 - cosA)*x*x,	(1 - cosA)*x*y + sinA*z,	(1 - cosA)*x*z - sinA*y,	0,
			(1 - cosA)*y*x - sinA*z,	cosA + (1 - cosA)*y*y,		(1 - cosA)*y*z + sinA*x,	0, 	
			(1 - cosA)*z*x + sinA*y,	(1 - cosA)*z*y - sinA*x,	cosA + (1 - cosA)*z*z,		0,
			0 ,							0 ,							0, 							1}, 4);
	}
	
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean onDown(MotionEvent e) {
		return true;
	}

	public void vibrate(int duration) {
		 Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
		 if (vibrator != null) vibrator.vibrate(duration);		
	}

	public void sound(int duration) {
		try {
			if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_ALARM, 90);
			tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, duration);			
		} catch (Exception e) {
		    e.printStackTrace();
		}		
	}
	
	@Override
	public void onShowPress(MotionEvent e) {
		vibrate(20);
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		float[] a = new float[4];
        SensorManager.getOrientation(convertArrayDoubleToFloat(rotationMatrix.getColumnPackedCopy()), a);
    	doScroll(distanceX, distanceY);
		return true;
	}

	private synchronized void doScroll(float distanceX, float distanceY) {
		Matrix matrixX = createScrollRotationMatrix(distanceX/size,  0, -1, 0);
    	Matrix matrixY = createScrollRotationMatrix(distanceY/size, -1,  0, 0);
        
		scrollMatrix = rotationMatrix.inverse()
				.times(matrixY).times(matrixX)
				.times(rotationMatrix)
				.times(scrollMatrix);
        
		invalidate();
	}

	@Override
	public void onLongPress(MotionEvent e) {
		sound(25);
		Activity mainActivity = (Activity)getContext();
		
		if (mainActivity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
			mainActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		} else {
			mainActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		if (Math.sqrt(velocityX*velocityX + velocityY*velocityY) > 5) {
			handler.post(new FlingRotation(-velocityX/50f, -velocityY/50f));
			return true;
		}
		return false;
	}

	protected void resetScrollMatrix() {
		scrollMatrix = Matrix.identity(4, 4);
		invalidate();
	}

	protected boolean onRotation(MotionEvent event1, MotionEvent event2) {
    	double angle1 = Math.toDegrees(Math.atan2(event1.getY(0) - event1.getY(1), 
    												event1.getX(0) - event1.getX(1)));
    	double angle2 = Math.toDegrees(Math.atan2(event2.getY(0) - event2.getY(1), 
    												event2.getX(0) - event2.getX(1)));
    	
    	Matrix matrixZ = createScrollRotationMatrix(angle2 - angle1,  0, 0, -1);
    	
		scrollMatrix = rotationMatrix.inverse()
				.times(matrixZ)
				.times(rotationMatrix)
				.times(scrollMatrix);
        
		invalidate();
		return true;
	}

	private double getTriangleRadius(float x0, float y0, float x1, float y1, float x2, float y2) {
		double a = Math.sqrt(Math.pow(x1 - x0, 2.0) + Math.pow(y1 - y0, 2.0));
		double b = Math.sqrt(Math.pow(x2 - x1, 2.0) + Math.pow(y2 - y1, 2.0));
		double c = Math.sqrt(Math.pow(x0 - x2, 2.0) + Math.pow(y0 - y2, 2.0));
		double p = (a + b + c)/2.0;

		return (float) (a*b*c/(4*Math.sqrt(p * (p - a) * (p - b) * (p - c))));
	}
	
	protected boolean onScale(MotionEvent prevEvent, MotionEvent event) {
		double radius1 = getTriangleRadius(prevEvent.getX(0), prevEvent.getY(0),
											prevEvent.getX(1), prevEvent.getY(1),
											prevEvent.getX(2), prevEvent.getY(2));
		double radius2 = getTriangleRadius(event.getX(0), event.getY(0),
											event.getX(1), event.getY(1),
											event.getX(2), event.getY(2));
		double scale = radius1/radius2; 
		
		Matrix scaleMatrix = createScaleMatrix(scale);
		scrollMatrix = rotationMatrix.inverse()
				.times(scaleMatrix)
				.times(rotationMatrix)
				.times(scrollMatrix);
        
		invalidate();
		return true;
	}

	protected Matrix createScaleMatrix(double scale) {
		double[] array = {1, 0, 0, 0, 
						   0, 1, 0, 0,  
						   0, 0, 1, 0,
						   0, 0, 0, scale};
		return new Matrix(array, 4);
	}

	public void setScrollMatrix(Matrix matr) {
		scrollMatrix = matr;
		invalidate();
	}

	public Matrix getScrollMatrix() {
		return scrollMatrix;
	}
    
    
}
