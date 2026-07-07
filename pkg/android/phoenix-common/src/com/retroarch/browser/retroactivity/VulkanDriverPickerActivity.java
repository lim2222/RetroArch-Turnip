package com.retroarch.browser.retroactivity;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

/**
 * An independent helper Activity dedicated to launching the system file picker 
 * for selecting the Turnip driver ZIP.
 * 
 * Uses the standard launchMode (instead of singleInstance) to prevent the 
 * issue where RetroActivityFuture's singleInstance causes the 
 * startActivityForResult result to be lost.
 */
public class VulkanDriverPickerActivity extends Activity {

    private static final int REQ_PICK_ZIP = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PICK_ZIP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_PICK_ZIP) {
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    if (Build.VERSION.SDK_INT >= 19) {
                        getContentResolver().takePersistableUriPermission(uri,
                              data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                    }
                    SharedPreferences prefs = getSharedPreferences("retroarch_vulkan", Context.MODE_PRIVATE);
                    prefs.edit()
                         .putString("driver_zip_uri", uri.toString())
                         .putBoolean("user_reset_to_system", false)
                         .commit();

                    Toast.makeText(this,
                          "Turnip driver selected. Restarting RetroArch...",
                          Toast.LENGTH_SHORT).show();

                    restartApp();
                    return;
                } else {
                    Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Driver selection cancelled.", Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }

    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent == null) {
            finish();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        Runtime.getRuntime().exit(0);
    }
}
