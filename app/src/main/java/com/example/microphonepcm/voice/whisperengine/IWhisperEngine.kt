package com.example.talkandexecute.whisperengine

import android.content.res.AssetManager
import java.io.IOException

interface IWhisperEngine {
    val isInitialized: Boolean

    @Throws(IOException::class)
    fun initialize(assetManager: AssetManager, vocabPath: String?, multilingual: Boolean, modelFile:String): Boolean
    fun transcribeFile(wavePath: String?): String
}
