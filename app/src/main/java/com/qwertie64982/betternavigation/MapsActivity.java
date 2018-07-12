package com.qwertie64982.betternavigation;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.geometry.Point;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

// Credit app icon to Icon Pond via Flaticon (under Flaticon

// Do not allow the app to rotate or fall asleep because savedInstanceState is incomplete, because it lacks markerHashMap, which must be Parceled because of Markers

/**
 * Main activity - shows a map and a text box for directions
 */
public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    // Views/UI
    private Button getBetterDirectionsButton;        // Gets my customized directions
    private Button getGoogleDirectionsButton;        // Gets Google's directions (to compare)
    private GoogleMap mMap;                          // Map on screen
    private ProgressBar mapsProgressBar;             // Loading icon for when it's retrieving directions
    private TextView directionsTextView;             // TextView where directions are displayed
    private Marker locationMarker;                   // Marker representing the user's current location
    private ProgressDialog progressDialog;           // Halts the app while the step counter catches up after testing finishes
    private TextToSpeech textToSpeech;               // Text-to-speech object that speaks the user's directions
    private SensorManager sensorManager;             // Manages sensors
    private Sensor proximitySensor;                  // Proximity sensor
    private Sensor stepCountSensor;                  // Step counter
    private SensorEventListener sensorEventListener; // Listens for sensor changes

    // Data
    private LocationManager locationManager;        // Manages location data
    private Criteria criteria;                      // Describes the best location provider
    private LocationListener locationListener;      // Listens for location changes
    private LinkedHashSet<LatLng> latLngs;          // Stores the LatLng where each instruction takes place (LinkedHashSet to preserve order and remove duplicates)
    private ArrayList<LatLng> latLngsArrayList;     // Same as above (ArrayList so it can be accessed by index)
    private ArrayList<LatLng> polylineArrayList;    // Contains the polyline LatLngs, so either set of directions can check if the user has deviated
    private ArrayList<String> directions;           // Stores each user-friendly direction as Strings
    private HashMap<Integer, Marker> markerHashMap; // Stores each marker on the map which represents an instruction
    private boolean isTesting;                      // Keeps track of whether or not a test is running
    private boolean isScreenTouchable;              // Keeps track of if the screen is touchable or not (so I'm not writing flags on every proximity update)
    private int directionsCode;                     // Keeps track of what kind of directions we're using (in case of recalculation)
    private int speakingDistance;                   // Counts repeatedly 0-2, and textToSpeech tells the user how far away the next point is on 2
    private int currentDirection;                   // Keeps track of which direction the user is currently on (used as index for above data sets)
    private int currentPolylineStep;                // Keeps track of which step on the path the user is currently on (used to determine if they've deviated from the path)
    private int totalDirections;                    // Keeps track of the total number of directions (so I'm not using a size() method constantly)
    private int currentSteps;                       // Keeps track of the current step count from the step counter
    private int initialSteps;                       // Keeps track of how many steps were in the counter when the test started
    private long timeStart;                         // Keeps track of when a test started for timing purposes
    private Wait10SecondsTask wait10SecondsTask;    // Counts for 10 seconds while the step counter catches up

    // Finals
    private final static String TAG = "MapsActivity";   // Logcat tag
    private final static String ROUTES = "routes";      // Used to find the "routes" Array in a Google Directions API JSON response
    private final static String LEGS = "legs";          // Used to find the "legs" Array [...]
    private final static String STEPS = "steps";        // Used to find the "steps" Array [...]
    private final static String POLYLINE = "polyline";  // Used to find the "polyline" Object [...]
    private final static String POINTS = "points";      // Used to find the "points" String [...]
    private final static String HTML_INSTRUCTIONS = "html_instructions";    // Used to find the "html_instructions" String [...]
    private final static String START_LOCATION = "start_location";          // Used to find the "start_location" Array [...]
    private final static String END_LOCATION = "end_location";              // Used to find the "end_location" Array [...]
    private final static String LAT = "lat";            // Used to find the "lat" Double [...]
    private final static String LONG = "lng";           // Used to find the "long" Double [...]
    private final static String COULD_NOT_CONNECT = "Could not connect!";   // General string that represents when the app could not reach Google
    private final static int TILE_SIZE = 256;           // Size of tiles (in pixels) that are used to draw a GoogleMap
    private final static long UPDATE_INTERVAL = 1000;   // How often to update GPS (in ms)
    private final int MAP_REQUEST_CODE = 0;             // Code identifying a location request from this app (map)
    private final int LOCATION_REQUEST_CODE = 1;        // Code identifynig a location request from this app (set up locationManager)
    private final int NO_DIRECTIONS_CODE = 0;           // Code identifying that no directions are currently used
    private final int BETTER_DIRECTIONS_CODE = 1;       // Code identifying that my directions are currently used
    private final int GOOGLE_DIRECTIONS_CODE = 2;       // Code identifying that Google's directions are currently used
    private final int MAX_ACCURACY_RADIUS = 9;          // How accurate (in m) the GPS must be for the app to safely assume the user has reached a point and is ready to move on
    private final int MAX_DISTANCE_TO_POINT = 7;        // How far (in m) the user must be from a point for the app to consider them at it
//    private final int DEVIATION_DISTANCE = 10;          // How far (in m) the user must be from their current path for the app to recalculate their directions
    private final int PROXIMITY_DISTANCE = 5;           // How far (in cm) for the proximity sensor to turn the screen off
    private final double DEVIATION_DISTANCE = 0.013;    // How far (in Google Map world coordinates) the user must be from their current path for the app to recalculate their directions
    private final double STEP_LENGTH = 0.8098;          // Step length (in m/step) used to determine how far someone has gone (instead of GPS)
    private final String LATITUDES_KEY = "latitudes";   // Key for getting latitudes from savedInstanceState (since LatLngs aren't Serializable)
    private final String LONGITUDES_KEY = "longitudes"; // Key for getting longitudes from savedInstanceState (")
    private final String POLYLINE_LATITUDES_KEY = "polylineLatitudes";      // Key for getting polylineLatitudes from savedInstanceState (")
    private final String POLYLINE_LONGITUDES_KEY = "polylineLongitudes";    // Key for getting polylineLongitudes from savedInstanceState (")
    private final String DIRECTIONS_KEY = "directions"; // Key for getting directinos from savedInstanceState (")
