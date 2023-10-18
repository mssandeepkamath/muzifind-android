package com.sandeep.music_recognizer_app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.skyfishjy.library.RippleBackground


class SplashActivity : AppCompatActivity() {

    private val AD_UNIT_ID = "ca-app-pub-5634416739025689/8429368052"
    private val LOG_TAG = "AppOpenAdManager"
    private lateinit var rippleView: RippleBackground

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        setContentView(R.layout.activity_splash)
        rippleView = findViewById<RippleBackground>(R.id.lytripple)
        rippleView.startRippleAnimation()
        loadAd()
    }

    fun loadAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this@SplashActivity,
            AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(LOG_TAG, adError.message)
                    Toast.makeText(
                        this@SplashActivity,
                        "Failed to load service, please reopen the app",
                        Toast.LENGTH_LONG
                    ).show()
                    val error =
                        "domain: ${adError.domain}, code: ${adError.code}, " + "message: ${adError.message}"
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(LOG_TAG, "Ad was loaded.")
                    val adManager = AdManager(ad, adRequest)
                    AdManagerSingleton.adManager = adManager
                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        )

    }
}