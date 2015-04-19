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
		if (link == null)
			return;
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

		double outerWheelVelocity = 500 * Math.sqrt(drive * drive + rotate * rotate);
		if (outerWheelVelocity > 500.)
			outerWheelVelocity = 500.;
		double innerWheelVelocity = (1 - 2 * Math.abs(rotate)) * outerWheelVelocity;
		
		if (drive < 0) {
			innerWheelVelocity = -innerWheelVelocity;
		}
		
		int left;
		int right;
		
		if (rotate < 0) {
			left = (int)innerWheelVelocity;
			right = (int)outerWheelVelocity;
		}
		else {
			left = (int)outerWheelVelocity;
			right = (int)innerWheelVelocity;
		}
		
		Log.v("Roodrive", "left = "+left+" right = "+right);
		
		link.transmit(145, right >> 8, right & 0xFF, left >> 8, right & 0xFF);
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
		int vel = (int)(Math.abs(x) * 500);

		if (vel == 0)
			noDrive();
		else {
			int rot = x < 0 ? 1 : -1;
			link.transmit(137, vel >> 8, vel & 0xFF, rot >> 8, rot & 0xFF);
		}
	}

	@Override
	public void noDrive() {
		if (link == null)
			return;
		Log.v("Roodrive", "no drive");
		link.transmit(137, 0, 0, 0, 0);
	}

	@Override
	public boolean connect() {
		Log.v("Roodrive", "connect to Roomba");
		
		try {
			link = new BTDataLink(options.getString(RemoteDevice.PREF_BT_ADDRESS, "(none)"));
			link.transmit(128, 131);
			link.transmit(139, 0, 128, 255);
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
		
		if (link != null) {
			link.setOnDisconnectListener(null);
			link.transmit(133);
			link.stop(); 
			link = null;
		}
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
