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
	
	/* can only be called after connection is established */
	public void setOnDisconnectListener(DataLink.OnDisconnectListener listener) {
		if (link != null)
			link.setOnDisconnectListener(listener);
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
			driveAndRotate(y, x);
		}
	}
	
	private void driveAndRotate(float drive, float rotate) {
		Log.v("Roodrive", "drive and rotate"+drive+" "+rotate);
		double speed = Math.sqrt(drive * drive + rotate * rotate);
		if (speed > 1)
			speed = 1;
		int vel;
		if (drive > 0)
			vel = (int)(speed * 500);
		else
			vel = -(int)(speed * 500);
		int rotRadius = (int) ((1-Math.abs(rotate)) * 2000);
		if (rotate > 0)
			rotRadius = -rotRadius;
		link.transmit(137, vel >> 8, vel & 0xFF, rotRadius >> 8, rotRadius & 0xFF);
	}

	private void drive(float y) {
		Log.v("Roodrive", "drive "+y);
		int vel = (int)(y * 500);
		if (vel == 0)
			noDrive();
		else
			link.transmit(137, vel >> 8, vel & 0xFF, 0x80, 0x00);
	}

	private void rotate(float x) {
		Log.v("Roodrive", "rotate "+x);
		int vel = (int)(x * 500);
		if (vel == 0)
			noDrive();
		else
			link.transmit(137, vel >> 8, vel & 0xFF, x < 0 ? 1 : -1);
	}

	@Override
	public void noDrive() {
		Log.v("Roodrive", "no drive");
		link.transmit(137, 0, 0, 0, 0);
	}

	@Override
	public boolean connect() {
		Log.v("Roodrive", "connect to Roomba");
		
		try {
			link = new BTDataLink(options.getString(RemoteDevice.PREF_BT_ADDRESS, "(none)"));
			link.transmit(128, 131);
			return true;
		}
		catch (Exception e) {
			if (link != null)
				link.stop();
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
		
		if (data[0] == VACUUM) {
			if (data[1] == 0) {
				link.transmit(138, 0);
			}
			else {
				link.transmit(138, 1 | 2 | 4);
			}
		}
	}
}
