package com.sandeep.music_recognizer_app



import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkOverlayPermission()
        startService()
    }

    // method for starting the service
    private fun startService() {
        // check if the user has already granted
        // the Draw over other apps permission
        if (Settings.canDrawOverlays(this)) {
            // start the service based on the android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, ForegroundService::class.java))
            } else {
                startService(Intent(this, ForegroundService::class.java))
            }
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            // send user to the device settings
            val myIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(myIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, ForegroundService::class.java))
    }


    override fun onResume() {
        super.onResume()
        startService()
    }


}