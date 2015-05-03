package mobi.omegacentauri.roodrive;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.MotionEvent.PointerCoords;
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
	private static final String PREF_WINDOW_WIDTH = "joywindow.width";
	private static final String PREF_WINDOW_X = "joywindow.x";
	private static final String PREF_WINDOW_Y = "joywindow.y";
	private SharedPreferences options;
	private WindowManager wm;
	private RelativeLayout layout;
	private WindowManager.LayoutParams lp;
	private float moveStartX;
	private float moveStartY;
	private boolean moveMode;
	private ImageView joyCenter;
	private android.widget.RelativeLayout.LayoutParams joyCenterParams;
	private int origPosX;
	private int origPosY;
	private Roomba roomba;
	private boolean vacuum;
	private View lineView;
	private float curX;
	private float curY;
	private int origSize;
	private double origSeparation;
	private boolean zoomMode;

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
				final boolean connected = roomba.connect();

				Handler h = new Handler(Looper.getMainLooper());
				h.post(new Runnable(){

					@Override
					public void run() {
						if (! connected) {
							Toast.makeText(RoodriveService.this, "Unable to connect", Toast.LENGTH_LONG).show();
							Log.v("Roodrive", "Unable to connect");
							RoodriveService.this.stopSelf();
							return;
						}
//						Toast.makeText(RoodriveService.this, "Connected!", Toast.LENGTH_LONG).show();
						Log.v("Roodrive", "connected successfully");
						Toast.makeText(RoodriveService.this, "Connected", Toast.LENGTH_SHORT).show();
						layout.findViewById(R.id.connecting).setVisibility(View.GONE);
						roomba.setOnDisconnectListener(new DataLink.OnDisconnectListener() {
							@Override
							public void disconnected() {
								stopSelf();
							}
						});
					}});
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
		lp.x = options.getInt(PREF_WINDOW_X, 0);
		lp.y = options.getInt(PREF_WINDOW_Y, 0);
		lp.width = options.getInt(PREF_WINDOW_WIDTH, size/2);
		adjustWindow();
		
		layout = (RelativeLayout)li.inflate(R.layout.joystick, null);
		wm.addView(layout, lp);
		
		moveMode = false;

		joyCenter = new ImageView(this);
		Drawable d = getResources().getDrawable(R.drawable.controlpad);
		joyCenter.setImageDrawable(d);
		joyCenter.setVisibility(View.INVISIBLE);
		joyCenterParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);
		joyCenterParams.leftMargin = (lp.width - d.getIntrinsicWidth()) / 2;
		joyCenterParams.topMargin = (lp.height - d.getIntrinsicHeight()) / 2;

		lineView = new LineView();
		android.widget.RelativeLayout.LayoutParams lineViewParams = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT,LayoutParams.FILL_PARENT);
		
		layout.addView(joyCenter, joyCenterParams);
		layout.addView(lineView, lineViewParams);

		final View moveView = layout.findViewById(R.id.move);
		
		layout.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View arg0, MotionEvent motion) {
				Log.v("Roodrive","onTouch "+motion.getAction());
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
				roomba.reEnable();
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
			moveStartX = motion.getRawX();
			moveStartY = motion.getRawY();
			origPosX = lp.x;
			origPosY = lp.y;
			zoomMode = false;
		}
		else if (motion.getAction() == MotionEvent.ACTION_POINTER_2_DOWN) {
			zoomMode = true;
			origSize = lp.width;

			PointerCoords coords0 = new PointerCoords();
			PointerCoords coords1 = new PointerCoords();
			motion.getPointerCoords(0, coords0);
			motion.getPointerCoords(1, coords1);
			
			double dx = coords1.x - coords0.x;
			double dy = coords1.y - coords0.y;
			origSeparation = Math.sqrt(dx*dx + dy*dy);
			
			Log.v("Roodrive", "origSize="+origSize+" origSeparation="+origSeparation);
		}
		else if (motion.getAction() == MotionEvent.ACTION_MOVE) {
			if (motion.getPointerCount() > 1) {
				PointerCoords coords0 = new PointerCoords();
				PointerCoords coords1 = new PointerCoords();
				motion.getPointerCoords(0, coords0);
				motion.getPointerCoords(1, coords1);
				
				double dx = coords1.x - coords0.x;
				double dy = coords1.y - coords0.y;

				double dist = Math.sqrt(dx*dx+dy*dy);
				
				Log.v("Roodrive", "dist="+dist);

				if (origSeparation > 10 && dist > 10) {
					lp.width = (int) (dist / origSeparation * origSize);
				}
				
			}
			else if (! zoomMode) {
				
				float dx = motion.getRawX() - moveStartX;
				float dy = motion.getRawY() - moveStartY;
				
				lp.x = (int)(origPosX + dx);
				lp.y = (int)(origPosY + dy);
			}

			adjustWindow();
			
			wm.updateViewLayout(layout, lp);
		}
		else if (motion.getAction() == MotionEvent.ACTION_UP || motion.getAction() == MotionEvent.ACTION_POINTER_2_UP) {
			SharedPreferences.Editor ed = options.edit();
			ed.putInt(PREF_WINDOW_WIDTH, lp.width);
			ed.putInt(PREF_WINDOW_X, lp.x);
			ed.putInt(PREF_WINDOW_Y, lp.y);
			ed.commit();
		}
	}

	protected void adjustWindow() {
		DisplayMetrics dm = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(dm);

		if (lp.width > dm.widthPixels)
			lp.width = dm.widthPixels;
		if (lp.width > dm.heightPixels)
			lp.width = dm.heightPixels;
		if (lp.width < dm.widthPixels / 6)
			lp.width = dm.widthPixels / 6;
		if (lp.width < dm.heightPixels / 6)
			lp.width = dm.heightPixels / 6;

		lp.height = lp.width;

		if (lp.x < - dm.widthPixels / 2 + lp.width / 2)
			lp.x = - dm.widthPixels / 2 + lp.width / 2;
		if (lp.x > dm.widthPixels / 2 - lp.width / 2)
			lp.x = dm.widthPixels / 2 - 1 - lp.width / 2;
		if (lp.y < - dm.heightPixels / 2 + lp.height / 2)
			lp.y = - dm.heightPixels / 2 + lp.height / 2;
		if (lp.y > dm.heightPixels / 2 - lp.height / 2)
			lp.y = dm.heightPixels / 2 - 1 - lp.height / 2;
	}
	
	protected void joystickMove(MotionEvent motion) {
		roomba.reEnable();
		
		Log.v("Roodrive", "joystickMove "+motion);
		if (motion.getAction() == MotionEvent.ACTION_DOWN) {
			curX = moveStartX = motion.getX();
			curY = moveStartY = motion.getY();
		}
		
		if (motion.getAction() == MotionEvent.ACTION_DOWN || 
				motion.getAction() == MotionEvent.ACTION_MOVE) {

			float x = (int)motion.getX();
			float y = (int)motion.getY();
			
			curX = x;
			curY = y;
			
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
		}
		else if (motion.getAction() == MotionEvent.ACTION_UP) {
			roomba.noDrive();

			joyCenter.setVisibility(View.GONE);
		}
		
		lineView.invalidate();
	}
	
	class LineView extends View {
		private Paint paint;

		public LineView() {
			super(RoodriveService.this);
			paint = new Paint();
			paint.setColor(Color.WHITE);
			paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics()));
			paint.setAlpha(204);
		}
		
		@Override
		protected 
		void onDraw(Canvas c) {
			if (!moveMode && joyCenter.getVisibility() == View.VISIBLE) {
				Log.v("Roodrive", "line "+this.getWidth()+" "+this.getHeight()+" "+ moveStartX+" "+moveStartY+" "+curX+" "+curY);
				
				c.drawLine(moveStartX, moveStartY, curX, curY, paint);
			}
		}
	}
}