//    private final String MARKER_HASHMAP_KEY = "markerHashMap";              // Key for getting markerHashMap from savedInstanceState (")
    private final String IS_TESTING_KEY = "isTesting";                      // Key for getting isTesting from savedInstanceState (")
    private final String IS_SCREEN_TOUCHABLE_KEY = "isScreenTouchable";     // Key for getting isScreenTouchable from savedInstanceState (")
    private final String DIRECTIONS_CODE_KEY = "directionsCode";            // Key for getting directionsCode from savedInstanceState (")
    private final String CURRENT_DIRECTION_KEY = "currentDirection";        // Key for getting currentDirection from savedInstanceState (")
    private final String CURRENT_POLYLINE_STEP_KEY = "currentPolylineStep"; // Key for getting currentDirection from savedInstanceState (")
    private final String TOTAL_DIRECTIONS_KEY = "totalDirections";          // Key for getting totalDirections from savedInstanceState (")
    private final String CURRENT_STEPS_KEY = "currentSteps";                // Key for getting currentSteps from savedInstanceState (")
    private final String INITIAL_STEPS_KEY = "initialSteps";                // Key for getting initialSteps from savedInstanceState (")
    private final String TIME_START_KEY = "timeStart";  // Key for getting timeStart from savedInstanceState (")
    private final String MY_INSTRUCTIONS = "A";         // Used to identify that the app should generate my instructions (I'd like to use ints but AsyncTask complicates this)
    private final String GOOGLE_INSTRUCTIONS = "B";     // Used to identify that the app should generate Google's instructions (")

    /**
     * onCreate override
     * Finds views, sets onClick listeners, restores savedInstanceState, renders map
     * When using Google Directions API, the AsyncTasks run as follows:
     *     If using "better directions": GetGoogleDirectionsTask -> GetPolylinesTask -> LatLngsToPointsTask -> PointsToDirectionsTask -> Polulate mMap and directionsTextView
     *     If Using Google's directions: GetGoogleDirectionsTask -> JSONToDirectionsTask -> Populate mMap and directionsTextView
     *     (Wait10SecondsTask is unrelated - it runs after a test has ended)
     * @param savedInstanceState instance data used to preserve the state of the app when it pauses/stops
     */
    @Override
    @SuppressWarnings({"unchecked"})
    protected void onCreate(Bundle savedInstanceState) {
        // Layout
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Set up proximity sensor and step counter
        isScreenTouchable = true;
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            stepCountSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            sensorEventListener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                        if (event.values[0] < PROXIMITY_DISTANCE & isScreenTouchable) {
                            getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                            isScreenTouchable = false;
                        } else if (!isScreenTouchable) {
                            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                            isScreenTouchable = true;
                        }
                    } else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                        currentSteps = (int) event.values[0];
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                    // Nothing for now
                }
            };
        } else {
            Log.e(TAG, "onCreate: Sensors not available!");
            finish();
        }

        // Set up variables
        isTesting = false;
        directionsCode = NO_DIRECTIONS_CODE;

        // Set up criteria
        criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setPowerRequirement(Criteria.POWER_HIGH);
        criteria.setAltitudeRequired(false);
        criteria.setSpeedRequired(false);
        criteria.setCostAllowed(true);
        criteria.setBearingRequired(false);
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);

        // Set up location
        markerHashMap = new HashMap<>(); // I know a hash map isn't the most efficient, but it is the most convenient
        speakingDistance = 0;
        setUpLocationManager();

        // Set up views
        directionsTextView = findViewById(R.id.directionsTextView);
        directionsTextView.setText(R.string.no_directions_yet);
        mapsProgressBar = findViewById(R.id.mapsProgressBar);
        getBetterDirectionsButton = findViewById(R.id.getBetterDirectionsButton);
        getGoogleDirectionsButton = findViewById(R.id.getGoogleDirectionsButton);

        // Get better directions button
        getBetterDirectionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                directionsCode = BETTER_DIRECTIONS_CODE;
                makeDirections();
            }
        });

        // Get google directions button
        getGoogleDirectionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                directionsCode = GOOGLE_DIRECTIONS_CODE;
                makeDirections();
            }
        });

        // Start text to speech engine
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.US);
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "onInit: Language not supported!");
                    } else {
                        Log.d(TAG, "onInit: Text-to-speech successfully initialized");
                    }
                } else {
                    Log.e(TAG, "onInit: Text to speech initialization failed!");
                }
            }
        });

        // Restore data from savedInstanceState
        if (savedInstanceState != null) {
            // Restore latLngs and latLngsArrayList
            ArrayList<Double> latitudes = (ArrayList<Double>) savedInstanceState.getSerializable(LATITUDES_KEY);
            ArrayList<Double> longitudes = (ArrayList<Double>) savedInstanceState.getSerializable(LONGITUDES_KEY);
            latLngs = new LinkedHashSet<>();
            if (latitudes != null && longitudes != null) {
                for (int i = 0; i < latitudes.size(); i++) {
                    latLngs.add(new LatLng(latitudes.get(i), longitudes.get(i)));
                }
            }
            latLngsArrayList = new ArrayList<>(latLngs);

            // Restore polylineArrayList
            ArrayList<Double> polylineLatitudes = (ArrayList<Double>) savedInstanceState.getSerializable(POLYLINE_LATITUDES_KEY);
            ArrayList<Double> polylineLongitudes = (ArrayList<Double>) savedInstanceState.getSerializable(POLYLINE_LONGITUDES_KEY);
            polylineArrayList = new ArrayList<>();
            if (polylineLatitudes != null && polylineLongitudes != null) {
                for (int i = 0; i < polylineLatitudes.size(); i++) {
                    polylineArrayList.add(new LatLng(polylineLatitudes.get(i), polylineLongitudes.get(i)));
                }
            }

            // Restore directions
            directions = (ArrayList<String>) savedInstanceState.getSerializable(DIRECTIONS_KEY);
            if (directions != null) {
                StringBuilder stringBuilder = new StringBuilder();
                for (String direction : directions) {
                    stringBuilder.append(direction).append("\n");
                }
                directionsTextView.setText(stringBuilder.toString());
            }

            // Restore others
//            markerHashMap = (HashMap<Integer, Marker>) savedInstanceState.getSerializable(MARKER_HASHMAP_KEY);
            isTesting = savedInstanceState.getBoolean(IS_TESTING_KEY);
            isScreenTouchable = savedInstanceState.getBoolean(IS_SCREEN_TOUCHABLE_KEY);
            directionsCode = savedInstanceState.getInt(DIRECTIONS_CODE_KEY);
            currentDirection = savedInstanceState.getInt(CURRENT_DIRECTION_KEY);
            currentPolylineStep = savedInstanceState.getInt(CURRENT_POLYLINE_STEP_KEY);
            totalDirections = savedInstanceState.getInt(TOTAL_DIRECTIONS_KEY);
            currentSteps = savedInstanceState.getInt(CURRENT_STEPS_KEY);
            initialSteps = savedInstanceState.getInt(INITIAL_STEPS_KEY);
            timeStart = savedInstanceState.getLong(TIME_START_KEY);
        }

        // Start listener for location updates (using Android framework, not Google Play services)
        // Less consistent (which is what Google likes) but more accurate (which is more important for pedestrian navigation)
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                Log.d(TAG, "onLocationChanged: Are currentDirection and currentPolylineStep the same? " + currentDirection + ", " + currentPolylineStep);
                
                // Move the marker & debug outputs
                Log.d(TAG, "onLocationChanged: " + location.getLatitude() + ", " + location.getLongitude());
