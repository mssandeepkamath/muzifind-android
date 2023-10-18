package com.sandeep.music_recognizer_app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sandeep.music_recognizer_app.AdManagerSingleton.adManager
import com.sandeep.music_recognizer_app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(){

    private  var data: Intent? = null
    private val  LOG_TAG = "AppOpenAdManager"
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var isServiceStarted = false
    private lateinit var activityMainBinding : ActivityMainBinding
    val database = FirebaseDatabase.getInstance()
    private var interstitialAd: InterstitialAd? = null
    private var adRequest: AdRequest? = null
    val secretKeysRef = database.getReference("secret_keys")



    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null)
        val adManager = AdManagerSingleton.adManager
        activityMainBinding.btnCasting.setBackgroundColor(resources.getColor(R.color.purple_500))
        activityMainBinding.btnCasting.isEnabled = true
        activityMainBinding.btnCasting.text = "ALLOW CASTING PERMISSION"
        if (adManager != null) {
            interstitialAd = adManager.interstitialAd
            adRequest = adManager.adRequest
            activityMainBinding.adView.loadAd(adRequest!!)
        }
        if(checkOverlayPermission())
        {
            activityMainBinding.btnCasting.setOnClickListener {
                if(isRecordAudioPermissionGranted(this))
                {
                    activityMainBinding.btnCasting.setBackgroundColor(Color.GRAY)
                    activityMainBinding.btnCasting.isEnabled = false
                    activityMainBinding.btnCasting.text = "Switch to other apps!"
                    startCapturing(this)
                }else
                {
                    requestRecordAudioPermission()
                }

            }
        }
        else
        {
            val myIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(myIntent)
            Toast.makeText(this,"Reopen the app, once permitted",Toast.LENGTH_LONG).show()
            finish()
        }

        activityMainBinding.insta.setOnClickListener {
            val uri = Uri.parse("https://www.instagram.com/_mssandeep_kamath_")
            val likeIng = Intent(Intent.ACTION_VIEW, uri)
            likeIng.setPackage("com.instagram.android")

            try {
                startActivity(likeIng)
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.instagram.com/_mssandeep_kamath_")))
            }
        }
        activityMainBinding.git.setOnClickListener {
            var intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mssandeepkamath"))
            val packageManager = this.packageManager
            val list = packageManager?.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY)
            if (list != null) {
                if (list.isEmpty()) {
                    intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/mssandeepkamath"))
                }
            }
            startActivity(intent)
        }
        activityMainBinding.linked.setOnClickListener {
            var intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/m-s-sandeep-kamath-296913233/"))
            val packageManager = this.packageManager
            val list = packageManager?.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY)
            if (list != null) {
                if (list.isEmpty()) {
                    intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.linkedin.com/in/m-s-sandeep-kamath-296913233/"))
                }
            }
            startActivity(intent)
        }
        activityMainBinding.play.setOnClickListener {
            val intent=Intent(Intent.ACTION_VIEW)
            intent.data=Uri.parse("https://play.google.com/store/apps/dev?id=6781046200635814881&hl=en&gl=US")
            startActivity(intent)
        }
    }
    public fun startCapturing(context: Context) {
        if (!isRecordAudioPermissionGranted(context)) {
            requestRecordAudioPermission()
        } else {
            startMediaProjectionRequest()
        }
    }

    private fun startMediaProjectionRequest() {
        mediaProjectionManager =
            applicationContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            MEDIA_PROJECTION_REQUEST_CODE
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        isServiceStarted = true
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            MEDIA_PROJECTION_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    showInterstitial()
                    startOverlayService(data!!)
                } else {
                    activityMainBinding.btnCasting.setBackgroundColor(resources.getColor(R.color.purple_500))
                    activityMainBinding.btnCasting.isEnabled = true
                    activityMainBinding.btnCasting.text = "ALLOW CASTING PERMISSION"
                    Toast.makeText(this,"Screen casting permission request rejected!",Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // method for starting the service
    public  fun startOverlayService(data:Intent) {
            // start the service based on the android version
        Toast.makeText(this@MainActivity,"Starting service, Please wait",Toast.LENGTH_SHORT).show()
        secretKeysRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val accessKey = dataSnapshot.child("access_key").getValue(String::class.java)
                val host = dataSnapshot.child("host").getValue(String::class.java)
                val secretKey = dataSnapshot.child("secret_key").getValue(String::class.java)
                val serviceintent = Intent(this@MainActivity,ForegroundService::class.java).apply {
                    action = ForegroundService.ACTION_START
                    putExtra(ForegroundService.EXTRA_RESULT_DATA, data)
                    putExtra("host",host)
                    putExtra("accessKey",accessKey)
                    putExtra("secretKey",secretKey)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceintent)
                } else {
                    startService(serviceintent)
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@MainActivity,"Failed to start service",Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun checkOverlayPermission() : Boolean{
        return Settings.canDrawOverlays(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, ForegroundService::class.java))
    }


    companion object {
        private const val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 42
        private const val MEDIA_PROJECTION_REQUEST_CODE = 13
    }

    private fun requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_PERMISSION_REQUEST_CODE
        )
    }

    private fun isRecordAudioPermissionGranted(context:Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Permissions granted, click on Allow Casting now!",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this, "Permissions to capture audio denied.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showInterstitial() {
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback =
                object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(LOG_TAG, "Ad was dismissed.")
                        // Don't forget to set the ad reference to null so you
                        // don't show the ad a second time.
                        interstitialAd = null
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d(LOG_TAG, "Ad failed to show.")
                        interstitialAd = null
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d(LOG_TAG, "Ad showed fullscreen content.")

                    }
                }
            interstitialAd?.show(this)
        } else {

        }
    }




}