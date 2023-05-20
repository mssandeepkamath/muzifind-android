package com.sandeep.music_recognizer_app

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.airbnb.lottie.LottieAnimationView
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.experimental.and
import kotlin.system.exitProcess


class ForegroundService : Service() {

    private lateinit var mView: View
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
        mView = layoutInflater.inflate(R.layout.popup_window, null)
        // set onClickListener on the remove button, which removes
        // the view from the window
        mWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mWindowManager.addView(mView, mParams)
        dragAndDrop()
        animationView = mView.findViewById(R.id.animation_view)
        statusText = mView.findViewById(R.id.status_text_view)
    }


    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        mWindowManager.removeView(mView)
        exitProcess(0)
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        return if (intent != null) {
            when (intent.action) {
                ACTION_START -> {
                    mediaProjection =
                        mediaProjectionManager.getMediaProjection(
                            Activity.RESULT_OK,
                            intent.getParcelableExtra(EXTRA_RESULT_DATA)!!
                        ) as MediaProjection
                    mView.findViewById<View>(R.id.start_recording).setOnClickListener {
                        startAudioCapture()
                        setAirbnbAnimation(R.raw.mic, R.string.listen)
                    }
                    mView.findViewById<View>(R.id.stop_recording).setOnClickListener {
                        setAirbnbAnimation(R.raw.process, R.string.process)
                        stopAudioCapture()
                    }
                    mView.findViewById<View>(R.id.close_window).setOnClickListener {
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
            .setContentTitle("Service running")
            .setContentText("Displaying over other apps") // this is important, otherwise the notification will show the way
            // you want i.e. it will show some default notification
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(SERVICE_ID, notification)
    }

    fun dragAndDrop() {
        mView.setOnTouchListener { v, event ->
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
                mWindowManager.updateViewLayout(mView, params);
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
                            AcrCloudRecognizer.configRecognizer(filePath)
                            return ""
                        }

                        override fun onPostExecute(result: String) {

                            Toast.makeText(this@ForegroundService, "API CALL SENT", Toast.LENGTH_LONG).show()
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



}