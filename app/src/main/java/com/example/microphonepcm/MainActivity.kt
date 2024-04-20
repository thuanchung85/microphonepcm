package com.example.microphonepcm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
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
import com.example.talkandexecute.whisperengine.IWhisperEngine
import com.example.talkandexecute.whisperengine.WhisperEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private var mVoiceRecorder: Recorder? = null
    protected var mVoiceCallback: Recorder.Callback? = null


    lateinit var buttonStop:Button
    lateinit var buttonRecord:Button
    lateinit var buttonPlay:Button
    lateinit var buttonDich:Button

    private var mediaPlayer: MediaPlayer? = null



    private var whisperEngine: IWhisperEngine = WhisperEngine(this)
    var testWavFilePath = "english_test1.wav";
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



                //nut call whisper dich file wav
                buttonDich = findViewById<Button>(R.id.buttonDich);
                buttonDich.setOnClickListener {
                    Log.d("CHUNG", "CHUNG whisperEngine.CALL")

                    val filePathModelAI_TFLITE_FILE =  getFileFromAssets(this, "whisper-tiny.en.tflite").absolutePath
                    println(filePathModelAI_TFLITE_FILE)

                    val filePathBinFile =  getFileFromAssets(this, "filters_vocab_en.bin").absolutePath
                    println(filePathBinFile)

                    val testwavFile =  getFileFromAssets(this, "english_test1.wav").absolutePath
                    println(testwavFile)

                    var F1 = File(filePathModelAI_TFLITE_FILE)
                    if(F1.exists() == true){
                        Log.d("CHUNG", "CHUNG F1.exists() == true")
                    }
                    var F2 = File(filePathBinFile)
                    if(F2.exists() == true){
                        Log.d("CHUNG", "CHUNG F2.exists() == true")
                    }
                    var F3 = File(testwavFile)
                    if(F3.exists() == true){
                        Log.d("CHUNG", "CHUNG F3.exists() == true")
                    }
                    var assetsManager = this.assets

                    try {
                        CoroutineScope(Dispatchers.Main).launch(Dispatchers.Default) {

                            try {


                                whisperEngine.initialize(assetsManager, F1.path, F2.path, false)
                                //var transcribedText= whisperEngine.transcribeFile(F3.absolutePath )
                                var transcribedText= whisperEngine.transcribeFile(mVoiceRecorder?.fileWAVPath)
                                Log.d("CHUNG", "CHUNG whisperEngine.CALL + " + transcribedText)
                                var textView = findViewById<TextView>(R.id.textView2)
                                textView.text = transcribedText

                            } catch (e:Exception) {
                                e.printStackTrace()
                            }


                        }
                    } catch (e: RuntimeException) {


                    } catch (e: IllegalStateException) {

                    }




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





    //======================CUSTOM FUNCTIONS================//
    @Throws(IOException::class)
    fun getFileFromAssets(context: Context, fileName: String): File = File(context.cacheDir, fileName)
        .also {
            if (!it.exists()) {
                it.outputStream().use { cache ->
                    context.assets.open(fileName).use { inputStream ->
                        inputStream.copyTo(cache)
                    }
                }
            }
        }


    private fun getFilePath(assetName: String): String {
        val outfile = File(filesDir, assetName)
        if (!outfile.exists()) {
            Log.d("CHUNG", "File not found - " + outfile.absolutePath)
        }

        Log.d("CHUNG", "Returned asset path: " + outfile.absolutePath)
        return outfile.absolutePath
    }




    //=================
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


    //======================CUSTOM FUNCTIONS================//
}