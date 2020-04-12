package com.kivlin.tinytranslator;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final int CODE_DRAW_OVER_OTHER_APP_PERMISSION = 2084;
    private FloatingViewService mFloatingViewService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            //If the draw over permission is not available open the settings screen
            //to grant the permission.
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, CODE_DRAW_OVER_OTHER_APP_PERMISSION);
        } else {
            initializeView();
        }
    }

    private void initializeView() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ){
            startForegroundService(new Intent(MainActivity.this, FloatingViewService.class));
        }
        else {
            startService(new Intent(MainActivity.this, FloatingViewService.class));
        }
//        bindService(new Intent(this,
//                FloatingViewService.class), mConnection, Context.BIND_AUTO_CREATE);

        // finish();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mFloatingViewService = ((FloatingViewService.LocalBinder) iBinder).getService();
            // now you have the instance of service.
            Log.d("kivlin.lin", "service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mFloatingViewService = null;
            Log.d("kivlin.lin", "service disconnected");
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("kivlin.lin","Pause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mFloatingViewService != null) {
            // Detach the service connection.
            unbindService(mConnection);
        }
        Log.d("kivlin.lin","Destroy");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CODE_DRAW_OVER_OTHER_APP_PERMISSION) {
            //Check if the permission is granted or not.
            if (resultCode == RESULT_OK) {
                initializeView();
            } else { //Permission is not available
                Toast.makeText(this,
                        "Draw over other app permission not available. Closing the application",
                        Toast.LENGTH_SHORT).show();

                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
