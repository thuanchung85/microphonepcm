package com.example.microphonepcm

import android.Manifest
import android.content.Context
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.CallSuper
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

    var modelfilename = ""
    var filePathBinFile =  ""
    private var whisperEngine: IWhisperEngine = WhisperEngine(this)

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

    //drop down menu choose language
    val spinner = findViewById<Spinner>(R.id.spinner)
    val items = arrayOf("English", "Viet Nam", "Korea")
    val adapter = ArrayAdapter<String>(
        this,
        android.R.layout.simple_spinner_dropdown_item,
        items
    )
    spinner.setAdapter(adapter)

    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            arg0: AdapterView<*>?,
            arg1: View?,
            arg2: Int,
            arg3: Long
        ) {
            // Do what you want
            val items = spinner.selectedItem.toString()
            Log.d("CHUNG", "CHUNG " + items)
            when (items) {
                "English" -> {
                    filePathBinFile = getFileFromAssets(this@MainActivity, "filters_vocab_en.bin").absolutePath
                    modelfilename = "whisper-tiny.en.tflite"
                }

                "Viet Nam" -> {
                    filePathBinFile = getFileFromAssets(this@MainActivity, "filters_vocab_vi.bin").absolutePath
                    modelfilename = "PhoWhisper-tiny.tflite"
                }
                "Korea" -> {
                    filePathBinFile = getFileFromAssets(this@MainActivity, "filters_vocab_en.bin").absolutePath
                    modelfilename = "whisper-tiny.en.tflite"
                }
            }
        }

        override fun onNothingSelected(arg0: AdapterView<*>?) {
            filePathBinFile = getFileFromAssets(this@MainActivity, "filters_vocab_en.bin").absolutePath
            modelfilename = "whisper-tiny.en.tflite"
        }
    }

}

    override fun onStart() {
        super.onStart()
        //từ android sdk 33 thì phải vào setting cấp quyền ghi file băng tay
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                val getpermission = Intent()
                getpermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(getpermission)
            }
        }

        ///XIN QUYEN MICRO//nếu micro ok quyền rôi thi hỏi tiep vi tri nguoi dung. và quyền đọc ghi file
        if (ContextCompat.checkSelfPermission(this.applicationContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1123)
        }
        else{
            initUI()
        }

    }


    @CallSuper
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == 1123){
            initUI()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        // Release the MediaPlayer resources
        mediaPlayer?.release()
        mediaPlayer = null
    }





    //======================CUSTOM FUNCTIONS================//

    fun initUI(){

        // Permission already granted, you can proceed with file writing
        // Your file writing logic here...
        buttonPlay = findViewById<Button>(R.id.buttonPlay);

        buttonPlay.setOnClickListener {
            Log.d("CHUNG", "CHUNG buttonPlay.Click")
            // Initialize the MediaPlayer with the WAV file
            val filePath = Environment.getExternalStorageDirectory().absolutePath + "/YourAppName/CHUNGrecorded_audio_NEW.wav"//mVoiceRecorder?.fileWAVPath // Replace this with your file path
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
            }
            buttonDich.visibility = VISIBLE
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
       // buttonDich.visibility = GONE
        buttonDich.setOnClickListener {
            Log.d("CHUNG", "CHUNG whisperEngine.CALL")

            //val filePathBinFile =  getFileFromAssets(this, "filters_vocab_en.bin").absolutePath


            //val testwavFile =  getFileFromAssets(this, "english_test1.wav").absolutePath


            var assetsManager = this.assets

            try {
                CoroutineScope(Dispatchers.Main).launch(Dispatchers.Default) {

                    try {
                        //init model file
                       // whisperEngine.initialize(assetsManager,  filePathBinFile, false, "whisper-tiny.en.tflite")

                        whisperEngine.initialize(assetsManager,  filePathBinFile, false, modelfilename)

                        //call model transcribeFile wav
                        val filePath = Environment.getExternalStorageDirectory().absolutePath + "/YourAppName/CHUNGrecorded_audio_NEW.wav"
                        var transcribedText= whisperEngine.transcribeFile(filePath)//(mVoiceRecorder?.fileWAVPath)
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

    }



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