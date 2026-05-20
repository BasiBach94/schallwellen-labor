package ride.logger;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener, LocationListener {
    private WebView webView;
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Location lastLocation;
    private final float[] accel = new float[3];
    private final float[] gyro = new float[3];
    private final float[] linear = new float[3];
    private final float[] gravity = new float[3];
    private final float[] magnetic = new float[3];
    private final float[] rotation = new float[5];
    private boolean running = true;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "NativeRide");
        webView.loadUrl("file:///android_asset/index.html");
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 9);
        }
    }

    @Override protected void onResume(){ super.onResume(); registerSensors(); startLocation(); }
    @Override protected void onPause(){ super.onPause(); sensorManager.unregisterListener(this); }

    private void registerSensors(){
        int delay = SensorManager.SENSOR_DELAY_GAME;
        int[] types = new int[]{Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GRAVITY, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ROTATION_VECTOR};
        for(int t: types){ Sensor s=sensorManager.getDefaultSensor(t); if(s!=null) sensorManager.registerListener(this,s,delay); }
    }
    private void startLocation(){
        try{
            if(Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, this);
            }
        }catch(Exception ignored){}
    }
    @Override public void onSensorChanged(SensorEvent e){
        if(!running)return;
        float[] target = null;
        if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER)target=accel;
        else if(e.sensor.getType()==Sensor.TYPE_GYROSCOPE)target=gyro;
        else if(e.sensor.getType()==Sensor.TYPE_LINEAR_ACCELERATION)target=linear;
        else if(e.sensor.getType()==Sensor.TYPE_GRAVITY)target=gravity;
        else if(e.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD)target=magnetic;
        else if(e.sensor.getType()==Sensor.TYPE_ROTATION_VECTOR)target=rotation;
        if(target!=null) System.arraycopy(e.values,0,target,0,Math.min(e.values.length,target.length));
        send();
    }
    @Override public void onAccuracyChanged(Sensor s,int a){}
    @Override public void onLocationChanged(Location l){ lastLocation=l; send(); }

    private String arr(float[] a,int max){ StringBuilder b=new StringBuilder("["); for(int i=0;i<Math.min(max,a.length);i++){ if(i>0)b.append(','); b.append(String.format(Locale.US,"%.5f",a[i])); } return b.append(']').toString(); }
    private String snapshot(){
        StringBuilder b=new StringBuilder("{");
        b.append("\"time\":").append(System.currentTimeMillis()).append(',');
        b.append("\"accel\":").append(arr(accel,3)).append(',');
        b.append("\"gyro\":").append(arr(gyro,3)).append(',');
        b.append("\"linear\":").append(arr(linear,3)).append(',');
        b.append("\"gravity\":").append(arr(gravity,3)).append(',');
        b.append("\"magnetic\":").append(arr(magnetic,3)).append(',');
        b.append("\"rotation\":").append(arr(rotation,5)).append(',');
        b.append("\"location\":");
        if(lastLocation==null)b.append("null"); else {
            b.append('{');
            b.append("\"lat\":").append(String.format(Locale.US,"%.7f",lastLocation.getLatitude())).append(',');
            b.append("\"lon\":").append(String.format(Locale.US,"%.7f",lastLocation.getLongitude())).append(',');
            b.append("\"alt\":").append(lastLocation.hasAltitude()?String.format(Locale.US,"%.2f",lastLocation.getAltitude()):"null").append(',');
            b.append("\"speed\":").append(lastLocation.hasSpeed()?String.format(Locale.US,"%.2f",lastLocation.getSpeed()*3.6f):"null").append(',');
            b.append("\"bearing\":").append(lastLocation.hasBearing()?String.format(Locale.US,"%.1f",lastLocation.getBearing()):"null").append(',');
            b.append("\"accuracy\":").append(lastLocation.hasAccuracy()?String.format(Locale.US,"%.1f",lastLocation.getAccuracy()):"null");
            b.append('}');
        }
        b.append('}'); return b.toString();
    }
    private void send(){ String js="window.onRideUpdate&&window.onRideUpdate("+snapshot()+");"; webView.post(()->webView.evaluateJavascript(js,null)); }
    public class Bridge { @JavascriptInterface public void setRunning(boolean r){running=r;} @JavascriptInterface public String snapshot(){return MainActivity.this.snapshot();} }
}
