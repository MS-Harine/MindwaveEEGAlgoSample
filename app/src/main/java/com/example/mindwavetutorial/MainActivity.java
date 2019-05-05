package com.example.mindwavetutorial;

import android.bluetooth.BluetoothAdapter;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.*;
import com.neurosky.AlgoSdk.NskAlgoDataType;
import com.neurosky.AlgoSdk.NskAlgoSdk;
import com.neurosky.AlgoSdk.NskAlgoSignalQuality;
import com.neurosky.AlgoSdk.NskAlgoState;
import com.neurosky.AlgoSdk.NskAlgoType;
import com.neurosky.connection.ConnectionStates;
import com.neurosky.connection.DataType.MindDataType;
import com.neurosky.connection.TgStreamHandler;
import com.neurosky.connection.TgStreamReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements OnClickListener {

    final String TAG = "MainActivityTag";

    private NskAlgoSdk nskAlgoSdk;
    // graph plot variables
    private final static int X_RANGE = 50;
    private SimpleXYSeries bp_deltaSeries = null;
    private SimpleXYSeries bp_thetaSeries = null;
    private SimpleXYSeries bp_alphaSeries = null;
    private SimpleXYSeries bp_betaSeries = null;
    private SimpleXYSeries bp_gammaSeries = null;

    // COMM SDK handles
    private TgStreamReader tgStreamReader;
    private BluetoothAdapter mBluetoothAdapter;

    // internal variables
    private boolean bInited = false;
    private boolean bRunning = false;
    private NskAlgoType currentSelectedAlgo;

    // canned data variables
    private short raw_data[] = {0};
    private int raw_data_index= 0;
    private float output_data[];
    private int output_data_count = 0;
    private int raw_data_sec_len = 85;

    // UI components
    private XYPlot plot;
    private EditText text;

    private Button headsetButton;
    private Button cannedButton;
    private Button setAlgosButton;
    private Button setIntervalButton;
    private Button startButton;
    private Button stopButton;

    private SeekBar intervalSeekBar;
    private TextView intervalText;

    private Button bpText;

    private TextView attValue;
    private TextView medValue;

    private CheckBox attCheckBox;
    private CheckBox medCheckBox;
    private CheckBox blinkCheckBox;
    private CheckBox bpCheckBox;

    private TextView stateText;
    private TextView sqText;

    private ImageView blinkImage;
    private int bLastOutputInterval = 1;
    private int gcCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nskAlgoSdk = new NskAlgoSdk();
        try {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                Toast.makeText(
                        this,
                        "Please enable your Bluetooth and re-run this program!",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "error: " + e.getMessage());
            return;
        }

        headsetButton = (Button)this.findViewById(R.id.headsetButton);
        cannedButton = (Button)this.findViewById(R.id.cannedDataButton);
        setAlgosButton = (Button)this.findViewById(R.id.setAlgosButton);
        setIntervalButton = (Button)this.findViewById(R.id.setIntervalButton);
        startButton = (Button)this.findViewById(R.id.startButton);
        stopButton = (Button)this.findViewById(R.id.stopButton);
        intervalSeekBar = (SeekBar)this.findViewById(R.id.intervalSeekBar);
        intervalText = (TextView)this.findViewById(R.id.intervalText);
        bpText = (Button)this.findViewById(R.id.bpTitle);
        attValue = (TextView)this.findViewById(R.id.attText);
        medValue = (TextView)this.findViewById(R.id.medText);
        attCheckBox = (CheckBox)this.findViewById(R.id.attCheckBox);
        medCheckBox = (CheckBox)this.findViewById(R.id.medCheckBox);
        blinkCheckBox = (CheckBox)this.findViewById(R.id.blinkCheckBox);
        bpCheckBox = (CheckBox)this.findViewById(R.id.bpCheckBox);
        blinkImage = (ImageView)this.findViewById(R.id.blinkImage);
        stateText = (TextView)this.findViewById(R.id.stateText);
        sqText = (TextView)this.findViewById(R.id.sqText);

        bpText.setEnabled(false);
        intervalSeekBar.setEnabled(false);
        setIntervalButton.setEnabled(false);

        plot = (XYPlot) findViewById(R.id.myPlot);
        plot.setVisibility(View.INVISIBLE);
        text = (EditText) findViewById(R.id.myText);
        text.setVisibility(View.INVISIBLE);

        headsetButton.setOnClickListener(this);
        cannedButton.setOnClickListener(this);
        startButton.setOnClickListener(this);
        stopButton.setOnClickListener(this);
        setAlgosButton.setOnClickListener(this);
        bpText.setOnClickListener(this);

        intervalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                bLastOutputInterval = seekBar.getProgress();
            }
        });

        nskAlgoSdk.setOnSignalQualityListener(new NskAlgoSdk.OnSignalQualityListener() {
            @Override
            public void onSignalQuality(int level) {
                final int fLevel = level;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String sqStr = NskAlgoSignalQuality.values()[fLevel].toString();
                        sqText.setText(sqStr);
                    }
                });
            }
        });

        nskAlgoSdk.setOnStateChangeListener(new NskAlgoSdk.OnStateChangeListener() {
            @Override
            public void onStateChange(int state, int reason) {
                String stateStr = "";
                String reasonStr = "";

                for (NskAlgoState n : NskAlgoState.values()) {
                    if (n.value == state)
                        stateStr = n.toString();
                    if (n.value == reason)
                        reasonStr = n.toString();
                }

                final String finalStateStr = stateStr + " | " + reasonStr;
                final int finalState = state;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        stateText.setText(finalStateStr);

                        if (finalState == NskAlgoState.NSK_ALGO_STATE_RUNNING.value ||
                                finalState == NskAlgoState.NSK_ALGO_STATE_COLLECTING_BASELINE_DATA.value) {
                            bRunning = true;
                            startButton.setText("Pause");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(true);
                        }
                        else if (finalState == NskAlgoState.NSK_ALGO_STATE_STOP.value) {
                            bRunning = false;
                            raw_data = null;
                            raw_data_index = 0;
                            startButton.setText("Start");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(false);

                            headsetButton.setEnabled(true);
                            cannedButton.setEnabled(true);

                            if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                                tgStreamReader.stop();
                                tgStreamReader.close();
                            }

                            output_data_count = 0;
                            output_data = null;

                            System.gc();
                        }
                        else if (finalState == NskAlgoState.NSK_ALGO_STATE_PAUSE.value) {
                            bRunning = false;
                            startButton.setText("Start");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(true);
                        }
                        else if (finalState == NskAlgoState.NSK_ALGO_STATE_ANALYSING_BULK_DATA.value) {
                            bRunning = true;
                            startButton.setText("Start");
                            startButton.setEnabled(false);
                            stopButton.setEnabled(true);
                        }
                        else if (finalState == NskAlgoState.NSK_ALGO_STATE_INITED.value ||
                                    finalState == NskAlgoState.NSK_ALGO_STATE_UNINTIED.value) {
                            bRunning = false;
                            startButton.setText("Start");
                            startButton.setEnabled(true);
                            stopButton.setEnabled(false);
                        }
                    }
                });
            }
        });

        nskAlgoSdk.setOnAttAlgoIndexListener(new NskAlgoSdk.OnAttAlgoIndexListener() {
            @Override
            public void onAttAlgoIndex(int value) {
                final String finalAttStr = "[" + value + "]";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        attValue.setText(finalAttStr);
                    }
                });
            }
        });

        nskAlgoSdk.setOnMedAlgoIndexListener(new NskAlgoSdk.OnMedAlgoIndexListener() {
            @Override
            public void onMedAlgoIndex(int value) {
                final String finalMedStr = "[" + value + "]";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        medValue.setText(finalMedStr);
                    }
                });
            }
        });

        nskAlgoSdk.setOnEyeBlinkDetectionListener(new NskAlgoSdk.OnEyeBlinkDetectionListener() {
            @Override
            public void onEyeBlinkDetect(int i) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        blinkImage.setImageResource(R.mipmap.led_on);
                        Timer timer = new Timer();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        blinkImage.setImageResource(R.mipmap.led_off);
                                    }
                                });
                            }
                        }, 500);
                    }
                });
            }
        });

        nskAlgoSdk.setOnBPAlgoIndexListener(new NskAlgoSdk.OnBPAlgoIndexListener() {
            @Override
            public void onBPAlgoIndex(float delta, float theta, float alpha, float beta, float gamma) {
                final float fDelta = delta, fTheta = theta, fAlpha = alpha, fBeta = beta, fGamma = gamma;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AddValueToPlot(bp_deltaSeries, fDelta);
                        AddValueToPlot(bp_thetaSeries, fTheta);
                        AddValueToPlot(bp_alphaSeries, fAlpha);
                        AddValueToPlot(bp_betaSeries, fBeta);
                        AddValueToPlot(bp_gammaSeries, fGamma);
                    }
                });
            }
        });
    }

    @Override
    public void onBackPressed() {
        nskAlgoSdk.NskAlgoUninit();
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.headsetButton) {
            output_data_count = 0;
            output_data = null;

            raw_data = new short[512];
            raw_data_index = 0;

            cannedButton.setEnabled(false);
            headsetButton.setEnabled(false);
            startButton.setEnabled(false);

            tgStreamReader = new TgStreamReader(mBluetoothAdapter, callback);
            if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                tgStreamReader.stop();
                tgStreamReader.close();
            }

            tgStreamReader.connect();
        }
        else if (v.getId() == R.id.cannedDataButton) {
            output_data_count = 0;
            output_data = null;

            System.gc();

            headsetButton.setEnabled(false);
            cannedButton.setEnabled(false);

            AssetManager assetManager = getAssets();
            InputStream inputStream = null;

            try {
                inputStream = assetManager.open("output_data.bin");
                output_data_count = 0;
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                try {
                    String line = reader.readLine();
                    while (!(line == null) || (line.isEmpty())) {
                        output_data_count++;
                        line = reader.readLine();
                    }
                } catch (IOException e) {}
                inputStream.close();

                if (output_data_count > 0) {
                    inputStream = assetManager.open("output_data.bin");
                    output_data = new float[output_data_count];
                    int i = 0;
                    reader = new BufferedReader(new InputStreamReader(inputStream));
                    try {
                        String line = reader.readLine();
                        while (i < output_data_count) {
                            output_data[i++] = Float.parseFloat(line);
                            line = reader.readLine();
                        }
                    } catch (IOException e) {}
                    inputStream.close();
                }
            } catch (IOException e) { }

            try {
                inputStream = assetManager.open("raw_data_em.bin");
                raw_data = readData(inputStream, 512 * raw_data_sec_len);
                raw_data_index = 512 * raw_data_sec_len;
                inputStream.close();
                // TODO: 얘는 뭐하는 앨까?
                nskAlgoSdk.NskAlgoDataStream(
                        NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value,
                        raw_data,
                        raw_data_index);
            } catch (IOException e) {}
        }
        else if (v.getId() == R.id.startButton) {
            if (!bRunning)
                nskAlgoSdk.NskAlgoStart(false);
            else
                nskAlgoSdk.NskAlgoPause();
        }
        else if (v.getId() == R.id.stopButton) {
            nskAlgoSdk.NskAlgoStop();
        }
        else if (v.getId() == R.id.setAlgosButton) {
            int algoTypes = 0;

            startButton.setEnabled(false);
            stopButton.setEnabled(false);
            clearAllSeries();

            text.setVisibility(View.INVISIBLE);
            text.setText("");
            bpText.setEnabled(false);

            currentSelectedAlgo = NskAlgoType.NSK_ALGO_TYPE_INVALID;
            intervalSeekBar.setEnabled(false);
            setIntervalButton.setEnabled(false);
            intervalText.setText("--");
            attValue.setText("--");
            medValue.setText("--");
            stateText.setText("");
            sqText.setText("");

            if (medCheckBox.isChecked())
                algoTypes += NskAlgoType.NSK_ALGO_TYPE_MED.value;
            if (attCheckBox.isChecked())
                algoTypes += NskAlgoType.NSK_ALGO_TYPE_ATT.value;
            if (blinkCheckBox.isChecked())
                algoTypes += NskAlgoType.NSK_ALGO_TYPE_BLINK.value;
            if (bpCheckBox.isChecked()) {
                algoTypes += NskAlgoType.NSK_ALGO_TYPE_BP.value;
                bpText.setEnabled(true);
                bp_deltaSeries = createSeries("Delta");
                bp_thetaSeries = createSeries("Theta");
                bp_alphaSeries = createSeries("Alpha");
                bp_betaSeries = createSeries("Beta");
                bp_gammaSeries = createSeries("Gamma");
            }

            if (algoTypes == 0)
                showDialog("Please select at least one algorithm");
            else {
                if (bInited) {
                    nskAlgoSdk.NskAlgoUninit();
                    bInited = false;
                }
                int ret = nskAlgoSdk.NskAlgoInit(algoTypes, getFilesDir().getAbsolutePath());
                if (ret == 0)
                    bInited = true;

                String sdkVersion = "SDK ver.: " + nskAlgoSdk.NskAlgoSdkVersion();
                if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_ATT.value) != 0)
                    sdkVersion += "\nATT ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_ATT.value);
                if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_MED.value) != 0)
                    sdkVersion += "\nMED ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_MED.value);
                if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_BLINK.value) != 0)
                    sdkVersion += "\nBlink ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_BLINK.value);
                if ((algoTypes & NskAlgoType.NSK_ALGO_TYPE_BP.value) != 0)
                    sdkVersion += "\nEEG Bandpower ver.: " + nskAlgoSdk.NskAlgoAlgoVersion(NskAlgoType.NSK_ALGO_TYPE_BP.value);
                showToast(sdkVersion, Toast.LENGTH_LONG);
            }
        }
        else if (v.getId() == R.id.bpTitle) {
            removeAllSeriesFromPlot();
            setupPlot(-20, 20, "EEG Bandpower");
            addSeries(plot, bp_deltaSeries, R.xml.line_point_formatter_with_plf1);
            addSeries(plot, bp_thetaSeries, R.xml.line_point_formatter_with_plf2);
            addSeries(plot, bp_alphaSeries, R.xml.line_point_formatter_with_plf3);
            addSeries(plot, bp_betaSeries, R.xml.line_point_formatter_with_plf4);
            addSeries(plot, bp_gammaSeries, R.xml.line_point_formatter_with_plf5);
            plot.redraw();

            text.setVisibility(View.INVISIBLE);
            currentSelectedAlgo = NskAlgoType.NSK_ALGO_TYPE_BP;

            intervalSeekBar.setMax(1);
            intervalSeekBar.setProgress(0);
            intervalSeekBar.setEnabled(false);
            intervalText.setText("1");
            setIntervalButton.setEnabled(false);
        }
        else if (v.getId() == R.id.setIntervalButton) {
            int ret = -1;
            String toastStr = "";

            // TODO: 왜 이렇게 있을까..?
            if (ret == 0) {
                showToast(toastStr + ": success", Toast.LENGTH_SHORT);
            } else {
                showToast(toastStr + ": fail", Toast.LENGTH_SHORT);
            }
        }
    }

    private short[] readData(InputStream is, int size) {
        short data[] = new short[size];
        int lineCount = 0;
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        try {
            while (lineCount < size) {
                String line = reader.readLine();
                if (line == null || line.isEmpty())
                    break;
                data[lineCount] = Short.parseShort(line);
                lineCount++;
            }
        } catch (IOException e) { }
        return data;
    }

    private void removeAllSeriesFromPlot() {
        if (bp_deltaSeries != null)
            plot.removeSeries(bp_deltaSeries);
        if (bp_thetaSeries != null)
            plot.removeSeries(bp_thetaSeries);
        if (bp_alphaSeries != null)
            plot.removeSeries(bp_alphaSeries);
        if (bp_betaSeries != null)
            plot.removeSeries(bp_betaSeries);
        if (bp_gammaSeries != null)
            plot.removeSeries(bp_gammaSeries);
        System.gc();
    }
    private void clearAllSeries() {
        if (bp_deltaSeries != null) {
            plot.removeSeries(bp_deltaSeries);
            bp_deltaSeries = null;
        }
        if (bp_thetaSeries != null) {
            plot.removeSeries(bp_thetaSeries);
            bp_thetaSeries = null;
        }
        if (bp_alphaSeries != null) {
            plot.removeSeries(bp_alphaSeries);
            bp_alphaSeries = null;
        }
        if (bp_betaSeries != null) {
            plot.removeSeries(bp_betaSeries);
            bp_betaSeries = null;
        }
        if (bp_gammaSeries != null) {
            plot.removeSeries(bp_gammaSeries);
            bp_gammaSeries = null;
        }
        plot.setVisibility(View.INVISIBLE);
        System.gc();
    }
    private XYPlot setupPlot(Number rangeMin, Number rangeMax, String title) {
        // initialize our XYPlot reference:
        plot = (XYPlot) findViewById(R.id.myPlot);

        if ((rangeMax.intValue() - rangeMin.intValue()) < 10) {
            plot.setRangeStepValue((rangeMax.intValue() - rangeMin.intValue() + 1));
        } else {
            plot.setRangeStepValue(11);
        }
        plot.setRangeBoundaries(rangeMin.intValue(), rangeMax.intValue(), BoundaryMode.FIXED);
        plot.setDomainBoundaries(0, X_RANGE, BoundaryMode.FIXED);

        //plot.setTicksPerDomainLabel(10);

        plot.setPlotPadding(0, 0, 0, 0);
        plot.setTitle(title);

        plot.setVisibility(View.VISIBLE);

        return plot;
    }
    private SimpleXYSeries createSeries(String seriesName) {
        SimpleXYSeries series = new SimpleXYSeries(
                null,
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY,
                seriesName);

        series.useImplicitXVals();
        return series;
    }
    private SimpleXYSeries addSeries(XYPlot plot, SimpleXYSeries series, int formatterId) {
        LineAndPointFormatter seriesFormat = new LineAndPointFormatter();
        seriesFormat.setPointLabelFormatter(null);
        seriesFormat.configure(getApplicationContext(), formatterId);
        seriesFormat.setVertexPaint(null);
        series.useImplicitXVals();

        plot.addSeries(series, seriesFormat);
        return series;
    }
    private void AddValueToPlot (SimpleXYSeries series, float value) {
        if (series.size() >= X_RANGE) {
            series.removeFirst();
        }
        Number num = value;
        series.addLast(null, num);
        plot.redraw();
        gcCount++;
        if (gcCount >= 20) {
            System.gc();
            gcCount = 0;
        }
    }

    private void showToast(final String msg, final int timeStyle) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }
        });
    }
    private void showDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private TgStreamHandler callback = new TgStreamHandler() {
        @Override
        public void onStatesChanged(int connectionStates) {
            switch (connectionStates) {
                case ConnectionStates.STATE_CONNECTING:
                    break;
                case ConnectionStates.STATE_CONNECTED:
                    tgStreamReader.start();
                    showToast("Connected", Toast.LENGTH_SHORT);
                    break;
                case ConnectionStates.STATE_WORKING:
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startButton.setEnabled(true);
                        }
                    });
                    break;
                case ConnectionStates.STATE_GET_DATA_TIME_OUT:
                    showToast("Get data time out!", Toast.LENGTH_SHORT);
                    if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                        tgStreamReader.stop();
                        tgStreamReader.close();
                    }
                    break;
                case ConnectionStates.STATE_STOPPED:
                    break;
                case ConnectionStates.STATE_DISCONNECTED:
                    break;
                case ConnectionStates.STATE_ERROR:
                    break;
                case ConnectionStates.STATE_FAILED:
                    break;
            }
        }

        @Override
        public void onDataReceived(int datatype, int data, Object obj) {
            switch (datatype) {
                case MindDataType.CODE_ATTENTION:
                    short attValue[] = { (short)data };
                    nskAlgoSdk.NskAlgoDataStream(
                            NskAlgoDataType.NSK_ALGO_DATA_TYPE_ATT.value,
                            attValue,
                            1);
                    break;
                case MindDataType.CODE_MEDITATION:
                    short medValue[] = { (short)data };
                    nskAlgoSdk.NskAlgoDataStream(
                            NskAlgoDataType.NSK_ALGO_DATA_TYPE_MED.value,
                            medValue,
                            1);
                    break;
                case MindDataType.CODE_POOR_SIGNAL:
                    short pqValue[] = { (short)data };
                    nskAlgoSdk.NskAlgoDataStream(
                            NskAlgoDataType.NSK_ALGO_DATA_TYPE_PQ.value,
                            pqValue,
                            1);
                    break;
                case MindDataType.CODE_RAW:
                    raw_data[raw_data_index++] = (short)data;
                    if (raw_data_index == 512) {
                        nskAlgoSdk.NskAlgoDataStream(
                                NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value,
                                raw_data,
                                raw_data_index);
                        raw_data_index = 0;
                    }
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onRecordFail(int i) { }

        @Override
        public void onChecksumFail(byte[] bytes, int i, int i1) { }
    };
}
