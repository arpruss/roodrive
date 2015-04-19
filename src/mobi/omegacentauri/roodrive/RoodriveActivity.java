package mobi.omegacentauri.roodrive;

import java.util.ArrayList;
import java.util.Collections;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.Comparator;

public class RoodriveActivity extends Activity {
    private BluetoothAdapter btAdapter;
	private ArrayList<BluetoothDevice> devs;
	private ArrayAdapter<String> deviceSelectionAdapter;
	private Spinner deviceSpinner;
	private SharedPreferences options;
	private android.widget.TextView message;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        options = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(R.layout.main);
        deviceSpinner = (Spinner)findViewById(R.id.device_spinner);
        message = (android.widget.TextView)findViewById(R.id.message);
    }
    
    public void onConnect(View v) {
    	int pos = deviceSpinner.getSelectedItemPosition();
		if (pos < 0) {
			Toast.makeText(this, "Select a device", Toast.LENGTH_LONG).show();
			return;
		}

		//devs.get(pos);

		options.edit().putString(RemoteDevice.PREF_BT_ADDRESS, devs.get(pos).getAddress()).commit();
		
        Intent i = new Intent(this, RoodriveService.class);
        stopService(i);
        i = new Intent(this, RoodriveService.class);
    	startService(i);
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	
    	btAdapter = BluetoothAdapter.getDefaultAdapter();
		devs = new ArrayList<BluetoothDevice>();
		devs.addAll(btAdapter.getBondedDevices());
		Collections.sort(devs, new Comparator<BluetoothDevice>(){
			@Override
			public int compare(BluetoothDevice lhs, BluetoothDevice rhs) {
				return String.CASE_INSENSITIVE_ORDER.compare(lhs.getName(), rhs.getName());
			}});
		ArrayList<String> devLabels = new ArrayList<String>();
		for (BluetoothDevice d : devs) 
			devLabels.add(d.getName()+" ("+d.getAddress()+")");
		
		deviceSelectionAdapter = new ArrayAdapter<String>(this, 
				android.R.layout.simple_spinner_item, devLabels);
		deviceSelectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		deviceSpinner.setAdapter(deviceSelectionAdapter);
		String lastDev = options.getString(RemoteDevice.PREF_LAST_DEVICE, "(none)");
		
		for (int i = 0 ; i < devs.size() ; i++) {
			if (devs.get(i).getName().equals("RN42-A308")) {
				deviceSpinner.setSelection(i);
				break;
			}
		}
		
		for (int i = 0 ; i < devs.size() ; i++) {
			if (devs.get(i).getAddress().equals(lastDev))
				deviceSpinner.setSelection(i);
		} 
		
		deviceSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int position, long arg3) {
				options.edit().putString(RemoteDevice.PREF_LAST_DEVICE, devs.get(position).getAddress()).commit();				
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
				
			}
		});
		
		if (devs.size() == 0)
			message.setText("Bluetooth turned off or no devices paired.");

    }
}