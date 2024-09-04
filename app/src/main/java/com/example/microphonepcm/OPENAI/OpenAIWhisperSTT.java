package com.example.microphonepcm.OPENAI;

import android.os.AsyncTask;
import android.util.Log;

import com.example.microphonepcm.OPENAI.AsyncTaskListener;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenAIWhisperSTT extends AsyncTask<File, Void, String> {

    private static final String OPENAI_API_KEY = "";//"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";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/audio/transcriptions";

    public OpenAIWhisperSTT(AsyncTaskListener listener) {
        this.listener = listener;
    }
    public static void main(String[] args) {
        // Replace "audio.wav" with the path to your WAV audio file
        File audioFile = new File("audio.wav");

        try {
            String transcription = transcribeAudio(audioFile);
            System.out.println("Transcription: " + transcription);
        } catch (IOException e) {
            System.err.println("Error reading audio file: " + e.getMessage());
        }
    }

    public static String transcribeAudio(File audioFile) throws IOException {
        OkHttpClient client = new OkHttpClient();

        String currentlang = "en";
        currentlang = Locale.getDefault().getLanguage();
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("language", currentlang)
                .addFormDataPart("file", audioFile.getAbsolutePath(),
                        RequestBody.create(MediaType.parse("audio/wav"), audioFile))
                .build();

        Request request = new Request.Builder()

                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer " + OPENAI_API_KEY)
                .addHeader("Content-Type" , "multipart/form-data")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            String responseBody = response.body().string();
            return responseBody; // Response body contains the transcription
        }
    }



    private Exception exception;

    @Override
    protected String doInBackground(File... files) {
        try {
            String txt = transcribeAudio(files[0]);
            System.out.println(txt);
            return txt;
        }catch (IOException error){
            Log.d("CHUNG-", String.format("CHUNG- OpenAIWhisperSTT() -> IOException() " + error.getMessage()));
        }

        return null;
    }
    private AsyncTaskListener listener;
    @Override
    protected void onPostExecute(String result) {
        if (exception != null) {
            // Handle exception
            exception.printStackTrace();
        } else {
            // Process result

            Log.w("CHUNG"," WHISPER Transcription: " + result);
            if (listener != null) {
                listener.onTaskComplete(result);
            }
        }
    }
}

