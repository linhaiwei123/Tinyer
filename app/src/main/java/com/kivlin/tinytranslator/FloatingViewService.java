package com.kivlin.tinytranslator;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

interface ClipboardFunc {
    /** Invokes the function. */
    void invoke(String text);
}

public class FloatingViewService extends Service implements ClipboardFunc{
    private WindowManager mWindowManager;
    private View mFloatingView;
    private ClipboardManager mClipboardManager;
    Handler mHandler = null;
    Runnable mRunnable = null;
    RequestQueue mQueue;
    String mUrl = "https://fy.iciba.com/ajax.php?a=fy&f=zh-CN&t=en-US&w=";
    public static final String CHANNEL_ID = "FloatingViewServiceChannel";

    private int mPreAction = -1;

    private final IBinder mBinder = new LocalBinder();

    public FloatingViewService() {

    }

    public class LocalBinder extends Binder {
        FloatingViewService getService() {
            // Return this instance of LocalService so clients can call public methods
            return FloatingViewService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null);
        mHandler = new Handler();
        mQueue = Volley.newRequestQueue(this);

        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT
        );

        // params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = 0;
        params.y = 0;

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mFloatingView, params);

        final View collapsedView = mFloatingView.findViewById(R.id.collapse_view);
        final View expandedView = mFloatingView.findViewById(R.id.expanded_contanier);

        ImageView closeButton = (ImageView) mFloatingView.findViewById(R.id.search_btn_expanded);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                collapsedView.setVisibility(View.VISIBLE);
                expandedView.setVisibility(View.GONE);
            }
        });

        mFloatingView.findViewById(R.id.root_container).setOnTouchListener(new View.OnTouchListener() {

            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;


            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mPreAction = MotionEvent.ACTION_DOWN;
                        //remember the initial position.
                        initialX = params.x;
                        initialY = params.y;

                        //get the touch location
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        // int Xdiff = (int) (event.getRawX() - initialTouchX);
                        // int Ydiff = (int) (event.getRawY() - initialTouchY);


                        //The check for Xdiff <10 && YDiff< 10 because sometime elements moves a little while clicking.
                        //So that is click event.
                        // if (Xdiff < 1 && Ydiff < 1) {
                        if(mPreAction == MotionEvent.ACTION_DOWN) {
                            if (isViewCollapsed()) {
                                //When user clicks on the image view of the collapsed layout,
                                //visibility of the collapsed layout will be changed to "View.GONE"
                                //and expanded view will become visible.
                                collapsedView.setVisibility(View.GONE);
                                expandedView.setVisibility(View.VISIBLE);
                                getClipBoardText(FloatingViewService.this);
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        mPreAction = MotionEvent.ACTION_MOVE;
                        //Calculate the X and Y coordinates of the view.
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);


                        //Update the layout with new X & Y coordinate
                        mWindowManager.updateViewLayout(mFloatingView, params);
                        return true;
                }
                return false;
            }
        });

        mClipboardManager = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
        // mClipboardManager.addPrimaryClipChangedListener(mPrimaryChangeListener);
    }

    private void getClipBoardText(Service service) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && service != null) {
            getTextFromClipFromAndroidQ(service, (ClipboardFunc) service);
        } else {
            ((ClipboardFunc) service).invoke(getTextFromClip());
        }
    }

    /**
     * AndroidQ 获取剪贴板的内容
     */
    @TargetApi(Build.VERSION_CODES.Q)
    private void getTextFromClipFromAndroidQ(final Service service, final ClipboardFunc f) {
        mRunnable = new Runnable() {
            @Override
            public void run() {
                ClipboardManager clipboardManager =
                        (ClipboardManager)service.getSystemService(Context.CLIPBOARD_SERVICE);
                if (null == clipboardManager || !clipboardManager.hasPrimaryClip()) {
                    f.invoke("");
                    return;
                }
                ClipData clipData = clipboardManager.getPrimaryClip();
                if (null == clipData || clipData.getItemCount() < 1) {
                    f.invoke("");
                    return;
                }
                ClipData.Item item = clipData.getItemAt(0);
                if (item == null) {
                    f.invoke("");
                    return;
                }
                CharSequence clipText = item.getText();
                f.invoke(clipText.toString());
            }
        };
        mHandler.post(mRunnable);
    }

    private String getTextFromClip() {
        ClipboardManager clipboardManager =
                (ClipboardManager)this.getSystemService(Context.CLIPBOARD_SERVICE);
        if (null == clipboardManager || !clipboardManager.hasPrimaryClip()) {
            return "";
        }
        ClipData clipData = clipboardManager.getPrimaryClip();
        if (null == clipData || clipData.getItemCount() < 1) {
            return "";
        }
        ClipData.Item item = clipData.getItemAt(0);
        if (item == null)
            return "";
        CharSequence clipText = item.getText();
        return clipText.toString();

    }

    private boolean isViewCollapsed() {
        return mFloatingView == null || mFloatingView.findViewById(R.id.collapse_view).getVisibility() == View.VISIBLE;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Tinyer")
                .setContentText("tiny translator for pdf reading")
                .setSmallIcon(R.drawable.search)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mFloatingView != null) {
            mWindowManager.removeView(mFloatingView);
        }
        if(mRunnable != null) {
            mHandler.removeCallbacks(mRunnable);
        }
        Log.d("kivlin.lin","FloatingViewService destroyed");
    }

    @Override
    public void invoke(String text) {
        if(text != null) {
            translate(text);
        }
    }

    public String textPreprocess(String text) {
        String[] filters = new String[]{",",".",":",";"};
        for(String filter : filters) {
            int index = text.lastIndexOf(filter);
            if(index >= 0) {
                text = text.substring(0, index);
            }
            if(text.startsWith(filter))
            {
                text = text.substring(1,text.length());
            }
        }
        return text;
    }

    public void translate(String text) {
        text = textPreprocess(text);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, mUrl + text,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            present(response);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(FloatingViewService.this,error.toString(),Toast.LENGTH_SHORT);
            }
        });

// Add the request to the RequestQueue.
        mQueue.add(stringRequest);
    }

    public void present(String response) throws JSONException {
        // deserialize
        JSONObject responseObject = new JSONObject(response);
        JSONObject content = responseObject.optJSONObject("content");
        String[] data = {};
        if(content != null) {
            JSONArray word_mean = content.optJSONArray("word_mean");
            if (word_mean != null) {
                int length = word_mean.length();
                data = new String[length];
                for (int i = 0; i < length; i++) {
                    String string = word_mean.getString(i);
                    data[i] = string;
                }
            }
        }
        // display
        ListView listView = (ListView) mFloatingView.findViewById(R.id.list_view);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                FloatingViewService.this,
                R.layout.layout_list_view,
                data
        );
        listView.setAdapter(adapter);
        return;
    }
}
