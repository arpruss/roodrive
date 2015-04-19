package mobi.omegacentauri.roodrive;

import android.content.SharedPreferences;

abstract public class RemoteDevice {
	protected SharedPreferences options;
	abstract public void drive(float x, float y);
	abstract public void noDrive();
	abstract public boolean connect();
	abstract public void disconnect();
	abstract public void command(int... data);	

	public static final String PREF_BT_ADDRESS = "RemoteDevice_btAddress";
	public static final String PREF_LAST_DEVICE = "RemoteDevice_last";

	public RemoteDevice(SharedPreferences options) {
		this.options = options;
	}
}
