package com.harsha.vizagsafety;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener, RecognitionListener {

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private static final int PICK_CONTACT_REQUEST = 2;
    private MapView map = null;
    private MyLocationNewOverlay mLocationOverlay;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private static final float FALL_THRESHOLD = 5.0f;
    private long lastFallTime;
    private SpeechRecognizer speechRecognizer;
    private String selectedContactNumber;
    private ToggleButton sosToggleButton;
    private ToggleButton voiceAutomationToggleButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        setContentView(R.layout.activity_main);

        // Initialize map
        map = findViewById(R.id.map);
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);

        // Request necessary permissions
        requestPermissionsIfNecessary(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.INTERNET
        });

        // Set up location overlay
        mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        mLocationOverlay.enableMyLocation();
        mLocationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
            GeoPoint myLocation = mLocationOverlay.getMyLocation();
            if (myLocation != null) {
                map.getController().setCenter(myLocation);
                fetchAndDisplayZones(myLocation);
            }
        }));
        map.getOverlays().add(mLocationOverlay);

        // Set up accelerometer for fall detection
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        // Set up speech recognizer for voice activation
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(this);
        startVoiceRecognition();

        // Set up select contact button
        TextView selectContactButton = findViewById(R.id.select_contact_button);
        selectContactButton.setOnClickListener(v -> openContactPicker());

        // Set up SOS toggle button
        sosToggleButton = findViewById(R.id.sos_toggle);
        sosToggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Enable SOS alerts
                Toast.makeText(this, "SOS Alert Enabled", Toast.LENGTH_SHORT).show();
            } else {
                // Disable SOS alerts
                Toast.makeText(this, "SOS Alert Disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // Set up voice automation toggle button
        voiceAutomationToggleButton = findViewById(R.id.voice_automation_toggle);
        voiceAutomationToggleButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Enable voice automation
                startVoiceRecognition();
                Toast.makeText(this, "Voice Automation Enabled", Toast.LENGTH_SHORT).show();
            } else {
                // Disable voice automation
                speechRecognizer.stopListening();
                Toast.makeText(this, "Voice Automation Disabled", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS_REQUEST_CODE);
            }
        }
    }

    private void startVoiceRecognition() {
        if (voiceAutomationToggleButton != null && voiceAutomationToggleButton.isChecked()) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            speechRecognizer.startListening(intent);
        }
    }

    private void openContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            Uri contactUri = data.getData();
            Cursor cursor = getContentResolver().query(contactUri, null, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                    String hasPhone = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));
                    if (hasPhone.equals("1")) {
                        Cursor phones = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{id}, null);
                        if (phones != null) {
                            if (phones.moveToFirst()) {
                                selectedContactNumber = phones.getString(phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                                Toast.makeText(this, "Contact selected: " + selectedContactNumber, Toast.LENGTH_LONG).show();
                            }
                            phones.close();
                        }
                    }
                }
                cursor.close();
            }
        }
    }

    private void sendLocationToContact() {
        if (selectedContactNumber != null) {
            GeoPoint myLocation = mLocationOverlay.getMyLocation();
            if (myLocation != null) {
                String message = "I need help. My current location is: http://maps.google.com/?q=" + myLocation.getLatitude() + "," + myLocation.getLongitude();
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(selectedContactNumber, null, message, null, null);
                Log.d("MainActivity", "Location sent to: " + selectedContactNumber);
            }
        } else {
            Toast.makeText(this, "No contact selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchAndDisplayZones(GeoPoint myLocation) {
        double latitude = myLocation.getLatitude();
        double longitude = myLocation.getLongitude();

        // Fetch nearby police stations and other government protection bodies
        fetchNearbyPlaces(latitude, longitude, "police");
        fetchNearbyPlaces(latitude, longitude, "government");

        // Assuming you have a method to process the fetched data and classify zones
        List<GeoPoint> greenZones = new ArrayList<>();
        List<GeoPoint> orangeZones = new ArrayList<>();
        List<GeoPoint> redZones = new ArrayList<>();
        List<GeoPoint> policeStations = new ArrayList<>();

        // Example data classification
        greenZones.add(new GeoPoint(latitude + 0.01, longitude));
        orangeZones.add(new GeoPoint(latitude - 0.01, longitude));
        redZones.add(new GeoPoint(latitude, longitude + 0.01));

        // Add fetched police stations to the list
        // This is just an example; you should populate this list based on your API response
        policeStations.add(new GeoPoint(latitude + 0.02, longitude + 0.02));

        // Display zones on map
        displayZonesOnMap(map, greenZones, orangeZones, redZones, policeStations);
    }

    private void fetchNearbyPlaces(double latitude, double longitude, String placeType) {
        // Use an API like Google Places API to fetch nearby places
        // Example API call (pseudo-code):
        // String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=" + latitude + "," + longitude + "&radius=5000&type=" + placeType + "&key=YOUR_API_KEY";
        // Make the API call and process the response

        // For demonstration purposes, let's assume the response is processed and we get a list of GeoPoints
        List<GeoPoint> fetchedPlaces = new ArrayList<>();
        // Add fetched places to the respective lists based on placeType
        if (placeType.equals("police")) {
            // Add to police stations list
            // Example:
            fetchedPlaces.add(new GeoPoint(latitude + 0.02, longitude + 0.02));
        } else if (placeType.equals("government")) {
            // Add to government protection bodies list
            // Example:
            fetchedPlaces.add(new GeoPoint(latitude + 0.03, longitude + 0.03));
        }

        // You can update the relevant list here or pass it to another method to handle it
    }

    private void displayZonesOnMap(MapView mapView, List<GeoPoint> greenZones, List<GeoPoint> orangeZones, List<GeoPoint> redZones, List<GeoPoint> policeStations) {
        for (GeoPoint point : greenZones) {
            addCircleOverlay(mapView, point, 0x5000FF00); // Green with transparency
        }
        for (GeoPoint point : orangeZones) {
            addCircleOverlay(mapView, point, 0x50FFA500); // Orange with transparency
        }
        for (GeoPoint point : redZones) {
            addCircleOverlay(mapView, point, 0x50FF0000); // Red with transparency
        }
        for (GeoPoint point : policeStations) {
            addCircleOverlay(mapView, point, 0x500000FF); // Blue with transparency for police stations
        }
    }

    private void addCircleOverlay(MapView mapView, GeoPoint point, int color) {
        Polygon circle = new Polygon(mapView);
        circle.setPoints(Polygon.pointsAsCircle(point, 500)); // 500 meters radius
        circle.setFillColor(color);
        circle.setStrokeColor(color);
        circle.setStrokeWidth(2);
        mapView.getOverlays().add(circle);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;

            if (acceleration > FALL_THRESHOLD) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFallTime > 2000) {
                    lastFallTime = currentTime;
                    if (sosToggleButton != null && sosToggleButton.isChecked()) {
                        sendLocationToContact();
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        // Do nothing
    }

    @Override
    public void onBeginningOfSpeech() {
        // Do nothing
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        // Do nothing
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        // Do nothing
    }

    @Override
    public void onEndOfSpeech() {
        // Restart voice recognition if automation is enabled
        startVoiceRecognition();
    }

    @Override
    public void onError(int error) {
        // Restart voice recognition if automation is enabled
        startVoiceRecognition();
    }

    @Override
    public void onResults(Bundle results) {
        List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null) {
            for (String match : matches) {
                if (match.toLowerCase().contains("help")) {
                    sendLocationToContact();
                    break;
                }
            }
        }
        // Restart voice recognition if automation is enabled
        startVoiceRecognition();
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        // Do nothing
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        // Do nothing
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
}
