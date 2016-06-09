package com.tonydicola.bletest.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.ValueDependentColor;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {

    // UUIDs for UAT service and associated characteristics.
    public static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    public static UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    // UUID for the BTLE client characteristic which is necessary for notifications.
    public static UUID CLIENT_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // UI elements
    private TextView countDisplay;
    private TextView timeDisplay;
    private String timer;

    // BTLE state
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;

    // bitQuit State
    private int numCigs;
    private int thisMonth;

    //private String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/smoking_data.txt";
//    private String path = "smoking_data.txt";
    //File history = new File(getApplicationContext().getFilesDir(), path);

    // Main BTLE device callback where much of the logic occurs.
    private BluetoothGattCallback callback = new BluetoothGattCallback() {
        // Called whenever the device connection state changes, i.e. from disconnected to connected.
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
//                writeLine("Connected!");
                // Discover services.
                if (!gatt.discoverServices()) {
//                    writeLine("Failed to start discovering services!");
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
//                writeLine("Disconnected!");
            } else {
//                writeLine("Connection state changed.  New state: " + newState);
            }
        }

        // Called when services have been discovered on the remote device.
        // It seems to be necessary to wait for this discovery to occur before
        // manipulating any services or characteristics.
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
//                writeLine("Service discovery completed!");
            } else {
//                writeLine("Service discovery failed with status: " + status);
            }
            // Save reference to each characteristic.
            tx = gatt.getService(UART_UUID).getCharacteristic(TX_UUID);
            rx = gatt.getService(UART_UUID).getCharacteristic(RX_UUID);
            // Setup notifications on RX characteristic changes (i.e. data received).
            // First call setCharacteristicNotification to enable notification.
            if (!gatt.setCharacteristicNotification(rx, true)) {
//                writeLine("Couldn't set notifications for RX characteristic!");
            }
            // Next update the RX characteristic's client descriptor to enable notifications.
            if (rx.getDescriptor(CLIENT_UUID) != null) {
                BluetoothGattDescriptor desc = rx.getDescriptor(CLIENT_UUID);
                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                if (!gatt.writeDescriptor(desc)) {
                    //                  writeLine("Couldn't write RX client descriptor value!");
                }
            } else {
//                writeLine("Couldn't get RX client descriptor!");
            }
        }

        // Called when a remote characteristic changes (like the RX characteristic).
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            String bq_received = characteristic.getStringValue(0);
            bq_parse(bq_received);
        }
    };

    // BTLE device scanning callback.
    private LeScanCallback scanCallback = new LeScanCallback() {
        // Called when a device is found.
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            // Check if the device has the UART service.
            if (parseUUIDs(bytes).contains(UART_UUID)) {
                // Found a device, stop the scan.
                adapter.stopLeScan(scanCallback);
                // Connect to the device.
                // Control flow will now go to the callback functions when BTLE events occur.
                gatt = bluetoothDevice.connectGatt(getApplicationContext(), false, callback);
            }
        }
    };

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    // OnCreate, called once to initialize the activity.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Grab references to UI elements.
        countDisplay = (TextView) findViewById(R.id.CigCounter);
        timeDisplay = (TextView) findViewById(R.id.Timer);

        // Get Bluetooth Radio
        adapter = BluetoothAdapter.getDefaultAdapter();

        //Test for data file --> if none exists, initialize with fake data
