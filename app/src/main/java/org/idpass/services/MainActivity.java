package org.idpass.services;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toast.makeText(getBaseContext(), "Hello........", Toast.LENGTH_LONG).show();

        Context context = getBaseContext();
        Intent intent = new Intent(context, WebServerService.class);
        context.startService(intent);
        Log.i("Autostart", "started");
        Toast.makeText(getBaseContext(), "started....", Toast.LENGTH_LONG).show();
    }

}
