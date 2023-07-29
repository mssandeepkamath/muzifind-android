package com.sandeep.music_recognizer_app

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.airbnb.lottie.LottieAnimationView
import com.sandeep.music_recognizer_app.databinding.PopupWindowBinding
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.experimental.and
import kotlin.system.exitProcess


class ForegroundService : Service() {

    private lateinit var mParams: WindowManager.LayoutParams
    private lateinit var mWindowManager: WindowManager
    private lateinit var layoutInflater: LayoutInflater
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private lateinit var audioCaptureThread: Thread
    private var audioRecord: AudioRecord? = null
    private lateinit var outputFile: File
    private lateinit var animationView: LottieAnimationView
    private lateinit var statusText: TextView
    private lateinit var popupWindowBinding: PopupWindowBinding
    private var isStarted = false
    private var isStopped = false
    private lateinit var host:String
    private lateinit var accessKey:String
    private lateinit var secretKey:String


    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startMyOwnForeground() else startForeground(
            1,
            Notification()
        )
        // create the custom or default notification
        // based on the android version
        mediaProjectionManager =
            applicationContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        mParams.gravity = Gravity.CENTER or Gravity.TOP

        // getting a LayoutInflater
        layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        // inflating the view with the custom layout we created
        popupWindowBinding = PopupWindowBinding.inflate(layoutInflater)
        // set onClickListener on the remove button, which removes
        // the view from the window
        mWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mWindowManager.addView(popupWindowBinding.root, mParams)
        dragAndDrop()
        animationView = popupWindowBinding.animationView
        statusText = popupWindowBinding.statusTextView
        popupWindowBinding.resultWindow.alpha = 0f
        isStarted = false
        isStopped = false
    }


    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        mWindowManager.removeView(popupWindowBinding.root)
        exitProcess(0)
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        popupWindowBinding.stopRecording.isEnabled = false
        setAirbnbAnimation(R.raw.startup,R.string.start)
        return if (intent != null) {
            host = intent.getStringExtra("host")!!
            accessKey = intent.getStringExtra("accessKey")!!
            secretKey = intent.getStringExtra("secretKey")!!
            when (intent.action) {
                ACTION_START -> {
                    mediaProjection =
                        mediaProjectionManager.getMediaProjection(
                            Activity.RESULT_OK,
                            intent.getParcelableExtra(EXTRA_RESULT_DATA)!!
                        ) as MediaProjection
                    popupWindowBinding.startRecording.setOnClickListener {
                        popupWindowBinding.startRecording.colorFilter = ColorMatrixColorFilter(
                            ColorMatrix().apply { setSaturation(0f)})
                        popupWindowBinding.startRecording.isEnabled = false
                        popupWindowBinding.stopRecording.colorFilter = ColorMatrixColorFilter(
                            ColorMatrix().apply { setSaturation(1f)})
                        popupWindowBinding.stopRecording.isEnabled = true
                        startAudioCapture()
                        setAirbnbAnimation(R.raw.mic, R.string.listen)
                    }
                    popupWindowBinding.stopRecording.setOnClickListener {
                        popupWindowBinding.startRecording.colorFilter = ColorMatrixColorFilter(
                            ColorMatrix().apply { setSaturation(0f)})
                        popupWindowBinding.startRecording.isEnabled = false
                        popupWindowBinding.stopRecording.colorFilter = ColorMatrixColorFilter(
                            ColorMatrix().apply { setSaturation(0f)})
                        popupWindowBinding.stopRecording.isEnabled = false
                        setAirbnbAnimation(R.raw.process, R.string.process)
                        stopAudioCapture()
                    }
                    popupWindowBinding.closeWindow.setOnClickListener {
                        stopSelf()
                    }

                    START_STICKY
                }
                ACTION_STOP -> {
                    stopAudioCapture()
                    START_NOT_STICKY
                }
                else -> throw IllegalArgumentException("Unexpected action received: ${intent.action}")
            }
        } else {
            START_NOT_STICKY
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startMyOwnForeground() {
        val channelName = "Background Service"
        val chan = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_MIN
        )
        val manager =
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?)!!
        manager.createNotificationChannel(chan)
        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        val notification: Notification = notificationBuilder.setOngoing(true)
            .setContentTitle("Audio Recognizer service started..")
            .setContentText("Displaying over other apps for music recognition!") // this is important, otherwise the notification will show the way
            // you want i.e. it will show some default notification
            .setSmallIcon(R.drawable.mu)
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(SERVICE_ID, notification)
    }

    fun dragAndDrop() {
        popupWindowBinding.root.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
                var xOffset = v.width / 2
                var yOffset = v.height / 2
                var x = event.rawX - xOffset
                var y = event.rawY - yOffset
                var params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    x.toInt(), y.toInt(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.TOP or Gravity.START
                mWindowManager.updateViewLayout(popupWindowBinding.root, params);
                return@setOnTouchListener true
            }
            return@setOnTouchListener false
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startAudioCapture() {

        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        /**
         * Using hardcoded values for the audio format, Mono PCM samples with a sample rate of 8000Hz
         * These can be changed according to your application's needs
         */
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(44100)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(BUFFER_SIZE_IN_BYTES)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        audioRecord!!.startRecording()

        audioCaptureThread = thread(start = true) {
            outputFile = createAudioFile(1)
            Log.d(LOG_TAG, "Created file for capture target: ${outputFile.absolutePath}")
            writeAudioToFile(outputFile)
        }
    }

    private fun createAudioFile(type: Int): File {
        val audioCapturesDirectory =
            File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "/AudioCaptures")
        if (!audioCapturesDirectory.exists()) {
            audioCapturesDirectory.mkdirs()
        }
        val timestamp = SimpleDateFormat("dd-MM-yyyy-hh-mm-ss", Locale.US).format(Date())
        val fileName = if (type == 1) "Capture-$timestamp.pcm" else "Capture-$timestamp.wav"
        return File(audioCapturesDirectory.absolutePath + "/" + fileName)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun writeAudioToFile(outputFile: File) {
        val fileOutputStream = FileOutputStream(outputFile)
        val capturedAudioSamples = ShortArray(NUM_SAMPLES_PER_READ)


        while (!audioCaptureThread.isInterrupted) {
            audioRecord?.read(capturedAudioSamples, 0, NUM_SAMPLES_PER_READ)

            fileOutputStream.write(
                capturedAudioSamples.toByteArray(),
                0,
                BUFFER_SIZE_IN_BYTES
            )
        }

        fileOutputStream.close()
        Log.d(
            LOG_TAG,
            "Audio capture finished for ${outputFile.absolutePath}. File size is ${outputFile.length()} bytes."
        )


    }


    private fun stopAudioCapture() {
        requireNotNull(mediaProjection) { "Tried to stop audio capture, but there was no ongoing capture in place!" }
        val wavFile = createAudioFile(2)
        audioCaptureThread.interrupt()
        audioCaptureThread.join()
        audioRecord!!.stop()
        audioRecord!!.release()
        audioRecord = null
        mediaProjection!!.stop()
        try {
            convertPcmToWav(outputFile.absolutePath, wavFile.absolutePath)
        } catch (ioException: IOException) {
            Log.e("wav error", ioException.toString())
        } finally {
            Handler().postDelayed(
                {
                    setAirbnbAnimation(R.raw.search, R.string.search)
                    statusText.text = resources.getString(R.string.search)

                    // Execute API call in background thread using AsyncTask
                    object : AsyncTask<String, Void, String>() {
                        override fun doInBackground(vararg params: String): String {
                            val filePath = params[0]
                            val response = AcrCloudRecognizer.configRecognizer(filePath,host,accessKey,secretKey)
                            return response!!
                        }

                        override fun onPostExecute(result: String) {
                            Log.d("response",result)
                            popupWindowBinding.startRecording.colorFilter = ColorMatrixColorFilter(
                                ColorMatrix().apply { setSaturation(0f)})
                            popupWindowBinding.startRecording.isEnabled = false
                            popupWindowBinding.stopRecording.colorFilter = ColorMatrixColorFilter(
                                ColorMatrix().apply { setSaturation(0f)})
                            popupWindowBinding.stopRecording.isEnabled = false

                            try {
                                val jsonResponse = JSONObject(result)
                                val msg = jsonResponse.getJSONObject("status").getString("msg")
                                if (!msg.equals("Success")) {
                                    popupWindowBinding.statusTextView.setTextColor(resources.getColor(R.color.red))
                                    setAirbnbAnimation(R.raw.failed,R.string.sorry)
                                } else {
                                    var spotifyAlbumId: String? = null
                                    var deezerAlbumId: String? = null
                                    var youtubeVideoId: String? = null
                                    var title:String? = null
                                    var label:String? = null
                                    var album:String? = null

                                    val musicArray = jsonResponse.getJSONObject("metadata").getJSONArray("music")
                                    if (musicArray.length() > 0) {
                                        val musicObject = musicArray.getJSONObject(0)
                                         label = musicObject.getString("label")
                                         title = musicObject.getString("title")
                                         album = musicObject.getJSONObject("album").getString("name")

                                        val externalMetadata = musicObject.optJSONObject("external_metadata")
                                        if (externalMetadata != null) {
                                            val spotifyMetadata = externalMetadata.optJSONObject("spotify")
                                            if (spotifyMetadata != null) {
                                                val spotifyAlbum = spotifyMetadata.optJSONObject("track")
                                                spotifyAlbumId = spotifyAlbum?.getString("id")
                                            }

                                            val deezerMetadata = externalMetadata.optJSONObject("deezer")
                                            if (deezerMetadata != null) {
                                                val deezerAlbum = deezerMetadata.optJSONObject("track")
                                                deezerAlbumId = deezerAlbum?.getString("id")
                                            }

                                            val youtubeMetadata = externalMetadata.optJSONObject("youtube")
                                            if (youtubeMetadata != null) {
                                                youtubeVideoId = youtubeMetadata.getString("vid")
                                            }

                                        }

                                    }
                                    popupWindowBinding.animWindow.alpha = 0f
                                    popupWindowBinding.resultWindow.alpha = 1f
                                    popupWindowBinding.statusTextView.text = "Success, here are the results!"
                                    if(spotifyAlbumId == null) {
                                        popupWindowBinding.spotifyResult.colorFilter = ColorMatrixColorFilter(
                                            ColorMatrix().apply { setSaturation(0f)})
                                        popupWindowBinding.spotifyResult.isEnabled = false
                                    }
                                    if(youtubeVideoId == null) {
                                        popupWindowBinding.youtubeResult.colorFilter = ColorMatrixColorFilter(
                                            ColorMatrix().apply { setSaturation(0f)})
                                        popupWindowBinding.youtubeResult.isEnabled = false
                                    }

                                    if(title == null) {
                                        popupWindowBinding.googleResult.colorFilter = ColorMatrixColorFilter(
                                            ColorMatrix().apply { setSaturation(0f)})
                                        popupWindowBinding.googleResult.isEnabled = false
                                    }

                                    popupWindowBinding.googleResult.setOnClickListener {
                                        val query = "$title, $album by $label song"
                                        val url = "https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
                                        val intent = Intent(Intent.ACTION_VIEW)
                                        intent.data = Uri.parse(url)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        startActivity(intent)
                                    }

                                    popupWindowBinding.spotifyResult.setOnClickListener {

                                        val uri = "spotify:track:$spotifyAlbumId"

                                        // Create an intent for the Spotify app
                                        val appIntent = Intent(Intent.ACTION_VIEW)
                                        appIntent.data = Uri.parse(uri)
                                        appIntent.`package` = "com.spotify.music" // Package name of the Spotify app
                                        appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        // Create an intent for the Spotify web URL
                                        val webIntent = Intent(Intent.ACTION_VIEW)
                                        webIntent.data = Uri.parse("https://open.spotify.com/track/${spotifyAlbumId}")
                                        webIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        // Check if there is an app available to handle the intent
                                        if (appIntent.resolveActivity(packageManager) != null) {
                                            startActivity(appIntent)
                                        } else {
                                            // Spotify app is not installed, fallback to the web URL
                                            startActivity(webIntent)
                                        }
                                    }

                                    popupWindowBinding.youtubeResult.setOnClickListener {

                                        val appIntent = Intent(Intent.ACTION_VIEW)
                                        appIntent.data = Uri.parse("vnd.youtube:$youtubeVideoId")
                                        appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                                        val webIntent = Intent(Intent.ACTION_VIEW)
                                        webIntent.data = Uri.parse("https://www.youtube.com/watch?v=$youtubeVideoId")
                                        webIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                                        if (appIntent.resolveActivity(packageManager) != null) {
                                            startActivity(appIntent)
                                        } else {
                                            startActivity(webIntent)
                                        }
                                    }
                                }
                            } catch (e: JSONException) {
                                Log.e("Errror", e.toString())
                                Toast.makeText(this@ForegroundService,"Service is no more available! please try again later..",Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.execute(wavFile.absolutePath)

                }, 2000
            )
        }

    }


    override fun onBind(p0: Intent?): IBinder? = null

    private fun ShortArray.toByteArray(): ByteArray {
        // Samples get translated into bytes following little-endianness:
        // least significant byte first and the most significant byte last
        val bytes = ByteArray(size * 2)
        for (i in 0 until size) {
            bytes[i * 2] = (this[i] and 0x00FF).toByte()
            bytes[i * 2 + 1] = (this[i].toInt() shr 8).toByte()
            this[i] = 0
        }
        return bytes
    }

    private fun setAirbnbAnimation(animId: Int, textId: Int) {
        animationView.setAnimation(animId)
        animationView.playAnimation()
        statusText.text = resources.getString(textId)
    }


    fun convertPcmToWav(pcmFilePath: String, wavFilePath: String) {

        val bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val pcmData = ByteArray(bufferSize)

        try {
            val pcmFile = File(pcmFilePath)
            val wavFile = File(wavFilePath)

            val pcmInputStream = FileInputStream(pcmFile)
            val wavOutputStream = FileOutputStream(wavFile)

            // Write WAV header
            writeWavHeader(wavOutputStream, pcmFile.length())

            // Copy PCM data to WAV file
            val dataInputStream = DataInputStream(pcmInputStream)
            val dataOutputStream = DataOutputStream(wavOutputStream)

            while (dataInputStream.read(pcmData) != -1) {
                dataOutputStream.write(pcmData)
            }

            // Close streams
            dataOutputStream.close()
            dataInputStream.close()
            pcmInputStream.close()
            wavOutputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    @Throws(IOException::class)
    fun writeWavHeader(outputStream: OutputStream, pcmSize: Long) {
        val totalAudioLen = pcmSize + 36
        val totalDataLen = pcmSize

        val channels = 1
        val byteRate = 44100 * 2 * channels.toLong()

        val header = ByteArray(44)

        // RIFF header
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalAudioLen and 0xff).toByte()
        header[5] = (totalAudioLen shr 8 and 0xff).toByte()
        header[6] = (totalAudioLen shr 16 and 0xff).toByte()
        header[7] = (totalAudioLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()

        // FMT sub-chunk
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16 // Sub-chunk size (16 for PCM)
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Audio format (1 for PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (44100 and 0xff).toByte()
        header[25] = (44100 shr 8 and 0xff).toByte()
        header[26] = (44100 shr 16 and 0xff).toByte()
        header[27] = (44100 shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (2 * 16 / 8).toByte() // Block align
        header[33] = 0
        header[34] = 16 // Bits per sample
        header[35] = 0

        // Data sub-chunk
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalDataLen and 0xff).toByte()
        header[41] = (totalDataLen shr 8 and 0xff).toByte()
        header[42] = (totalDataLen shr 16 and 0xff).toByte()
        header[43] = (totalDataLen shr 24 and 0xff).toByte()

        outputStream.write(header)
    }

    companion object {
        private const val LOG_TAG = "AudioCaptureService"
        private const val SERVICE_ID = 123
        private const val NOTIFICATION_CHANNEL_ID = "AudioCapture channel"

        private const val NUM_SAMPLES_PER_READ = 1024
        private const val BYTES_PER_SAMPLE = 2 // 2 bytes since we hardcoded the PCM 16-bit format
        private const val BUFFER_SIZE_IN_BYTES = NUM_SAMPLES_PER_READ * BYTES_PER_SAMPLE

        const val ACTION_START = "AudioCaptureService:Start"
        const val ACTION_STOP = "AudioCaptureService:Stop"
        const val EXTRA_RESULT_DATA = "AudioCaptureService:Extra:ResultData"
    }

    private fun reopenApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        val stackBuilder = TaskStackBuilder.create(this)
        stackBuilder.addNextIntentWithParentStack(intent)
        stackBuilder.startActivities()
    }



}