//                Toast.makeText(MapsActivity.this, location.getLatitude() + ", " + location.getLongitude()+ ": " + location.getAccuracy(), Toast.LENGTH_SHORT).show();
                if (locationMarker != null) {
                    locationMarker.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
                } else {
                    locationMarker = mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(location.getLatitude(), location.getLongitude()))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))); // TODO: Make this marker look better (circle)
                }

                // Variables
                LatLng currentLatLng = latLngsArrayList.get(currentDirection);
                Location tempLocation = new Location(location); // This is just to create it, it gets immediately overwritten in the next line
                tempLocation.setLatitude(currentLatLng.latitude);
                tempLocation.setLongitude(currentLatLng.longitude);

                // Give the user verbal updates every so often
                // Currently, this happens every 3 updates, or about 6 seconds if we get a response for each request
                switch (speakingDistance) { // TODO: make this time-based or distance-based instead
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                        speakingDistance++;
                        break;
                    case 5:
                        if (location.getAccuracy() > MAX_ACCURACY_RADIUS && location.getAccuracy() > location.distanceTo(tempLocation)) {
                            textToSpeech.speak("Walk slowly, awaiting higher GPS accuracy", TextToSpeech.QUEUE_FLUSH, null);
                        } else {
                            textToSpeech.speak(Math.round(location.distanceTo(tempLocation)) + "m away.", TextToSpeech.QUEUE_ADD, null);
                        }
                        speakingDistance = 0;
                        break;
                    default:
                        textToSpeech.speak("Something bad happened.", TextToSpeech.QUEUE_FLUSH, null);
                        break;
                }

                // Next step for user
                if (latLngsArrayList.size() > currentDirection + 1) {
                    LatLng followingLatLng = latLngsArrayList.get(currentDirection + 1);
                    Location followingLocation = new Location(location);  // This is just to create it, it gets immediately overwritten in the next line
                    followingLocation.setLatitude(followingLatLng.latitude);
                    followingLocation.setLongitude(followingLatLng.longitude);

                    // If the user skips a step
                    if (location.getAccuracy() <= MAX_ACCURACY_RADIUS && location.distanceTo(followingLocation) <= MAX_DISTANCE_TO_POINT) {
                        textToSpeech.speak(directions.get(currentDirection + 1), TextToSpeech.QUEUE_FLUSH, null);
                        markerHashMap.get(currentDirection + 1).setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                        currentDirection += 2;
                        if (currentDirection == totalDirections) {
                            textToSpeech.speak("You have arrived!", TextToSpeech.QUEUE_FLUSH, null);
                            stopTesting();
                        }
                    } else if (location.getAccuracy() <= MAX_ACCURACY_RADIUS && location.distanceTo(tempLocation) <= MAX_DISTANCE_TO_POINT) {
                        // Tell the user what to do when they reach the next point
                        textToSpeech.speak(directions.get(currentDirection), TextToSpeech.QUEUE_FLUSH, null);
//                    Toast.makeText(MapsActivity.this, markerHashMap.get(currentDirection).toString(), Toast.LENGTH_SHORT).show();
                        markerHashMap.get(currentDirection).setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                        currentDirection++;
                        if (currentDirection == totalDirections) {
                            textToSpeech.speak("You have arrived!", TextToSpeech.QUEUE_FLUSH, null);
                            stopTesting();
                        }
                    }
                } else if (location.getAccuracy() <= MAX_ACCURACY_RADIUS && location.distanceTo(tempLocation) <= MAX_DISTANCE_TO_POINT) {
                    // Tell the user what to do when they reach the next point
                    textToSpeech.speak(directions.get(currentDirection), TextToSpeech.QUEUE_FLUSH, null);
//                    Toast.makeText(MapsActivity.this, markerHashMap.get(currentDirection).toString(), Toast.LENGTH_SHORT).show();
                    markerHashMap.get(currentDirection).setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
                    currentDirection++;
                    if (currentDirection == totalDirections) {
                        textToSpeech.speak("You have arrived!", TextToSpeech.QUEUE_FLUSH, null);
                        stopTesting();
                    }
                }

                // Polyline variables
                LatLng currentPolylineLatLng = polylineArrayList.get(currentPolylineStep);
                Location tempPolylineLocation = new Location(location); // This is just to create it, it gets immediately overwritten in the next line
                tempPolylineLocation.setLatitude(currentPolylineLatLng.latitude);
                tempPolylineLocation.setLongitude(currentPolylineLatLng.longitude);

                // Next polyline step for user
                if (polylineArrayList.size() > currentPolylineStep + 1) {
                    LatLng followingLatLng = polylineArrayList.get(currentPolylineStep + 1);
                    Location followingLocation = new Location(location);  // This is just to create it, it gets immediately overwritten in the next line
                    followingLocation.setLatitude(followingLatLng.latitude);
                    followingLocation.setLongitude(followingLatLng.longitude);

                    // If the user skips a polyline step
                    if (location.getAccuracy() <= MAX_ACCURACY_RADIUS && location.distanceTo(followingLocation) <= MAX_DISTANCE_TO_POINT) {
                        currentPolylineStep += 2;

                        // These three are commented out because they cause the app to try to finish twice
                        // Finishing involves an AsyncTask, so it starts one while the other is already running from when it was started in the checks above
//                        if (currentPolylineStep == polylineArrayList.size()) {
//                            textToSpeech.speak("You have arrived!", TextToSpeech.QUEUE_FLUSH, null);
//                            stopTesting();
//                        }

                    } else if (location.getAccuracy() <= MAX_ACCURACY_RADIUS && location.distanceTo(tempPolylineLocation) <= MAX_DISTANCE_TO_POINT) {
                        // When the user reaches the next polyline step
                        currentPolylineStep++;

//                        if (currentDirection == totalDirections) {
//                            textToSpeech.speak("You have arrived!", TextToSpeech.QUEUE_FLUSH, null);
//                            stopTesting();
//                        }

                    }
                } else if (location.getAccuracy() <= MAX_ACCURACY_RADIUS && location.distanceTo(tempPolylineLocation) <= MAX_DISTANCE_TO_POINT) {
                    // When the user reaches the next polyline step
                    currentPolylineStep++;

//                    if (currentDirection == totalDirections) {
//                        textToSpeech.speak("You have arrived!", TextToSpeech.QUEUE_FLUSH, null);
//                        stopTesting();
//                    }

                }

                // Check if the user has deviated
                if (polylineArrayList.size() > 0 && !currentPolylineLatLng.equals(polylineArrayList.get(0))) {
                    double distanceToPath = distancePointToLine(
                            latLngToPoint(new LatLng(location.getLatitude(), location.getLongitude())), // Current location = A
                            latLngToPoint(polylineArrayList.get(currentPolylineStep)),                  // Next point = B
                            latLngToPoint(polylineArrayList.get(currentPolylineStep - 1)));             // Previous point = C

                    // Set the next point as yellow so I can see where the distance formula is looking
                    mMap.addMarker(new MarkerOptions()
                            .position(polylineArrayList.get(currentPolylineStep))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
                    if (speakingDistance % 3 == 0) { // Only show the toast every so often, otherwise they get backed up
                        // Key for toast: Dn = Distance to next point, A = Accuracy, Dd
//                        Toast.makeText(MapsActivity.this, "Dn: " + Math.round(location.distanceTo(tempLocation)) + ", A: " + location.getAccuracy() + ", Dd: " + distanceToPath, Toast.LENGTH_SHORT).show();
//                        Toast.makeText(MapsActivity.this, distanceToPath + "",Toast.LENGTH_SHORT).show();
                    }
                    if (location.getAccuracy() <= MAX_ACCURACY_RADIUS && distanceToPath >= DEVIATION_DISTANCE) {
                        textToSpeech.speak("Recalculating.", TextToSpeech.QUEUE_FLUSH, null);
//                        Toast.makeText(MapsActivity.this, "Recalculating.", Toast.LENGTH_LONG).show();
                        currentDirection = 0;
                        currentPolylineStep = 0;
                        makeDirections(new LatLng(location.getLatitude(), location.getLongitude()));
                    }
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                // Unused
            }

            @Override
            public void onProviderEnabled(String provider) {
                // Unused
            }

            @Override
            public void onProviderDisabled(String provider) {
                Toast.makeText(MapsActivity.this, R.string.no_gps, Toast.LENGTH_LONG).show();
                Log.e(TAG, "onProviderDisabled: " + getString(R.string.no_gps));
                finish();
            }
        };

        // Set up map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

