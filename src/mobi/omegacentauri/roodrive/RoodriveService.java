package mobi.omegacentauri.roodrive;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class RoodriveService extends Service  {
	private SharedPreferences options;
	private WindowManager wm;
	private RelativeLayout layout;
	private WindowManager.LayoutParams lp;
	private RelativeLayout.LayoutParams fingertipParams;
	private ImageView fingertip;
	private float moveStartX;
	private float moveStartY;
	private boolean moveMode;
	private ImageView joyCenter;
	private android.widget.RelativeLayout.LayoutParams joyCenterParams;
	private int origPosX;
	private int origPosY;
	private Roomba roomba;
	private boolean vacuum;

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (layout != null) {
			try {
				wm.removeView(layout);
			}
			catch(Exception e){}
		}

		Log.v("Roodrive", "onDestroy()");

		if (roomba != null)
			roomba.disconnect();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);

		new Thread(new Runnable() {
			
			@Override
			public void run() {
				if (! roomba.connect()) {
//					Toast.makeText(RoodriveService.this, "Unable to connect", Toast.LENGTH_LONG).show();
					Log.v("Roodrive", "stopping");
					RoodriveService.this.stopSelf();
				}
//				Toast.makeText(RoodriveService.this, "Connected!", Toast.LENGTH_LONG).show();
				Log.v("Roodrive", "connected successfully");
				roomba.setOnDisconnectListener(new DataLink.OnDisconnectListener() {
					@Override
					public void disconnected() {
						stopSelf();
					}
				});
			}
		}).start();
		return START_NOT_STICKY;
		
		
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		Log.v("Roodrive", "service creating");
		options = PreferenceManager.getDefaultSharedPreferences(this);
		
		roomba = new Roomba(options);
		wm = (WindowManager)getSystemService(WINDOW_SERVICE);
		DisplayMetrics dm = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(dm);
		int size = dm.heightPixels < dm.widthPixels ? dm.heightPixels : dm.widthPixels;
		LayoutInflater li = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
		
		lp = new WindowManager.LayoutParams(size/2,size/2, WindowManager.LayoutParams.TYPE_SYSTEM_ERROR, 
				WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
				PixelFormat.TRANSLUCENT);
		
		layout = (RelativeLayout)li.inflate(R.layout.joystick, null);
		wm.addView(layout, lp);
		
		moveMode = false;

		fingertip = new ImageView(this);
		Drawable d = getResources().getDrawable(android.R.drawable.btn_star);
		fingertip.setImageDrawable(d);
		fingertip.setVisibility(View.INVISIBLE);
		fingertipParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);
		fingertipParams.leftMargin = (lp.width - d.getIntrinsicWidth()) / 2;
		fingertipParams.topMargin = (lp.height - d.getIntrinsicHeight()) / 2;

		joyCenter = new ImageView(this);
		d = getResources().getDrawable(android.R.drawable.ic_menu_compass);
		joyCenter.setImageDrawable(d);
		joyCenter.setVisibility(View.INVISIBLE);
		joyCenterParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);
		joyCenterParams.leftMargin = (lp.width - d.getIntrinsicWidth()) / 2;
		joyCenterParams.topMargin = (lp.height - d.getIntrinsicHeight()) / 2;

		layout.addView(joyCenter, joyCenterParams);
		layout.addView(fingertip, fingertipParams);

		final View moveView = layout.findViewById(R.id.move);
		
		layout.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View arg0, MotionEvent motion) {
				if (motion.getAction() == MotionEvent.ACTION_DOWN) {
					moveMode = moveView.getX() <= motion.getX() && 
							moveView.getY() <= motion.getY() &&
							motion.getX() <= moveView.getX() + moveView.getWidth() &&
							motion.getY() <= moveView.getY() + moveView.getHeight();
				}
				
				if (moveMode)
					viewMove(motion);
				else
					joystickMove(motion);
				return false;
			}});
		
		android.widget.Button b = (android.widget.Button)layout.findViewById(R.id.disconnect);
		b.setOnClickListener(new android.widget.Button.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Toast.makeText(RoodriveService.this, "Disconnecting", Toast.LENGTH_SHORT).show();
				wm.removeView(layout);
				stopSelf();
			}
		});
		
		vacuum = false;
		b = (android.widget.Button)layout.findViewById(R.id.vacuum);
		b.setOnClickListener(new android.widget.Button.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				vacuum = ! vacuum;
				roomba.command(Roomba.VACUUM, vacuum ? 1 : 0);
			}
		});
		
