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
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.view.*
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
    private lateinit var outputFile:File

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
        val anim = mView.findViewById<LottieAnimationView>(R.id.animation_view)
        anim.setAnimation(R.raw.mic)
        anim.playAnimation()

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
                    mView.findViewById<View>(R.id.window_close).setOnClickListener { stopAudioCapture()}
                    mView.findViewById<View>(R.id.start_recording).setOnClickListener { startAudioCapture()}
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

        // for android version >=O we need to create
        // custom notification stating
        // foreground service is running

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
            .setSampleRate(48000)
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

    private fun createAudioFile(type:Int): File {
        val audioCapturesDirectory = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "/AudioCaptures")
        if (!audioCapturesDirectory.exists()) {
            audioCapturesDirectory.mkdirs()
        }
        val timestamp = SimpleDateFormat("dd-MM-yyyy-hh-mm-ss", Locale.US).format(Date())
        val fileName = if(type==1) "Capture-$timestamp.pcm" else "Capture-$timestamp.wav"
        return File(audioCapturesDirectory.absolutePath + "/" + fileName)
    }

    private fun writeAudioToFile(outputFile: File) {
        val fileOutputStream = FileOutputStream(outputFile)
        val capturedAudioSamples = ShortArray(NUM_SAMPLES_PER_READ)

        while (!audioCaptureThread.isInterrupted) {
            audioRecord?.read(capturedAudioSamples, 0, NUM_SAMPLES_PER_READ)

            // This loop should be as fast as possible to avoid artifacts in the captured audio
            // You can uncomment the following line to see the capture samples but
            // that will incur a performance hit due to logging I/O.
            // Log.v(LOG_TAG, "Audio samples captured: ${capturedAudioSamples.toList()}")

            fileOutputStream.write(
                capturedAudioSamples.toByteArray(),
                0,
                BUFFER_SIZE_IN_BYTES
            )
        }

        fileOutputStream.close()
        Log.d(LOG_TAG, "Audio capture finished for ${outputFile.absolutePath}. File size is ${outputFile.length()} bytes.")
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

        try
        {
            PCMToWAV(outputFile,wavFile,1,48000,16)
        }catch (ioException:IOException) {
            Log.e("wav error",ioException.toString())
        }
        finally {
            stopSelf()
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

    @Throws(IOException::class)
    fun PCMToWAV(
        input: File,
        output: File?,
        channelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int,
    ){
        val inputSize = input.length().toInt()
        FileOutputStream(output).use { encoded ->
            // WAVE RIFF header
            writeToOutput(encoded, "RIFF") // chunk id
            writeToOutput(encoded, 36 + inputSize) // chunk size
            writeToOutput(encoded, "WAVE") // format

            // SUB CHUNK 1 (FORMAT)
            writeToOutput(encoded, "fmt ") // subchunk 1 id
            writeToOutput(encoded, 16) // subchunk 1 size
            writeToOutput(encoded, 1.toShort()) // audio format (1 = PCM)
            writeToOutput(encoded, channelCount.toShort()) // number of channelCount
            writeToOutput(encoded, sampleRate) // sample rate
            writeToOutput(encoded, sampleRate * channelCount * bitsPerSample / 8) // byte rate
            writeToOutput(encoded, (channelCount * bitsPerSample / 8).toShort()) // block align
            writeToOutput(encoded, bitsPerSample.toShort()) // bits per sample
            // SUB CHUNK 2 (AUDIO DATA)
            writeToOutput(encoded, "data") // subchunk 2 id
            writeToOutput(encoded, inputSize) // subchunk 2 size
            copy(FileInputStream(input), encoded)
        }
    }



    private val TRANSFER_BUFFER_SIZE = 10 * 1024
    @Throws(IOException::class)
    fun writeToOutput(output: OutputStream, data: String) {
        for (element in data) {
            output.write(element.toInt())
        }
    }

    @Throws(IOException::class)
    fun writeToOutput(output: OutputStream, data: Int) {
        output.write(data shr 0)
        output.write(data shr 8)
        output.write(data shr 16)
        output.write(data shr 24)
    }

    @Throws(IOException::class)
    fun writeToOutput(output: OutputStream, data: Short) {
        output.write(data.toInt() shr 0)
        output.write(data.toInt() shr 8)
    }

    @Throws(IOException::class)
    fun copy(source: InputStream, output: OutputStream): Long {
        return copy(source, output, TRANSFER_BUFFER_SIZE)
    }

    @Throws(IOException::class)
    fun copy(source: InputStream, output: OutputStream, bufferSize: Int): Long {
        var read = 0L
        val buffer = ByteArray(bufferSize)
        var n: Int
        while (source.read(buffer).also { n = it } != -1) {
            output.write(buffer, 0, n)
            read += n.toLong()
        }
        return read
    }
    }