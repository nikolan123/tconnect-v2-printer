package com.niko.tconnectprinter;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String TAG = "TConnectPrinter";
    private static final String ACTION_USB_PERMISSION = "com.niko.tconnectprinter.USB_PERMISSION";
    private static final int PRINTER_PRODUCT_ID = 9219;
    private static final int USB_CLASS_PRINTER = 7;
    private static final int MAX_CHARS_PER_LINE = 43;
    private static final byte SAFE_CONTRAST = 50;
    private static final byte SAFE_SPEED = 1;

    private UsbManager usbManager;
    private UsbDevice printerDevice;
    private UsbDeviceConnection connection;
    private UsbInterface printerInterface;
    private UsbEndpoint readEndpoint;
    private UsbEndpoint writeEndpoint;
    private EditText textInput;
    private EditText padInput;
    private EditText contrastInput;
    private EditText speedInput;
    private EditText lineDelayInput;
    private Spinner fontSpinner;
    private Button printButton;
    private TextView statusText;
    private boolean statusLoopRunning;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                    printerDevice = device;
                    openPrinter();
                } else {
                    setStatus("USB permission denied");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        registerReceiver(receiver, new IntentFilter(ACTION_USB_PERMISSION));
        UsbDevice device = findPrinter();
        if (device == null) {
            setStatus("Printer USB device not found");
            return;
        }
        printerDevice = device;
        if (usbManager.hasPermission(device)) {
            openPrinter();
        } else {
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(device, pi);
            setStatus("Requesting USB permission");
        }
    }

    @Override
    protected void onDestroy() {
        statusLoopRunning = false;
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("T-Connect Printer");
        title.setTextSize(24);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setText("Connecting...");
        statusText.setTextSize(16);
        root.addView(statusText);

        addLabel(root, "Receipt text");

        textInput = new EditText(this);
        textInput.setMinLines(6);
        textInput.setGravity(48);
        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        textInput.setText("testing testing 123");
        root.addView(textInput);

        addLabel(root, "Font size");

        fontSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
                new String[]{"Small - stable", "Regular - may reboot", "Large - may reboot"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fontSpinner.setAdapter(adapter);
        root.addView(fontSpinner);

        addLabel(root, "Blank lines after receipt");

        padInput = new EditText(this);
        padInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        padInput.setText("4");
        root.addView(padInput);

        addLabel(root, "Contrast");
        contrastInput = numberInput("50");
        root.addView(contrastInput);

        addLabel(root, "Speed");
        speedInput = numberInput("1");
        root.addView(speedInput);

        addLabel(root, "Delay between lines, ms");
        lineDelayInput = numberInput("180");
        root.addView(lineDelayInput);

        printButton = new Button(this);
        printButton.setText("Print receipt");
        printButton.setEnabled(false);
        printButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                printReceipt();
            }
        });
        root.addView(printButton);

        setContentView(scrollView);
    }

    private void addLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15);
        root.addView(label);
    }

    private EditText numberInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(value);
        return input;
    }

    private UsbDevice findPrinter() {
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getProductId() == PRINTER_PRODUCT_ID) {
                return device;
            }
        }
        return null;
    }

    private void openPrinter() {
        connection = usbManager.openDevice(printerDevice);
        if (connection == null) {
            setStatus("openDevice failed");
            return;
        }
        for (int i = 0; i < printerDevice.getInterfaceCount(); i++) {
            UsbInterface candidate = printerDevice.getInterface(i);
            if (candidate.getInterfaceClass() == USB_CLASS_PRINTER) {
                printerInterface = candidate;
            }
        }
        if (printerInterface == null || !connection.claimInterface(printerInterface, true)) {
            connection.close();
            setStatus("claimInterface failed");
            return;
        }
        for (int i = 0; i < printerInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = printerInterface.getEndpoint(i);
            if (endpoint.getDirection() == 128) {
                readEndpoint = endpoint;
            } else {
                writeEndpoint = endpoint;
            }
        }
        if (writeEndpoint == null || readEndpoint == null) {
            connection.releaseInterface(printerInterface);
            connection.close();
            setStatus("Printer endpoints not found");
            return;
        }
        startStatusLoop();
        printButton.setEnabled(true);
        setStatus("Printer ready");
    }

    private void startStatusLoop() {
        if (statusLoopRunning) {
            return;
        }
        statusLoopRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] status = new byte[8];
                while (statusLoopRunning && connection != null && readEndpoint != null) {
                    int result = connection.bulkTransfer(readEndpoint, status, status.length, 4000);
                    Log.d(TAG, "status result=" + result + " b0=" + status[0] + " b1=" + status[1] + " fw=" + status[6]);
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "status sleep interrupted", e);
                    }
                }
            }
        }).start();
    }

    private void printReceipt() {
        if (connection == null || writeEndpoint == null) {
            openPrinter();
        }
        if (connection == null || writeEndpoint == null) {
            setStatus("Printer not open");
            return;
        }
        printButton.setEnabled(false);
        setStatus("Printing...");
        final byte contrast = readByteSetting(contrastInput, 50, 0, 255);
        final byte speed = readByteSetting(speedInput, 1, 0, 255);
        final byte fontCommand = selectedFontCommand();
        final int padLines = readIntSetting(padInput, 4, 0, 12);
        final int lineDelayMs = readIntSetting(lineDelayInput, 180, 0, 2000);
        final ArrayList<String> lines = wrapLines(textInput.getText().toString());
        new Thread(new Runnable() {
            @Override
            public void run() {
                setContrast(contrast);
                setSpeed(speed);
                for (int i = 0; i < lines.size(); i++) {
                    sendLine(fontCommand, lines.get(i));
                    sleepQuietly(lineDelayMs);
                }
                for (int i = 0; i < padLines; i++) {
                    sendLine((byte) 176, "");
                    sleepQuietly(lineDelayMs);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        printButton.setEnabled(true);
                        setStatus("Print sent");
                        Toast.makeText(MainActivity.this, "Print sent", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private byte selectedFontCommand() {
        int index = fontSpinner.getSelectedItemPosition();
        if (index == 1) {
            return (byte) 160;
        }
        if (index == 2) {
            return (byte) 144;
        }
        return (byte) 176;
    }

    private int readIntSetting(EditText input, int defaultValue, int min, int max) {
        try {
            int value = Integer.parseInt(input.getText().toString());
            if (value < min) {
                return min;
            }
            if (value > max) {
                return max;
            }
            return value;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private byte readByteSetting(EditText input, int defaultValue, int min, int max) {
        return (byte) readIntSetting(input, defaultValue, min, max);
    }

    private ArrayList<String> wrapLines(String text) {
        ArrayList<String> lines = new ArrayList<String>();
        String normalized = text.replace('\r', '\n');
        String[] rawLines = normalized.split("\n", -1);
        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i];
            if (line.length() == 0) {
                lines.add("");
                continue;
            }
            while (line.length() > MAX_CHARS_PER_LINE) {
                lines.add(line.substring(0, MAX_CHARS_PER_LINE));
                line = line.substring(MAX_CHARS_PER_LINE);
            }
            lines.add(line);
        }
        return lines;
    }

    private void setContrast(byte value) {
        byte[] packet = new byte[64];
        packet[0] = (byte) 0x88;
        packet[1] = value;
        int result = connection.bulkTransfer(writeEndpoint, packet, packet.length, 4000);
        Log.d(TAG, "setContrast result=" + result + " value=" + value);
    }

    private void setSpeed(byte value) {
        byte[] packet = new byte[64];
        packet[0] = (byte) 0x8B;
        packet[1] = value;
        int result = connection.bulkTransfer(writeEndpoint, packet, packet.length, 4000);
        Log.d(TAG, "setSpeed result=" + result + " value=" + value);
    }

    private void sendLine(byte fontCommand, String text) {
        byte[] packet = new byte[64];
        packet[0] = fontCommand;
        if (text.length() > MAX_CHARS_PER_LINE) {
            text = text.substring(0, MAX_CHARS_PER_LINE);
        }
        try {
            byte[] bytes = text.getBytes("ISO-8859-1");
            System.arraycopy(bytes, 0, packet, 1, Math.min(bytes.length, 63));
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "encoding failed", e);
        }
        int result = connection.bulkTransfer(writeEndpoint, packet, packet.length, 4000);
        Log.d(TAG, "bulkTransfer result=" + result + " text=" + text);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.e(TAG, "sleep interrupted", e);
        }
    }

    private void setStatus(final String message) {
        Log.d(TAG, message);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText(message);
            }
        });
    }
}