//		final android.widget.Button moveButton = (android.widget.Button)layout.findViewById(R.id.move);
//		moveButton.setOnClickListener(new android.widget.Button.OnClickListener() {
//			
//			@Override
//			public void onClick(View v) {
//				moveMode = !moveMode;
//				moveButton.setText(moveMode ? "Stop moving" : "Move");
//				stopSelf();
//			}
//		});
		
	}
	
	protected void viewMove(MotionEvent motion) {
		if (motion.getAction() == MotionEvent.ACTION_DOWN) {
			moveStartX = motion.getX();
			moveStartY = motion.getY();
			origPosX = lp.x;
			origPosY = lp.y;
		}
		else if (motion.getAction() == MotionEvent.ACTION_MOVE) {
			float dx = motion.getX() - moveStartX + lp.x - origPosX;
			float dy = motion.getY() - moveStartY + lp.y - origPosY;
			
			DisplayMetrics dm = new DisplayMetrics();
			wm.getDefaultDisplay().getMetrics(dm);

			lp.x = (int)(origPosX + dx);
			lp.y = (int)(origPosY + dy);
			if (lp.x < - dm.widthPixels / 2)
				lp.x = - dm.widthPixels / 2;
			if (lp.x > dm.widthPixels / 2)
				lp.x = dm.widthPixels / 2 - 1;
			if (lp.y < - dm.heightPixels / 2)
				lp.y = - dm.heightPixels / 2;
			if (lp.y > dm.heightPixels / 2)
				lp.y = dm.heightPixels / 2 - 1;
			
			wm.updateViewLayout(layout, lp);
		}
	}

	protected void joystickMove(MotionEvent motion) {
		Log.v("Roodrive", "joystickMove "+motion);
		if (motion.getAction() == MotionEvent.ACTION_DOWN) {
			moveStartX = motion.getX();
			moveStartY = motion.getY();
		}
		
		if (motion.getAction() == MotionEvent.ACTION_DOWN || 
				motion.getAction() == MotionEvent.ACTION_MOVE) {

			float x = (int)motion.getX();
			float y = (int)motion.getY();
			if (x < fingertip.getWidth() / 2)
				x = fingertip.getWidth() / 2;
			if (x >= layout.getWidth() - fingertip.getWidth() / 2)
				x = layout.getWidth() - fingertip.getWidth() / 2 - 1;
			if (y < fingertip.getHeight() / 2)
				y = fingertip.getHeight() / 2;
			if (y >= layout.getHeight() - fingertip.getHeight() / 2)
				y = layout.getHeight() - fingertip.getHeight() / 2 - 1;
			
			if (motion.getAction() == MotionEvent.ACTION_MOVE) {
				float dx = 2 * (motion.getX() - moveStartX) / layout.getWidth();
				float dy = 2 * (motion.getY() - moveStartY) / layout.getHeight();
				if (dx < -1f)
					dx = -1f;
				if (dy < -1f)
					dy = -1f;
				if (dx > 1f)
					dx = 1f;
				if (dy > 1f)
					dy = 1f;
				roomba.drive(dx, -dy);
			}
			else {
				joyCenterParams.leftMargin = (int)(x - joyCenter.getWidth() / 2);
				joyCenterParams.topMargin = (int)(y - joyCenter.getHeight() / 2);
				joyCenter.setLayoutParams(joyCenterParams);
				joyCenter.setVisibility(View.VISIBLE);
			}

			fingertipParams.leftMargin = (int) (x - fingertip.getWidth() / 2);
			fingertipParams.topMargin = (int) (y - fingertip.getHeight() / 2);
			fingertip.setLayoutParams(fingertipParams);
			fingertip.setVisibility(View.VISIBLE);
		}
		else if (motion.getAction() == MotionEvent.ACTION_UP) {
			roomba.noDrive();

			fingertip.setVisibility(View.GONE);
			joyCenter.setVisibility(View.GONE);
		}
	}
}
