package org.idpass.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

public class WebServerService extends Service {
    private static final String TAG = "WebServerService";
    private int mNotificationID = -1;

    SecuGenWebServer webServer;


    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    //This broadcast receiver is necessary to get user permissions to access the attached USB device
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //Log.d(TAG,"Enter mUsbReceiver.onReceive()");
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            debugMessage("USB BroadcastReceiver VID : " + device.getVendorId());
                            debugMessage("USB BroadcastReceiver PID: " + device.getProductId());
                        }
                        else
                            Log.e(TAG, "mUsbReceiver.onReceive() Device is null");
                    }
                    else
                        Log.e(TAG, "mUsbReceiver.onReceive() permission denied for device " + device);
                }
            }
        }
    };

    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    private void debugMessage(String message) {
        Log.d(TAG, message);
    }

    public WebServerService() {
    }

    private void showForegroundNotification(String contentText) {
        // Create intent that will bring our app to the front, as if it was tapped in the app
        // launcher
        Intent showTaskIntent = new Intent(getApplicationContext(), MainActivity.class);
        showTaskIntent.setAction(Intent.ACTION_MAIN);
        showTaskIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        showTaskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent contentIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                showTaskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(getApplicationContext())
                .setContentTitle(getString(R.string.app_name))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(contentIntent)
                .build();
        startForeground(1, notification);
    }

    private void showNotification(String message) {
        cancelNotification();
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
//                .setSmallIcon(R.drawable.notification_icon)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("ID PASS")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        Notification notification = mBuilder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

        startForeground(1, notification);
    }

    protected void cancelNotification() {
        if (mNotificationID != -1) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.cancel(mNotificationID);
            mNotificationID = -1;
        }
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "My Service Stopped", Toast.LENGTH_LONG).show();
        showNotification("Webservice Stopped");
        Log.d(TAG, "onDestroy");
        unregisterBroadcastReceiver();
        stopServer();
    }

    @Override
    public void onStart(Intent intent, int startid)
    {
        Toast.makeText(this, "My Service Started", Toast.LENGTH_LONG).show();
        Log.d(TAG, "onStart");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Toast.makeText(this, "service starting", Toast.LENGTH_LONG).show();

        registerBroadcastReceiver();

        startServer();

//        showForegroundNotification("Webservice Started");
        showNotification("Webservice Started");

        return START_STICKY;
    }

    private void startServer() {
        try
        {
            UsbManager usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
            webServer = new SecuGenWebServer(usbManager);
            webServer.start();
        }
        catch( IOException ioe )
        {
            System.err.println( "Couldn't start server:\n" + ioe );
            System.exit( -1 );
        }
        System.out.println( "Listening on port 8080. Hit Enter to stop.\n" );
        try { System.in.read(); } catch( Throwable t ) {
            System.out.println("read error");
        };
    }

    private void stopServer() {
        webServer.stop();
    }


    public void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
    }

    public void unregisterBroadcastReceiver() {
        unregisterReceiver(mUsbReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