//        Toast.makeText(MapsActivity.this, "Distance in world coords: " + distancePointToLine(latLngToPoint(new LatLng(37.71749497024496, -97.29166937196953)), latLngToPoint(new LatLng(37.71749857718869,-97.29177746504195)), latLngToPoint(new LatLng(37.71749555641508,-97.2921318297104))), Toast.LENGTH_SHORT).show();
    }

    /**
     * Called when the instance needs to be saved
     * Packs important variables into outState so they can be restored next time onCreate is run
     * @param outState important values that must be preserved across activity loads/unloads
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Put latLngs (don't care about latLngsArrayList because it's mirrored data)
        if (latLngs != null) {
            ArrayList<Double> latitudes = new ArrayList<>();
            ArrayList<Double> longitudes = new ArrayList<>();
            for (LatLng latlng : latLngs) {
                latitudes.add(latlng.latitude);
                longitudes.add(latlng.longitude);
            }
            outState.putSerializable(LATITUDES_KEY, latitudes);
            outState.putSerializable(LONGITUDES_KEY, longitudes);
        }

        // Put polylineArrayList
        if (polylineArrayList != null) {
            ArrayList<Double> polylineLatitudes = new ArrayList<>();
            ArrayList<Double> polylineLongitudes = new ArrayList<>();
            for (LatLng latLng : polylineArrayList) {
                polylineLatitudes.add(latLng.latitude);
                polylineLongitudes.add(latLng.longitude);
            }
            outState.putSerializable(POLYLINE_LATITUDES_KEY, polylineLatitudes);
            outState.putSerializable(POLYLINE_LONGITUDES_KEY, polylineLongitudes);
        }

        // Put others
        outState.putSerializable(DIRECTIONS_KEY, directions);
//        outState.putSerializable(MARKER_HASHMAP_KEY, markerHashMap); // TODO: Marker is not Serializable, has to be Parceled
        outState.putBoolean(IS_TESTING_KEY, isTesting);
        outState.putBoolean(IS_SCREEN_TOUCHABLE_KEY, isScreenTouchable);
        outState.putInt(DIRECTIONS_CODE_KEY, directionsCode);
        outState.putInt(CURRENT_DIRECTION_KEY, currentDirection);
        outState.putInt(CURRENT_POLYLINE_STEP_KEY, currentPolylineStep);
        outState.putInt(TOTAL_DIRECTIONS_KEY, totalDirections);
        outState.putInt(CURRENT_STEPS_KEY, currentSteps);
        outState.putInt(INITIAL_STEPS_KEY, initialSteps);
        outState.putLong(TIME_START_KEY, timeStart);
        super.onSaveInstanceState(outState);
    }

    /**
     * onBackPressed override
     * Stops leaks when the back button is pressed
     */
    @Override
    public void onBackPressed() {
        if (wait10SecondsTask != null) {
            wait10SecondsTask.cancel(true);
        }
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        super.onBackPressed();
    }

    /**
     * Create test button in Menubar
     * @param menu menubar for MapsActivity
     * @return whether or not the options menu was created successfully
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_test, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Called when a Menubar item is selected
     * @param item which item was selected
     * @return whether or not the event was handled successfully
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int menuId = item.getItemId();
        switch (menuId) {
            case R.id.toggleTestItem:
                if (isTesting) {
                    stopTesting();
                } else {
                    startTesting();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Starts location updates (based on criteria)
     */
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(locationManager.getBestProvider(criteria, true), UPDATE_INTERVAL, 0, locationListener);
        Toast.makeText(MapsActivity.this, "starting location", Toast.LENGTH_SHORT).show();
    }

    /**
     * Stops location updates
     */
    private void stopLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.removeUpdates(locationListener);
        Toast.makeText(MapsActivity.this, "stopping location", Toast.LENGTH_SHORT).show();
    }

    /**
     * onDestroy override
     * Stops leaks
     */
    @Override
    protected void onDestroy() {
        if (isTesting) {
            stopLocationUpdates();
        }

        if (wait10SecondsTask != null) {
            wait10SecondsTask.cancel(true);
            progressDialog.dismiss();
        }

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        sensorManager.unregisterListener(sensorEventListener);
        super.onDestroy();
    }

    /**
     * onPause override
     * Stops leaks
     */
    @Override
    protected void onPause() {
        if (isTesting) {
            stopLocationUpdates();
        }

        if (wait10SecondsTask != null) {
            wait10SecondsTask.cancel(true);
            progressDialog.dismiss();
        }

        if (textToSpeech != null) { // Do I need to restart this? Who knows, but it probably isn't important for testing
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        sensorManager.unregisterListener(sensorEventListener);
        super.onPause();
    }

    /**
     * onResume override
     * Restarts location updates, text-to-speech, and the proximity sensor
     */
    @Override
    protected void onResume() {
        if (isTesting) {
            startLocationUpdates();
        }

        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.US);
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(TAG, "onInit: Language not supported!");
                    } else {
                        // success
//                        textToSpeech.speak("Hello world", TextToSpeech.QUEUE_FLUSH, null);
                    }
                } else {
                    Log.e(TAG, "onInit: Text to speech initialization failed!");
                }
            }
        });

        sensorManager.registerListener(sensorEventListener, proximitySensor, Sensor.TYPE_PROXIMITY, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(sensorEventListener, stepCountSensor, Sensor.TYPE_STEP_COUNTER, SensorManager.SENSOR_DELAY_NORMAL);
        super.onResume();
    }

    /**
     * Manipulates the map once available
     * Runs when the map is ready to use
     * @param googleMap Map View
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        moveMapToLocation();
    }

    /**
     * Gets the user's last known location and move the map's camera to that location
     */
    private void moveMapToLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // If we're here, we don't have permission to location
            // So request it

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // If Nougat or above
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MAP_REQUEST_CODE);
            } else {                                              // All previous versions
                Log.e(TAG, "moveMapToLocation: No permissions! Perhaps location is off in the phone settings.");
                finish();
            }
        } else {
            // If we're here, we already have permission
            // So get their location
            Location location = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, true));
            Log.d(TAG, "moveMapToLocation: " + location);

            // location can be null (no last location)
            if (location != null) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                Log.d(TAG, "moveMapToLocation: " + latLng);

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18.0f)); // The camera will not move if there was no previous location
                locationMarker = mMap.addMarker(new MarkerOptions()
                        .position(new LatLng(location.getLatitude(), location.getLongitude()))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))); // TODO: Make this marker look better (circle)
            }
        }
    }

    /**
     * Sets up locationManager (as long as there is permission)
     * Gets it from system services, sets it to use GPS only
     * If GPS is not available, it ends the app
     */
    private void setUpLocationManager() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // If we're here, we don't have permission to location
            // So request it

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // If Nougat or above
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
            } else {                                              // All previous versions
                Log.e(TAG, "setUpLocationManager: No permissions! Perhaps location is off in the phone settings.");
                finish();
            }
        } else {
            // If we're here, we already have permissions
            // So set up location
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager == null || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(MapsActivity.this, R.string.no_gps, Toast.LENGTH_LONG).show();
                Log.e(TAG, "onCreate: " + getString(R.string.no_gps));
                finish();
            }
            Log.d(TAG, "setUpLocationManager: " + locationManager);
        }
    }

    /**
     * Puts directions on the map when the user requests them
     * The directions produced are either mine or Google's, depending on directionsCode
     * This version of the overloaded method is for when the user is starting from the hard-coded starting point
     * // TODO: Simplify this by merging directionsCode and the codes passed into getGoogleDirectionsTask
     */
    private void makeDirections() {
        // Reset map
        mMap.clear();
        markerHashMap.clear();
        onMapReady(mMap);

        // Hide TextView - this will be replaced by a ProgressBar in the AsyncTask
        directionsTextView.setVisibility(View.GONE);

        // Get instructions and parse them
        GetGoogleDirectionsTask getGoogleDirectionsTask = new GetGoogleDirectionsTask(MapsActivity.this);

        if (directionsCode == BETTER_DIRECTIONS_CODE) {
            // Initial test: Shocker hall -> Beggs Hall (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=37.71978257362339,-97.29415262999709&destination=37.717503992464884,-97.2916656601883&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Initial test backwards: Beggs Hall -> Shocker Hall (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=37.717503992464884,-97.2916656601883&destination=37.71978257362339,-97.29415262999709&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Official test: Shocker hall -> Construction before Beggs Hall (Wichita State University campus)
            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=37.71795169552335,-97.29243862874102&destination=37.71978257362339,-97.29415262999709&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Very short test: Beggs hall -> Half a block south down the road in front (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=37.717503992464884,-97.2916656601883&destination=37.717074406067624,-97.29155678865129&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Second test: Seattle Great Wheel -> Pink Gorilla International District (Downtown Seattle)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=seattle+great+wheel+1301+alaskan+way+seattle+wa+98101&destination=pink+gorilla+international+district+601+s+king+st+101c+seattle+wa+98104&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Southern Hemisphere test: Queensland Conservatorium -> Nando's (Brisbane, Australia)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=queensland+conservatorium+140+grey+st+south+brisbane+qld+4101+australia&destination=nandos+153+stanley+st+south+brisbane+qld+4101+australia&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Extreme latitude test: Coal Miners' Cabins -> Kroa (Longyearbyen, Svalbard)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=coal+miners+cabins+nybyen+longyearbyen+svalbard+and+jan+mayen&destination=restaurant+kroa+steakers+svalbard+as+postboks+150+9171+longyearbyen+svalbard+and+jan+mayen&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Very long test: Lisbon, Portugal -> Talon, Russia
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=lisbon+portugal&destination=talon+magadan+russia&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Impossible test: Manyberries, AB -> Port au Francais, French Southern/Antarctic Lands
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=manyberries+ab&destination=port+aux+francais+french+southern+and+antarctic+lands&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
        } else if (directionsCode == GOOGLE_DIRECTIONS_CODE) {
            // Initial test: Shocker hall -> Beggs Hall (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=37.71978257362339,-97.29415262999709&destination=37.717503992464884,-97.2916656601883&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Initial test backwards: Beggs Hall -> Shocker Hall (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=37.717503992464884,-97.2916656601883&destination=37.71978257362339,-97.29415262999709&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Official test: Shocker hall -> Construction before Beggs Hall (Wichita State University campus)
            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=37.71795169552335,-97.29243862874102&destination=37.71978257362339,-97.29415262999709&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Very short test: Beggs hall -> Half a block south down the road in front (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=37.717503992464884,-97.2916656601883&destination=37.717074406067624,-97.29155678865129&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Second test: Seattle Great Wheel -> Pink Gorilla International District (Downtown Seattle)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=seattle+great+wheel+1301+alaskan+way+seattle+wa+98101&destination=pink+gorilla+international+district+601+s+king+st+101c+seattle+wa+98104&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Southern Hemisphere test: Queensland Conservatorium -> Nando's (Brisbane, Australia)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=queensland+conservatorium+140+grey+st+south+brisbane+qld+4101+australia&destination=nandos+153+stanley+st+south+brisbane+qld+4101+australia&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Extreme latitude test: Coal Miners' Cabins -> Kroa (Longyearbyen, Svalbard)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=coal+miners+cabins+nybyen+longyearbyen+svalbard+and+jan+mayen&destination=restaurant+kroa+steakers+svalbard+as+postboks+150+9171+longyearbyen+svalbard+and+jan+mayen&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Very long test: Lisbon, Portugal -> Talon, Russia
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=lisbon+portugal&destination=talon+magadan+russia&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Impossible test: Manyberries, AB -> Port au Francais, French Southern/Antarctic Lands
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=manyberries+ab&destination=port+aux+francais+french+southern+and+antarctic+lands&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
        }

    }

    /**
     * Puts directions on the map when the user requests them
     * The directions produced are either mine or Google's, depending on directionsCode
     * This version of the overloaded method is for when the user is starting from their current location (when recalculating)
     * // TODO: Simplify this by merging directionsCode and the codes passed into getGoogleDirectionsTask
     */
    private void makeDirections(LatLng currentLocation) {
        // Reset map
        mMap.clear();
        markerHashMap.clear();
        onMapReady(mMap);

        // Hide TextView - this will be replaced by a ProgressBar in the AsyncTask
        directionsTextView.setVisibility(View.GONE);

        // Get instructions and parse them
        GetGoogleDirectionsTask getGoogleDirectionsTask = new GetGoogleDirectionsTask(MapsActivity.this);

        if (directionsCode == BETTER_DIRECTIONS_CODE) {
            // Initial test: Shocker hall -> Beggs Hall (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=37.717503992464884,-97.2916656601883&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Initial test backwards: Beggs Hall -> Shocker Hall (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=37.71978257362339,-97.29415262999709&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Official test: Shocker hall -> Construction before Beggs Hall (Wichita State University campus)
            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=37.71978257362339,-97.29415262999709&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Very short test: Beggs hall -> Half a block south down the road in front (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=37.717074406067624,-97.29155678865129&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Second test: Seattle Great Wheel -> Pink Gorilla International District (Downtown Seattle)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=pink+gorilla+international+district+601+s+king+st+101c+seattle+wa+98104&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Southern Hemisphere test: Queensland Conservatorium -> Nando's (Brisbane, Australia)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=nandos+153+stanley+st+south+brisbane+qld+4101+australia&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Extreme latitude test: Coal Miners' Cabins -> Kroa (Longyearbyen, Svalbard)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=restaurant+kroa+steakers+svalbard+as+postboks+150+9171+longyearbyen+svalbard+and+jan+mayen&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Very long test: Lisbon, Portugal -> Talon, Russia
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=talon+magadan+russia&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
            // Impossible test: Manyberries, AB -> Port au Francais, French Southern/Antarctic Lands
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=port+aux+francais+french+southern+and+antarctic+lands&key=" + getString(R.string.google_maps_key) + "&mode=walking", MY_INSTRUCTIONS);
        } else if (directionsCode == GOOGLE_DIRECTIONS_CODE) {
            // Initial test: Shocker hall -> Beggs Hall (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=37.717503992464884,-97.2916656601883&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Initial test backwards: Beggs Hall -> Shocker Hall (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=37.71978257362339,-97.29415262999709&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Official test: Shocker hall -> Construction before Beggs Hall (Wichita State University campus)
            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=37.71978257362339,-97.29415262999709&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Very short test: Beggs hall -> Half a block south down the road in front (Wichita State University campus)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=37.717074406067624,-97.29155678865129&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Second test: Seattle Great Wheel -> Pink Gorilla International District (Downtown Seattle)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=pink+gorilla+international+district+601+s+king+st+101c+seattle+wa+98104&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Southern Hemisphere test: Queensland Conservatorium -> Nando's (Brisbane, Australia)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=nandos+153+stanley+st+south+brisbane+qld+4101+australia&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Extreme latitude test: Coal Miners' Cabins -> Kroa (Longyearbyen, Svalbard)
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=restaurant+kroa+steakers+svalbard+as+postboks+150+9171+longyearbyen+svalbard+and+jan+mayen&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Very long test: Lisbon, Portugal -> Talon, Russia
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=talon+magadan+russia&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
            // Impossible test: Manyberries, AB -> Port au Francais, French Southern/Antarctic Lands
//            getGoogleDirectionsTask.execute("https://maps.googleapis.com/maps/api/directions/json?origin=" + currentLocation.latitude + "," + currentLocation.longitude + "&destination=port+aux+francais+french+southern+and+antarctic+lands&key=" + getString(R.string.google_maps_key) + "&mode=walking", GOOGLE_INSTRUCTIONS);
        }
    }

    /**
     * When the user responds to a permission request
     * @param requestCode code identifying which request this answers
     * @param permissions which permissions were granted (or denied)
     * @param grantResults whether or not the permissions were granted or denied
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MAP_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, so try map again
                moveMapToLocation();
            }
        } else if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, so try locationManager setup again
                setUpLocationManager();
            }
        } else {
            // Permission denied, warn the user and ask again
            Toast.makeText(MapsActivity.this, R.string.need_location_perms, Toast.LENGTH_LONG).show();
            moveMapToLocation();
        }
    }

    /**
     * Called when a test begins - keeps track of steps walked and time elapsed
     * And begins the test procedure
     */
    private void startTesting() {
        // Start GPS loop
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        currentDirection = 0;
        currentPolylineStep = 0;
        startLocationUpdates();

        // Start timer
        timeStart = SystemClock.elapsedRealtime();

        // Start step counter
        initialSteps = currentSteps;

        // Change state to testing
        isTesting = true;
    }

    /**
     * Called when a test is finished, shows the user a report on how they did
     */
    private void stopTesting() {

        // Stop GPS loop
        stopLocationUpdates();

        // Stop the user for 10 seconds while the step counter catches up
        // (The step counter is more reliable than step detector but can have up to 10sec of latency)
        progressDialog = new ProgressDialog(MapsActivity.this);
        progressDialog.setMax(100);
        progressDialog.setMessage(MapsActivity.this.getString(R.string.wait_for_steps_message));
        progressDialog.setTitle(MapsActivity.this.getString(R.string.wait_for_steps_title));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.show();

        // Stop timer/step counter,  create alert showing results, and change state to not testing
        wait10SecondsTask = new Wait10SecondsTask(MapsActivity.this);
        wait10SecondsTask.execute();
    }

    /**
     * Gets JSON directions from Google's Directions API
     * Pass directions URL as a parameter
     * Returns JSON directions response as a String
     */
    private static class GetGoogleDirectionsTask extends AsyncTask<String, Void, String> {
        private boolean useMyInstructions;
        private String jsonResponse;

        // Using a weak reference so I can still access elements from the UI thread
        // without risking memory leaks (which would happen with a normal reference)
        private WeakReference<MapsActivity> weakReference;
        GetGoogleDirectionsTask(MapsActivity context) {
            weakReference = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            MapsActivity mapsActivity = weakReference.get();
            if (mapsActivity != null && !mapsActivity.isFinishing()) {
                mapsActivity.mapsProgressBar.setVisibility(View.VISIBLE);
                jsonResponse = "";
            }
        }

        @Override
        protected String doInBackground(String... strings) {
            MapsActivity mapsActivity = weakReference.get();
            if (mapsActivity != null && !mapsActivity.isFinishing()) {
                if (strings[1].equals(mapsActivity.MY_INSTRUCTIONS)) {
                    useMyInstructions = true;
                } else if (strings[1].equals(mapsActivity.GOOGLE_INSTRUCTIONS)) {
                    useMyInstructions = false;
                }
            } else {
                Log.e(TAG, "doInBackground: Could not get MapsActivity");
                this.cancel(true);
            }

            try {
                URL url = new URL(strings[0]);
                HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    bufferedReader.close();
                    return stringBuilder.toString();
                } finally {
                    // It's just good practice to put cleanup code in a finally, even if no exceptions are expected
                    urlConnection.disconnect();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                e.printStackTrace();
                return COULD_NOT_CONNECT;
            }
        }

        @Override
        protected void onPostExecute(String response) {
            MapsActivity mapsActivity = weakReference.get();
            if (mapsActivity != null && !mapsActivity.isFinishing()) {
                // Detect if something bad happened
                if (response == null) {
                    Log.e(TAG, "onPostExecute: Something bad happened!");
                    mapsActivity.mapsProgressBar.setVisibility(View.GONE);
                    mapsActivity.directionsTextView.setVisibility(View.VISIBLE);
                    mapsActivity.directionsTextView.setText(mapsActivity.getString(R.string.error_mystery));
                } else if (response.equals(COULD_NOT_CONNECT)) {
                    if (mapsActivity.isTesting) {
                        onPostExecute(response); // Recursively retry if the user has a failed recalculating attempt
                    } else {
                        Log.e(TAG, "onPostExecute: " + COULD_NOT_CONNECT);
                        mapsActivity.textToSpeech.speak("Connection error, manually reset directions.", TextToSpeech.QUEUE_FLUSH, null);
                        mapsActivity.mapsProgressBar.setVisibility(View.GONE);
                        mapsActivity.directionsTextView.setVisibility(View.VISIBLE);
                        mapsActivity.directionsTextView.setText(mapsActivity.getString(R.string.error_connection));
                    }
                } else {
                    jsonResponse = response;

                    if (useMyInstructions) {
                        GetPolylinesTask getPolylinesTask = new GetPolylinesTask(mapsActivity);
                        getPolylinesTask.execute(jsonResponse);
                    } else {
                        JSONToDirectionsTask jsonToDirectionsTask = new JSONToDirectionsTask(mapsActivity);
                        jsonToDirectionsTask.execute(jsonResponse);

                        GetPolylinesTask getPolylinesTask = new GetPolylinesTask(mapsActivity); // This just runs to set polylinesArrayList
                        getPolylinesTask.execute(jsonResponse);
                    }
                }
            }
        }
    }

    /**
     * Takes Google's JSON directions and puts all the polyline sections into one big ArrayList
     * which is sent back to MapsActivity
     * LinkedHashSet is used to maintain order and remove duplicates
     * Takes JSON directions String as a parameter
     * Returns an ArrayList of polylines, each representing a segment of the route
     */
    private static class GetPolylinesTask extends AsyncTask<String, Void, LinkedHashSet<LatLng>> {
        // Using a weak reference so I can still access elements from the UI thread
        // without risking memory leaks (which would happen with a normal reference)
        private WeakReference<MapsActivity> weakReference;
        GetPolylinesTask(MapsActivity context) {
            weakReference = new WeakReference<>(context);
        }

        @Override
        protected LinkedHashSet<LatLng> doInBackground(String... strings) {
            ArrayList<String> polylines = new ArrayList<>();
            LinkedHashSet<LatLng> latLngs = new LinkedHashSet<>();
            try {
                JSONObject jsonObject = new JSONObject(strings[0]);

                // Get routes array
                // Currently, this only gets the first possible route and leg
                // So, if you want to use your own, just pass your polylines into the next step
                JSONArray routes = jsonObject.getJSONArray(ROUTES);

                // Get legs array
                JSONObject temp = routes.getJSONObject(0);
                JSONArray legs = temp.getJSONArray(LEGS);

                // Get steps array
                temp = legs.getJSONObject(0);
                JSONArray steps = temp.getJSONArray(STEPS);

                // Get the polyline from each step in steps
                JSONObject step, polyline;
                for (int i = 0; i < steps.length(); i++) {
                    step = steps.getJSONObject(i);
                    polyline = step.getJSONObject(POLYLINE);
                    polylines.add(polyline.getString(POINTS));
                }
                Log.d(TAG, "doInBackground: polylines: " + polylines);

                List<LatLng> tempLatLngs;
                for (int i = 0; i < polylines.size(); i++) {
                    tempLatLngs = PolyUtil.decode(polylines.get(i));
                    latLngs.addAll(tempLatLngs);
                }
                Log.d(TAG, "doInBackground: latLngs: " + latLngs);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return latLngs;
        }

        @Override @SuppressWarnings({"unchecked"})
        protected void onPostExecute(LinkedHashSet<LatLng> latLngs) {
            MapsActivity mapsActivity = weakReference.get();
            if (mapsActivity != null && !mapsActivity.isFinishing()) {
                if (mapsActivity.directionsCode == mapsActivity.BETTER_DIRECTIONS_CODE) {
                    mapsActivity.latLngs = latLngs;
                    mapsActivity.latLngsArrayList = new ArrayList<>(latLngs);

                    LatLngsToPointsTask latLngsToPointsTask = new LatLngsToPointsTask(mapsActivity);
                    latLngsToPointsTask.execute(latLngs);
                }
                mapsActivity.polylineArrayList = new ArrayList<>(latLngs);
            }
        }
    }

    /**
     * Converts Google's Directions API JSON response directly into text instructions
     * Takes JSON directions String as a parameter
     * Returns an ArrayList of instructions
     */
    private static class JSONToDirectionsTask extends AsyncTask<String, Void, ArrayList<String>> {
        ArrayList<LatLng> latLngs;

        // Using a weak reference so I can still access elements from the UI thread
        // without risking memory leaks (which would happen with a normal reference)
        private WeakReference<MapsActivity> weakReference;
        JSONToDirectionsTask(MapsActivity context) {
            weakReference = new WeakReference<>(context);
        }

        @Override
        protected ArrayList<String> doInBackground(String... strings) {
            latLngs = new ArrayList<>();
            ArrayList<String> directions = new ArrayList<>();
            try {
                JSONObject jsonObject = new JSONObject(strings[0]);

                // Get routes array
                // Currently, this only gets the first possible route and leg
                // So, if you want to use your own, just pass your polylines into the next step
                JSONArray routes = jsonObject.getJSONArray(ROUTES);

                // Get legs array
                JSONObject temp = routes.getJSONObject(0);
                JSONArray legs = temp.getJSONArray(LEGS);

                // Get steps array
                temp = legs.getJSONObject(0);
                JSONArray steps = temp.getJSONArray(STEPS);

                // Get the point and instruction from each step in steps
                JSONObject step, endLocation;
                step = steps.getJSONObject(0);
                latLngs.add(new LatLng(step.getJSONObject(START_LOCATION).getDouble(LAT), step.getJSONObject(START_LOCATION).getDouble(LONG))); // First point
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // If Nougat or above
                    for (int i = 0; i < steps.length(); i++) {
                        step = steps.getJSONObject(i);
                        directions.add(Html.fromHtml(step.getString(HTML_INSTRUCTIONS), Html.FROM_HTML_MODE_COMPACT).toString());
                        endLocation = step.getJSONObject(END_LOCATION);
                        latLngs.add(new LatLng(endLocation.getDouble(LAT), endLocation.getDouble(LONG)));
                    }
                } else {
                    for (int i = 0; i < steps.length(); i++) {        // All previous versions
                        step = steps.getJSONObject(i);
                        directions.add(Html.fromHtml(step.getString(HTML_INSTRUCTIONS)).toString());
                        endLocation = step.getJSONObject(END_LOCATION);
                        latLngs.add(new LatLng(endLocation.getDouble(LAT), endLocation.getDouble(LONG)));
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return directions;
        }

        @Override
        protected void onPostExecute(ArrayList<String> strings) {
            MapsActivity mapsActivity = weakReference.get();
            if (mapsActivity != null && !mapsActivity.isFinishing()) {
                mapsActivity.directions = strings;
                mapsActivity.latLngs = new LinkedHashSet<>(latLngs);
                mapsActivity.latLngsArrayList = latLngs;

                if (strings != null) { // TODO: Put this in its own method
                    // Put Directions into instructionsTextView and Logcat
                    StringBuilder stringBuilder = new StringBuilder();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // If Nougat or above
                        for (String instruction : strings) {
                            stringBuilder.append(Html.fromHtml(instruction, Html.FROM_HTML_MODE_COMPACT)).append("\n");
                            Log.d(TAG, "onPostExecute: " + Html.fromHtml(instruction, Html.FROM_HTML_MODE_COMPACT));
                        }
                    } else {                                              // All previous versions
                        for (String instruction : strings) {
                            stringBuilder.append(Html.fromHtml(instruction)).append("\n");
                        }
                    }
                    mapsActivity.directionsTextView.setText(stringBuilder.toString());

                    mapsActivity.putMarkers();
                } else {
                    mapsActivity.directionsTextView.setText(R.string.error_mystery);
                }
                mapsActivity.mapsProgressBar.setVisibility(View.GONE);
                mapsActivity.directionsTextView.setVisibility(View.VISIBLE);
                mapsActivity.totalDirections = mapsActivity.directions.size();
            }
        }
    }

    /**
     * Turns a set of LatLngs into a set of Points
     * Takes a LinkedHashMap of LatLngs
     * Returns a LinkedHashMap of Points
     */
    private static class LatLngsToPointsTask extends AsyncTask<LinkedHashSet<LatLng>, Void, LinkedHashSet<Point>> {
        // Using a weak reference so I can still access elements from the UI thread
        // without risking memory leaks (which would happen with a normal reference)
        private WeakReference<MapsActivity> weakReference;
        LatLngsToPointsTask(MapsActivity context) {
            weakReference = new WeakReference<>(context);
        }

        @Override @SuppressWarnings({"unchecked"})
        protected LinkedHashSet<Point> doInBackground(LinkedHashSet<LatLng>... linkedHashSets) {
            LinkedHashSet<Point> points = new LinkedHashSet<>();

            for (LatLng latLng : linkedHashSets[0]) {
                points.add(latLngToPoint(latLng));
            }
            Log.d(TAG, "onPostExecute: Points: " + points.toString());
            return points;
        }

        @Override @SuppressWarnings({"unchecked"})
        protected void onPostExecute(LinkedHashSet<Point> points) {
            MapsActivity mapsActivity = weakReference.get();
            if (mapsActivity != null && !mapsActivity.isFinishing()) {
                PointsToDirectionsTask pointsToDirectionsTask = new PointsToDirectionsTask(mapsActivity);
                pointsToDirectionsTask.execute(points, mapsActivity.latLngs);
            }
        }
    }

    /**
     * Turns a set of Points into user-friendly instructions called SimpleDirections
     * These consist of a clock direction relative to the user and a distance to the next point
     * Takes two LinkedHashSets, one of Points and one of LatLngs
     * Returns an ArrayList of SimpleDirections
     */
    private static class PointsToDirectionsTask extends AsyncTask<LinkedHashSet, Void, ArrayList<SimpleDirection>> {
        // Using a weak reference so I can still access elements from the UI thread
        // without risking memory leaks (which would happen with a normal reference)
        private WeakReference<MapsActivity> weakReference;
        PointsToDirectionsTask(MapsActivity context) {
            weakReference = new WeakReference<>(context);
        }

        @Override @SuppressWarnings({"unchecked"})
        protected ArrayList<SimpleDirection> doInBackground(LinkedHashSet... linkedHashSets) {
            // Variables
            ArrayList<SimpleDirection> directions = new ArrayList<>(); // Will be filled used to give the user directions
            ArrayList<Integer> clockAngles = new ArrayList<>(); // Use to construct SimpleDirection
            ArrayList<Float> distances = new ArrayList<>(); // Use to construct SimpleDirection
            ArrayList<Point> tempPoints = new ArrayList<>(linkedHashSets[0]); // So I can access by index
            ArrayList<LatLng> tempLatLngs = new ArrayList<>(linkedHashSets[1]); // So I can access by index
            double tempAngle;
            float[] tempDistance = {0}; // This is a float[] because that's what Location.distanceBetween() wants

            if (tempPoints.size() > 0 && tempLatLngs.size() > 0) {
                // First instruction
                // (Cardinal direction for first instruction is up to Ali so it's a 0 o'clock for now)
                clockAngles.add(0);
                Location.distanceBetween(
                        tempLatLngs.get(0).latitude,
                        tempLatLngs.get(0).longitude,
                        tempLatLngs.get(1).latitude,
                        tempLatLngs.get(1).longitude,
                        tempDistance
                );
                distances.add(tempDistance[0]);

                // All following instructions
                for (int i = 1; i < tempPoints.size() - 1; i++) { // Don't iterate over endpoints
                    // Not using a Location bearingTo() method because this is simpler - we only need approximations anyways
                    tempAngle = calculateAngle(tempPoints.get(i - 1), tempPoints.get(i), tempPoints.get(i + 1));
                    clockAngles.add(angleToClock(tempAngle));

                    Location.distanceBetween(
                            tempLatLngs.get(i).latitude,
                            tempLatLngs.get(i).longitude,
                            tempLatLngs.get(i + 1).latitude,
                            tempLatLngs.get(i + 1).longitude,
                            tempDistance
                    );
                    distances.add(tempDistance[0]);
                }

                // Assemble instructions into ArrayList
                for (int i = 0; i < clockAngles.size(); i++) {
                    directions.add(new SimpleDirection(clockAngles.get(i), distances.get(i)));
                }
                return directions;
            } else {
                return null;
            }
        }

        @Override
        protected void onPostExecute(ArrayList<SimpleDirection> simpleDirections) {
            MapsActivity mapsActivity = weakReference.get();
            if (mapsActivity != null && !mapsActivity.isFinishing()) {
                if (simpleDirections != null) {
                    // Put SimpleDirections into instructionsTextView and Logcat
                    StringBuilder stringBuilder = new StringBuilder();
                    String tempString;
                    mapsActivity.directions = new ArrayList<>();
                    for (SimpleDirection simpleDirection : simpleDirections) {
                        tempString = mapsActivity.getString(
                                R.string.direction_instruction,
                                simpleDirection.getClockAngle(),
                                Math.round(simpleDirection.getDistance())
                        );
                        stringBuilder.append(tempString).append("\n");
                        mapsActivity.directions.add(tempString);
                    }
                    mapsActivity.directionsTextView.setText(stringBuilder.toString());
                    Log.d(TAG, "onPostExecute: Directions: " + simpleDirections.toString());

                    mapsActivity.putMarkers();
                } else {
                    mapsActivity.directionsTextView.setText(R.string.error_mystery);
                }
                mapsActivity.mapsProgressBar.setVisibility(View.GONE);
                mapsActivity.directionsTextView.setVisibility(View.VISIBLE);
                mapsActivity.totalDirections = mapsActivity.directions.size();
            }
        }
    }

    /**
     * Waits for 10 seconds - used for letting the step counter catch up after a test
     */
    private static class Wait10SecondsTask extends AsyncTask<Void, Integer, Void> {
        // Using a weak reference so I can still access elements from the UI thread
        // without risking memory leaks (which would happen with a normal reference)
        private WeakReference<MapsActivity> weakReference;
        Wait10SecondsTask(MapsActivity context) {
            weakReference = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (int i = 0; i < 100; i++) {
                if (!isCancelled()) {
                    publishProgress(i);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    MapsActivity mapsActivity = weakReference.get();
                    mapsActivity.textToSpeech.stop();
                    mapsActivity.textToSpeech.shutdown();
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            MapsActivity mapsActivity = weakReference.get();
            mapsActivity.progressDialog.setProgress(values[0]);
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            MapsActivity mapsActivity = weakReference.get();
            mapsActivity.progressDialog.dismiss();
            if (!isCancelled()) {
                int totalSteps = mapsActivity.currentSteps - mapsActivity.initialSteps;
                long elapsedTime = SystemClock.elapsedRealtime() - mapsActivity.timeStart; // in milliseconds

                // Create alert showing results
                AlertDialog.Builder builder = new AlertDialog.Builder(mapsActivity);
                builder.setTitle(mapsActivity.getString(R.string.results_title))
                        .setMessage(totalSteps + " steps, " + elapsedTime + "ms, " + totalSteps * mapsActivity.STEP_LENGTH + "m")
                        .setPositiveButton(R.string.ok, null);
                AlertDialog resultsDialog = builder.create();
                resultsDialog.show();

                mapsActivity.isTesting = false;
            } else {
                mapsActivity.textToSpeech.stop(); // This prevents leaks
                mapsActivity.textToSpeech.shutdown();
                Toast.makeText(mapsActivity, "Test cancelled", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Places all points making up the path on the map
     */
    private void putMarkers() {
        if (latLngs != null && latLngs.size() > 0) {
            int i = 0;
            for (LatLng latLng : latLngs) {
                Marker tempMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(Integer.toString(i)));
                markerHashMap.put(i, tempMarker);
                i++;
            }
        }
    }

    /**
     * Turns a LatLng into a Point (rectangular coordinates) on the map
     * Credit: // https://developers.google.com/maps/documentation/javascript/examples/map-coordinates
     * @param latLng Earth coordinate to turn into a Mercator rectangular coordinate
     * @return Rectangular coordinates Point
     */
    private static Point latLngToPoint(LatLng latLng) {
        double siny = Math.sin(latLng.latitude * Math.PI / 180);
        siny = Math.min(Math.max(siny, -0.9999), 0.9999);
        return new Point(
                TILE_SIZE * (0.5 + latLng.longitude / 360),
                TILE_SIZE * (Math.log((1 + siny) / (1 - siny)) / (4 * Math.PI))
        );
    }

    /**
     * Calculates the signed angle between three points
     * Calculates angle ∠ABC for points A, B, C
     * In the context of directions, previous/current/next points are in relation to the current instruction
     * @param a Point A (in this context, the previous point)
     * @param b Point B (in this context, the current point)
     * @param c Point C (in this context, the next point)
     * @return Signed angle at point B
     */
    private static double calculateAngle(Point a, Point b, Point c) {
        Point vectorBA = new Point(a.x - b.x, a.y - b.y); // Vector pointing from current point to the previous one
        Point vectorBC = new Point(c.x - b.x, c.y - b.y); // Vector pointing from the current point to the next one

        // Set the algebraic and geometric definitions of dot product equal and solve for theta
        // I made a ton of variables because something went wrong when I did this all in one line
        double dotProduct = dotProduct(vectorBA, vectorBC);
        double normBA = Math.hypot(vectorBA.x, vectorBA.y);
        double normBC = Math.hypot(vectorBC.x, vectorBC.y);
        double temp = dotProduct / (normBA * normBC);
        double angle = Math.toDegrees(Math.acos(temp));

        // Compare cross product between vectors and straight up. If they are equal,then its a right turn. If they're opposites, it's left
        if (angle != 180 && angle != 0 && isLeftTurn(vectorBA, vectorBC)) {
                angle = -angle;
        }

        return angle;
    }

    /**
     * Calculates the algebraic dot product between two vectors centred at the origin
     * @param a First vector
     * @param b Second vector
     * @return Doct product of a and b
     */
    private static double dotProduct(Point a, Point b) {
        return (a.x * b.x) + (a.y * b.y);
    }

    /**
     * Determines whether or not a turn angle is to the left or not
     * Used for signing an angle
     * Pre: Vectors are not parallel (180° or 0°)
     * @param a First vector
     * @param b Second vector
     * @return True if the angle between the vectors should be negative, false if it should be positive
     */
    private static boolean isLeftTurn(Point a, Point b) {
        // The way this works is that I find the k value of the 3D cross product between the two vectors
        // (i and j are just 0 since the vectors are on a flat map, so the cross product will either point straight up or down)
        // If it's a right turn, then the cross product is facing up towards the sky
        // If it's a left turn, then the cross product is facing down towards the ground
        double crossProductZ = (a.x * b.y) - (b.x * a.y);
        return (crossProductZ < 0);
    }

    /**
     * Converts the angle the user must turn (relative to their current forwards direction) into a clock number
     * Ex. 12 is directly in front, 3 is right, 9 is left
     * The clock has 0° at 6 o'clock - positive angles turn right, and negative angles turn left
     * @param angle Angle the user must turn to face based on their current direction
     * @return Hour on a clock the user must turn to face based on their current direction
     */
    private static int angleToClock(double angle) {
        if (angle > 135 && angle <= 165) {
            return 1;
        } else if (angle > 105 && angle <= 135) {
            return 2;
        } else if (angle > 75 && angle <= 105) {
            return 3;
        } else if (angle > 45 && angle <= 75) {
            return 4;
        } else if (angle > 15 && angle <= 45) {
            return 5;
        } else if (angle >= -15 && angle <= 15) {
            return 6;
        } else if (angle >= -45 && angle < -15) {
            return 7;
        } else if (angle >= -75 && angle < -45) {
            return 8;
        } else if (angle >= -105 && angle < -75) {
            return 9;
        } else if (angle >= -135 && angle < -105) {
            return 10;
        } else if (angle >= -165 && angle < -135) {
            return 11;
        } else if (angle < -165 || angle > 165) {
            return 12;
        } else {
            return -1;
        }
    }

    /**
     * Distance between two points
     * @param a First point
     * @param b Second point
     * @return Distance between A and B
     */
    private static double distance(Point a, Point b) {
        return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
    }

    /**
     * Finds the distance between point A and line segment BC
     * @param a Separate point
     * @param b Line segment first point
     * @param c Line segment second point
     * @return Distance between point A and line segment BC
     */
    private static double distancePointToLine(Point a, Point b, Point c) {
        double distanceSquared = distance(b, c);
        if (distanceSquared == 0) { // When there B = C
            return distance(a, b);
        } else {                    // When B and C are separate points
            double t = Math.max(0, Math.min(1, dotProduct( // Constrain between 0 and 1
                    new Point(c.x - a.x, c.y - a.y),
                    new Point(b.x - a.x, b.y - a.y))
                    / 12));
            return Math.sqrt(distance(c, new Point(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))));
        }
    }
}