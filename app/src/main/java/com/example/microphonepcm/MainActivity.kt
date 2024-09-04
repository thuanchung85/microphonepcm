package com.example.microphonepcm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.microphonepcm.OPENAI.AsyncTaskListener
import com.example.microphonepcm.OPENAI.OpenAIWhisperSTT
import com.example.microphonepcm.voice.Recorder
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity(), AsyncTaskListener {

    private var mVoiceRecorder: Recorder? = null
    protected var mVoiceCallback: Recorder.Callback? = null


    lateinit var buttonOKFileWAV:Button
    lateinit var buttonRecord:Button
    lateinit var buttonStop:Button
    lateinit var buttonPlay:Button

    private var mediaPlayer: MediaPlayer? = null

//====================ON CREATE====================//
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ///XIN QUYEN MICRO//nếu micro ok quyền rôi thi hỏi tiep vi tri nguoi dung. và quyền đọc ghi file
        if (ContextCompat.checkSelfPermission(this.applicationContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE), 1123)
        }
        else{
            initUI()
        }


    }//end onCreate

    override fun onDestroy() {
        super.onDestroy()
        // Release the MediaPlayer resources
        mediaPlayer?.release()
        mediaPlayer = null
    }

    //=========CALL BACK FUNCTION========//
    //==============KHI CẤP QUYỀN XONG==============//
    @CallSuper
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == 1123){
            initUI()
        }
    }




    //========PRIVATE FUN====//
    private fun initUI()
    {

        //từ android sdk 33 thì phải vào setting cấp quyền ghi file băng tay
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                val getpermission = Intent()
                getpermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(getpermission)
            }
        }

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
                buttonOKFileWAV.text = " "
            }

            //send to OPENAI
            sendToOpenAI()
        }

        //nut stop record wav
        buttonStop = findViewById<Button>(R.id.buttonStop);
        buttonStop.setOnClickListener {
            Log.d("CHUNG", "CHUNG buttonStop.Click")
            stopVoiceRecorder()

            buttonRecord.visibility = VISIBLE;
            buttonPlay.visibility = VISIBLE;
            buttonStop.visibility = GONE;
            buttonOKFileWAV.text = "WAV FILE DONE."
        }

        //nut record wav
        buttonRecord = findViewById<Button>(R.id.buttonRecord);
        buttonRecord.setOnClickListener {
            Log.d("CHUNG", "CHUNG buttonRecord.Click")
            startVoiceRecorder()
            it.visibility = GONE;
            buttonStop.visibility = VISIBLE;
            buttonOKFileWAV.visibility = GONE;
            buttonPlay.visibility = GONE;
            buttonOKFileWAV.text = "Please, talk."
        }

        //status
        buttonOKFileWAV = findViewById<Button>(R.id.buttonOKFileWAV);
        buttonOKFileWAV.visibility = GONE;


    }
    private fun startVoiceRecorder() {
        mVoiceRecorder = null
        Log.d("CHUNG", "CHUNG startVoiceRecorder")
        mVoiceCallback = object : Recorder.Callback() {

            override fun onListenStart() {
                super.onListenStart()
            }

            override fun onVoiceEnd() {
                super.onVoiceEnd()
                runOnUiThread(){
                    buttonOKFileWAV.visibility = VISIBLE;


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


    private fun stopVoiceRecorder() {
        if (mVoiceRecorder != null) {

            var textView = findViewById<TextView>(R.id.textView)
            textView.text = mVoiceRecorder!!.fileWAVPath

            mVoiceRecorder!!.stop()
           // mVoiceRecorder = null


        }
    }


    private fun sendToOpenAI(){
         val  openAIWhipper =  OpenAIWhisperSTT(this)
        if(mVoiceRecorder!!.fileWAVPath != null) {
            val recordedAudioFile = File(mVoiceRecorder!!.fileWAVPath)
            openAIWhipper.execute(recordedAudioFile);
        }
    }

    override fun onTaskComplete(result: String?) {
        Log.w("CHUNG","CHUNG onTaskComplete : " + result )
    }

}