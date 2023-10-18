package com.sandeep.music_recognizer_app

import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.interstitial.InterstitialAd
import java.io.Serializable


data class AdManager(
    val interstitialAd: InterstitialAd,
    val adRequest: AdRequest
) : Serializable