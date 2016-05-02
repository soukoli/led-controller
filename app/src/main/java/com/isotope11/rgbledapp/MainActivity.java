package com.isotope11.rgbledapp;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ToggleButton;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;

public class MainActivity extends Activity {

    private Socket socket;

    private static final int SERVERPORT = 4001;
    private String SERVER_IP;

	protected final String TAG = MainActivity.class.toString();

	int mRed = 0;
	int mGreen = 0;
	int mBlue = 0;

    int down = 0;

    Timer timer;
    TimerTask task;
    boolean update_force = false;
    boolean connected = false;

    private ColorPicker picker;
    private SaturationBar saturationBar;
    private ValueBar valueBar;

    private ColorPicker down_picker;
    private SaturationBar down_saturationBar;
    private ValueBar down_valueBar;


    private Menu menu;
    private String menuConnect = "Connect";
    private String menuDisconnect = "Disconnect";
    private String msg_json;

    SharedPreferences prefs;

    public void setAddress() {

        LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
        View promptView = layoutInflater.inflate(R.layout.popup, null);

        final EditText editText = (EditText) promptView.findViewById(R.id.edittext);
        editText.setText(SERVER_IP);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);

        alertDialogBuilder.setView(promptView).setCancelable(true)
                .setTitle("Settings")
                .setIcon(R.mipmap.ic_launcher)
                .setMessage("Please set IP address:")
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        SERVER_IP = editText.getText().toString();
                        prefs.edit().putString("ip_address", SERVER_IP).apply();
                        dialogInterface.dismiss();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        prefs = this.getSharedPreferences("com.isotope11.rgbledapp", Context.MODE_PRIVATE);

        String restored_ip = prefs.getString("ip_address", null);
        if (restored_ip != null) {
            SERVER_IP = restored_ip;
        } else {
            SERVER_IP = "192.168.1.105";
        }

        // ####### Night Mode
        ToggleButton toggleAlarm = (ToggleButton) findViewById(R.id.nigth_mode);

        toggleAlarm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(isChecked)
                {
                    if (!connected) {
                        connected = !connected;
                        updateStatus();
                    }
                    mRed = Color.red(0);
                    mGreen = Color.green(0);
                    mBlue = Color.blue(0);
                    down = Color.green(0);

                    update_force = true;
                }
            }
        });


        // ####### MAIN RGB Picker
        picker = (ColorPicker) findViewById(R.id.picker);
        saturationBar = (SaturationBar) findViewById(R.id.saturationbar);
        valueBar = (ValueBar) findViewById(R.id.valuebar);

        picker.addSaturationBar(saturationBar);
        picker.addValueBar(valueBar);

        picker.setOnColorChangedListener(new ColorPicker.OnColorChangedListener() {
            @Override
            public void onColorChanged(int color) {
                picker.setOldCenterColor(picker.getColor());
                if (connected) {
                    mRed = Color.red(picker.getColor());
                    mGreen = Color.green(picker.getColor());
                    mBlue = Color.blue(picker.getColor());

                    update_force = true;
                }
            }
        });

        // ####### DOWN Picker
        down_picker = (ColorPicker) findViewById(R.id.downpicker);
        down_saturationBar = (SaturationBar) findViewById(R.id.downsaturationbar);
        down_valueBar = (ValueBar) findViewById(R.id.downvaluebar);

        down_picker.addSaturationBar(down_saturationBar);
        down_picker.addValueBar(down_valueBar);

        down_picker.setColor(0);

        down_picker.setOnColorChangedListener(new ColorPicker.OnColorChangedListener() {
            @Override
            public void onColorChanged(int color) {
                if (connected) {
                    down = Color.green(down_picker.getColor());

                    update_force = true;
                }
            }
        });

        task = new TimerTask() {
			public void run(){
                if (update_force) {
                    RGBLedSetter task = new RGBLedSetter();
                    task.execute();
                }

			}
		};
		timer = new Timer();
		timer.schedule(task, 0, 33); // Around 30 times a second
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        this.menu = menu;
		return true;
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch(item.getItemId()) {
            case R.id.action_connect:
                connected = !connected;
                updateStatus();
                break;
            case R.id.action_settings:
                setAddress();
                break;
            case R.id.action_exit: {
                exit_action();
                break;
            }
        }
        return true;
    }

    public void updateStatus() {
        // This code will always run on the UI thread, therefore is safe to modify UI elements.
        MenuItem bedMenuItem = menu.findItem(R.id.action_connect);
        if (connected) {
            bedMenuItem.setTitle(menuDisconnect);
        } else {
            bedMenuItem.setTitle(menuConnect);
        }

    }

    private void exit_action() {
        timer.cancel();
        finish();
        System.exit(0);
    }

    public String getMapped(int val){
        float newVal = Float.valueOf(val) / Float.valueOf(255);
        //Log.d(TAG, Float.toString(newVal));
        return Float.toString(newVal);
    }

	private class RGBLedSetter extends AsyncTask<Object, Void, String> {

		@Override
		protected String doInBackground(Object... arg0) {

            if (connected) {

                msg_json = "{\"red\":" + getMapped(mRed) +
                        ",\"green\":" + getMapped(mGreen) +
                        ",\"blue\":" + getMapped(mBlue) +
                        ",\"down\":" + getMapped(down) + "}";

                //Log.d(TAG, msg_json);

                try {
                    InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
                    socket = new Socket(serverAddr, SERVERPORT);


                    DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                    //dos.writeUTF(enterMessage.getText().toString());

                    PrintWriter pw = new PrintWriter(dos);
                    pw.println(msg_json);
                    pw.flush();

                } catch (UnknownHostException e1) {
                    connected = false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // This code will always run on the UI thread, therefore is safe to modify UI elements.
                            updateStatus();

                        }
                    });
                    //e1.printStackTrace();
                } catch (IOException e1) {
                    connected = false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // This code will always run on the UI thread, therefore is safe to modify UI elements.
                            updateStatus();

                        }
                    });
                    //e1.printStackTrace();
                }
            }

            update_force = false;
			return "nope";
		}

	}
}