//        if (!history.exists())
//            fakeData();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    // OnResume, called right before UI is displayed.  Start the BTLE connection.
    @Override
    protected void onResume() {
        super.onResume();

        // Get previous state of bitQuit -- saved in sharedPreferences
        SharedPreferences data = getSharedPreferences("bitQuit_state", Context.MODE_PRIVATE);
        numCigs = data.getInt("Count", 0);
        thisMonth = data.getInt("thisMonth", 50);

        // Write to countDisplay
        countDisplay.setText("# Cigarettes: " + Integer.toString(numCigs));
        countDisplay.invalidate();

        timeDisplay.setText("bitQuit is Unlocked");
        timeDisplay.invalidate();

        // Scan for all BTLE devices.
        // The first one with the UART service will be chosen--see the code in the scanCallback.
        adapter.startLeScan(scanCallback);
    }

    // OnStop, called right before the activity loses foreground focus.  Close the BTLE connection.
    @Override
    protected void onStop() {
        super.onStop();

        // Save state of bitQuit -- saved in sharedPreferences
        SharedPreferences data = getSharedPreferences("bitQuit_state", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = data.edit();
        editor.putInt("Count", numCigs);
        editor.apply();
        editor.putInt("thisMonth", thisMonth);
        editor.apply();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.tonydicola.bletest.app/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        if (gatt != null) {
            // For better reliability be careful to disconnect and close the connection.
            gatt.disconnect();
            gatt.close();
            gatt = null;
            tx = null;
            rx = null;
        }
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.disconnect();
    }

    /*
        // Falsify smoking history for demonstration purposes
        private void fakeData() {
            FileOutputStream outputStream;
            String data[] = {"2016-01-17 23:04:33 -255",
                    "2016-02-18 23:23:13 175", "2016-03-18 23:23:13 157"};

            for (int i = 0; i < data.length; i++) {
                try {
                    outputStream = openFileOutput(path, Context.MODE_APPEND);
                    outputStream.write(data[i].getBytes());
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    */
    // Generate Plot
    public void click_stats(View view) throws FileNotFoundException {

        GraphView graph = (GraphView) findViewById(R.id.graph);
        graph.removeAllSeries();
/*
        // Load data from file
        InputStream instream = new FileInputStream(history);
        try {

            InputStreamReader inputreader = new InputStreamReader(instream);
            BufferedReader buffreader = new BufferedReader(inputreader);
            String line;

            // read every line of the file into the line-variable, on line at the time
            do {
                line = buffreader.readLine();
                System.out.println(line);


            } while (line != null);

            instream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // close the file.
*/
/*
        // Example datum
        String data[] = {"2016-01-17 23:04:33 -10", "2016-01-18 23:23:13 -1", "2016-02-18 23:23:13 -1",
                "2016-02-18 23:23:13 -1", "2016-02-18 23:23:13 -1", "2016-03-18 23:23:13 -1", "2016-03-17 23:04:33 -10",
                "2016-03-18 23:23:13 -1", "2016-03-18 23:23:13 -1", "2016-03-18 23:23:13 -1", "2016-04-18 23:23:13 -1",
                "2016-04-18 23:23:13 -1"};

        int monthly[] = new int[12];

        // loop through data
        for (int i = 0; i < data.length; i++) {

            //Split int date/time/count
            String token[] = data[i].split("[ ]+");

            // Get month
            String tok[] = token[0].split("[-]+");
            int month = Integer.parseInt(tok[1]);

            // Get count
            int num = Integer.parseInt(token[2]);

            monthly[month - 1] -= num;

        }


        for (int i = 0; i < 12; i++) {
            new DataPoint(i + 1, monthly[i]);
        }
*/

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();


        BarGraphSeries<DataPoint> series = new BarGraphSeries<DataPoint>(new DataPoint[]{
                new DataPoint(1, 335),
                new DataPoint(2, 271),
                new DataPoint(3, 169),
                new DataPoint(4, thisMonth)
        });
        series.setSpacing(50);
        graph.addSeries(series);

        series.setDrawValuesOnTop(true);
        series.setValuesOnTopColor(Color.BLACK);
        graph.setTitle("Monthly Cigarette Use");
        graph.setTitleTextSize(30);
        graph.setTitleColor(Color.BLACK);
        graph.setTitleTextSize(100);

        StaticLabelsFormatter staticLabelsFormatter = new StaticLabelsFormatter(graph);
        staticLabelsFormatter.setHorizontalLabels(new String[]{"","Jan", "Feb", "Mar", "Apr",""}); //, "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"});
        graph.getGridLabelRenderer().setLabelFormatter(staticLabelsFormatter);


        Viewport axes = graph.getViewport();
        axes.setXAxisBoundsManual(true);
        axes.setMinX(0);
        axes.setMaxX(5);


    }



    private void bq_parse(final String str){

        if (str.equals("o")) {
            // Write to countDisplay
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    timeDisplay.setText("bitQuit is Unlocked");
                    timeDisplay.invalidate();
                }
            });
        } else if (str.charAt(0) == 'c')
            checkCount(str);
        else{

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    timeDisplay.setText("bitQuit opens in " + str);
                    timeDisplay.invalidate();
                }
            });
        }
    }


    // Check recently received cigarette count against last cigarette count
    private void checkCount(String rec) {

        String[] token = rec.split("[ ]+");
        int newCount = Integer.parseInt(token[1]);

        // Detect removed cigarettes
        if (numCigs > newCount) {
            FileOutputStream outputStream;

            Calendar c = Calendar.getInstance();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String time = df.format(c.getTime());

            String datum = time + " " + Integer.toString(newCount - numCigs);

            thisMonth += numCigs - newCount;

/*
            // Write event to database file
            try {
                outputStream = openFileOutput(path, Context.MODE_APPEND);
                outputStream.write(datum.getBytes());
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
*/
        }
        // Save new count
        numCigs = newCount;

        // Write to countDisplay
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                countDisplay.setText("# Cigarettes: " + Integer.toString(numCigs));
                countDisplay.invalidate();
            }
        });
    }


    // Filtering by custom UUID is broken in Android 4.3 and 4.4, see:
    //   http://stackoverflow.com/questions/18019161/startlescan-with-128-bit-uuids-doesnt-work-on-native-android-ble-implementation?noredirect=1#comment27879874_18019161
    // This is a workaround function from the SO thread to manually parse advertisement data.
    private List<UUID> parseUUIDs(final byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<UUID>();

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0)
                break;

            int type = advertisedData[offset++];
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++];
                        uuid16 += (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    // Loop through the advertised 128-bit UUID's.
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            uuids.add(new UUID(leastSignificantBit,
                                    mostSignificantBit));
                        } catch (IndexOutOfBoundsException e) {
                            // Defensive programming.
                            //Log.e(LOG_TAG, e.toString());
                            continue;
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                default:
                    offset += (len - 1);
                    break;
            }
        }
        return uuids;
    }

    // Boilerplate code from the activity creation:

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.tonydicola.bletest.app/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }
}
