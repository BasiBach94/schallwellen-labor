package sensor.lab;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity implements SensorEventListener, LocationListener {
    private WebView webView;
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private final Map<Integer, Sensor> sensors = new HashMap<>();
    private final Map<Integer, float[]> values = new HashMap<>();
    private Location lastLocation;
    private boolean running = true;
    private int sensorDelay = SensorManager.SENSOR_DELAY_UI;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
        }
    }

    @Override protected void onResume() { super.onResume(); registerAll(); startLocation(); }
    @Override protected void onPause() { super.onPause(); sensorManager.unregisterListener(this); }

    private void registerAll() {
        sensors.clear();
        List<Sensor> list = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : list) {
            sensors.put(sensor.getType(), sensor);
            sensorManager.registerListener(this, sensor, sensorDelay);
        }
    }

    private void startLocation() {
        try {
            if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 0, this);
            }
        } catch (Exception ignored) {}
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (!running) return;
        float[] copy = new float[e.values.length];
        System.arraycopy(e.values, 0, copy, 0, e.values.length);
        values.put(e.sensor.getType(), copy);
        sendSnapshot();
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public void onLocationChanged(Location location) { lastLocation = location; sendSnapshot(); }

    private void sendSnapshot() {
        String json = buildJson();
        String js = "window.onNativeSensorUpdate && window.onNativeSensorUpdate(" + json + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private String arr(float[] a) {
        if (a == null) return "[]";
        StringBuilder b = new StringBuilder("[");
        for (int i=0;i<a.length;i++) { if (i>0) b.append(','); b.append(String.format(Locale.US,"%.6f",a[i])); }
        return b.append(']').toString();
    }

    private String buildJson() {
        StringBuilder b = new StringBuilder();
        b.append('{');
        b.append("\"device\":{");
        b.append("\"manufacturer\":\"").append(esc(Build.MANUFACTURER)).append("\",");
        b.append("\"model\":\"").append(esc(Build.MODEL)).append("\",");
        b.append("\"android\":\"").append(esc(Build.VERSION.RELEASE)).append("\",");
        b.append("\"sdk\":").append(Build.VERSION.SDK_INT).append(',');
        b.append("\"battery\":").append(getBattery()).append(',');
        b.append("\"charging\":").append(isCharging()).append(',');
        b.append("\"network\":\"").append(esc(network())).append("\"" ).append("},");
        b.append("\"location\":");
        if (lastLocation == null) b.append("null,"); else {
            b.append('{');
            b.append("\"lat\":").append(String.format(Locale.US,"%.7f",lastLocation.getLatitude())).append(',');
            b.append("\"lon\":").append(String.format(Locale.US,"%.7f",lastLocation.getLongitude())).append(',');
            b.append("\"alt\":").append(lastLocation.hasAltitude()?String.format(Locale.US,"%.2f",lastLocation.getAltitude()):"null").append(',');
            b.append("\"speed\":").append(lastLocation.hasSpeed()?String.format(Locale.US,"%.2f",lastLocation.getSpeed()*3.6f):"null").append(',');
            b.append("\"accuracy\":").append(lastLocation.hasAccuracy()?String.format(Locale.US,"%.1f",lastLocation.getAccuracy()):"null");
            b.append("},");
        }
        b.append("\"sensors\":[");
        boolean first = true;
        for (Sensor s : sensors.values()) {
            if (!first) b.append(','); first=false;
            b.append('{');
            b.append("\"type\":").append(s.getType()).append(',');
            b.append("\"name\":\"").append(esc(s.getName())).append("\",");
            b.append("\"vendor\":\"").append(esc(s.getVendor())).append("\",");
            b.append("\"power\":").append(String.format(Locale.US,"%.3f",s.getPower())).append(',');
            b.append("\"range\":").append(String.format(Locale.US,"%.3f",s.getMaximumRange())).append(',');
            b.append("\"resolution\":").append(String.format(Locale.US,"%.6f",s.getResolution())).append(',');
            b.append("\"values\":").append(arr(values.get(s.getType())));
            b.append('}');
        }
        b.append(']');
        b.append('}');
        return b.toString();
    }

    private int getBattery() {
        try { BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE); return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY); } catch(Exception e){return -1;}
    }
    private boolean isCharging() {
        try { BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE); return bm.isCharging(); } catch(Exception e){return false;}
    }
    private String network() {
        try { ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE); NetworkInfo ni=cm.getActiveNetworkInfo(); return ni!=null && ni.isConnected()?ni.getTypeName():"offline"; } catch(Exception e){return "unknown";}
    }

    public class Bridge {
        @JavascriptInterface public void setRunning(boolean r) { running = r; }
        @JavascriptInterface public void setRate(String rate) {
            if ("slow".equals(rate)) sensorDelay = SensorManager.SENSOR_DELAY_NORMAL;
            else if ("fast".equals(rate)) sensorDelay = SensorManager.SENSOR_DELAY_GAME;
            else if ("max".equals(rate)) sensorDelay = SensorManager.SENSOR_DELAY_FASTEST;
            else sensorDelay = SensorManager.SENSOR_DELAY_UI;
            sensorManager.unregisterListener(MainActivity.this);
            registerAll();
        }
        @JavascriptInterface public String snapshot() { return buildJson(); }
        @JavascriptInterface public void openLocationSettings() { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
    }
}
