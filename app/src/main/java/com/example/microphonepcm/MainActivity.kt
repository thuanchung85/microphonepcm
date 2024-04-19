package com.example.microphonepcm

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.microphonepcm.voice.Recorder
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private var mVoiceRecorder: Recorder? = null
    protected var mVoiceCallback: Recorder.Callback? = null


    lateinit var buttonStop:Button
    lateinit var buttonRecord:Button
    lateinit var buttonPlay:Button

    private var mediaPlayer: MediaPlayer? = null

//====================ON CREATE====================//
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        // Check if the permission has been granted already
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted, you can proceed with audio recording
            // Your audio recording logic here...

            // Check if the permission has been granted already
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                // Permission already granted, you can proceed with file writing
                // Your file writing logic here...
                buttonPlay = findViewById<Button>(R.id.buttonPlay);
                buttonPlay.visibility = GONE
                buttonPlay.setOnClickListener {
                    Log.d("CHUNG", "CHUNG buttonPlay.Click")
                    // Initialize the MediaPlayer with the WAV file
                    val filePath = mVoiceRecorder?.fileWAVPath // Replace this with your file path
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(filePath)
                        prepare()
                        start()
                    }

                }


                 buttonRecord = findViewById<Button>(R.id.buttonRecord);
                buttonRecord.setOnClickListener {
                    Log.d("CHUNG", "CHUNG buttonRecord.Click")
                    startVoiceRecorder()
                    it.visibility = GONE;

                    buttonPlay.visibility = GONE;
                }

                 buttonStop = findViewById<Button>(R.id.buttonStop);
                buttonStop.visibility = GONE;
                buttonStop.setOnClickListener {
                    Log.d("CHUNG", "CHUNG buttonStop.Click")
                    stopVoiceRecorder()
                    it.visibility = GONE;
                    buttonRecord.visibility = VISIBLE;
                    buttonPlay.visibility = VISIBLE;
                }



            } else {
                // Permission not yet granted, request it
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1002)
            }
        } else {
            // Permission not yet granted, request it
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }



    }

    override fun onDestroy() {
        super.onDestroy()
        // Release the MediaPlayer resources
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun startVoiceRecorder() {
        mVoiceRecorder = null
        Log.d("CHUNG", "CHUNG startVoiceRecorder")
        mVoiceCallback = object : Recorder.Callback() {

            override fun onListenStart() {
                super.onListenStart()
            }

            override fun onVoiceEnd() {
                super.onVoiceEnd()
                runOnUiThread(){
                    buttonStop.visibility = VISIBLE;
                }

            }

            override fun onListenEnd() {
                super.onListenEnd()

            }
        }


            if (mVoiceRecorder == null ) {
                try {
                    mVoiceRecorder = Recorder( mVoiceCallback as Recorder.Callback,this)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
                //mVoiceRecorder là object trong nó có chứa AudioRecorder, mà trong AudioRecorder lại có chưa buffer
                mVoiceRecorder!!.start()
            }

    }


    fun stopVoiceRecorder() {
        if (mVoiceRecorder != null) {

            var textView = findViewById<TextView>(R.id.textView)
            textView.text = mVoiceRecorder!!.fileWAVPath

            mVoiceRecorder!!.stop()
           // mVoiceRecorder = null


        }
    }
}