package mobi.omegacentauri.roodrive;

import java.io.IOException;

import android.content.SharedPreferences;
import android.util.Log;

public class Roomba extends RemoteDevice {
	public static final int VACUUM = 0;
	float STRIP_HALF_WIDTH = 0.1f;
	DataLink link;
	
	public Roomba(SharedPreferences options) {
		super(options);
	}

	@Override
	public void drive(float x, float y) {
		if (-STRIP_HALF_WIDTH <= x && x <= STRIP_HALF_WIDTH) {
			drive(y);
			return;
		}
		else if (-STRIP_HALF_WIDTH <= y && y <= STRIP_HALF_WIDTH) {
			rotate(x);
		}
		else {
			drive(x);
			rotate(y);
		}
	}

	private void drive(float y) {
		Log.v("Roodrive", "drive "+y);
	}

	private void rotate(float x) {
		Log.v("Roodrive", "rotate "+x);
	}

	@Override
	public void noDrive() {
		Log.v("Roodrive", "no drive");
	}

	@Override
	public boolean connect() {
		Log.v("Roodrive", "connect to Roomba");
		
		try {
			link = new BTDataLink(options.getString(RemoteDevice.PREF_BT_ADDRESS, "(none)"));
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}

	@Override
	public void disconnect() {
		Log.v("Roodrive", "disconnect from Roomba");
		
		if (link != null)
			link.stop();
	}

	@Override
	public void command(int... data) {
		// TODO Auto-generated method stub
		
		Log.v("Roodrive", "command "+data[0]);
	}
}
