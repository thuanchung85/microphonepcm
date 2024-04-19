/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyFile of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.microphonepcm.voice;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import static com.example.microphonepcm.voice.PCmTowav.PCMToWAV;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.example.microphonepcm.voice.Timer.Timer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;

public class Recorder {
    //private Global global;
    private int timerNumber = 0;
    private boolean isListening;
    private boolean isRecording;
    private static final int[] SAMPLE_RATE_CANDIDATES = new int[]{16000, 44100, 22050, 11025};
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final int MAX_AMPLITUDE_THRESHOLD = 15000;
    public static final int DEFAULT_AMPLITUDE_THRESHOLD = 2000; //original: 1500
    public static final int MIN_AMPLITUDE_THRESHOLD = 400;
    public static final int MAX_SPEECH_TIMEOUT_MILLIS = 5000;
    public static final int DEFAULT_SPEECH_TIMEOUT_MILLIS = 800; //original: 2000
    public static final int MIN_SPEECH_TIMEOUT_MILLIS = 100;
    public static final int MAX_PREV_VOICE_DURATION = 1500;
    public static final int DEFAULT_PREV_VOICE_DURATION = 800;
    public static final int MIN_PREV_VOICE_DURATION = 100;
    private static final int MAX_SPEECH_LENGTH_MILLIS = 29 * 1000; //original: 30 * 1000
    private Timer timer;
    private final Callback mCallback;
    private AudioRecord mAudioRecord;
    private Thread mThread;
    private int mPrevBufferMaxSize;
    private ArrayDeque<byte[]> mPrevBuffer;
    private byte[] mBuffer;
    /**
     * The timestamp of the last time that voice is heard.
     */
    private long mLastVoiceHeardMillis = Long.MAX_VALUE;
    /**
     * The timestamp when the current voice is started.
     */
    private long mVoiceStartedMillis;
    public FileOutputStream os = null;
    public String fileWAVPath = "";
    public Context myContext;

    public Recorder( @NonNull Callback callback, Context context) throws IOException {
        if (os != null) {
            os.close();
        }
        this.myContext = context;
        //this.global = global;
        //global.getMicSensitivity();
        //global.getSpeechTimeout();
        //global.getPrevVoiceDuration();
        mCallback = callback;
        mCallback.setRecorder(this);
        if (!(callback instanceof SimpleCallback)) {
            // used to not stop speech recognition before the 15 second step
            timer = new Timer(15000, 1000, new Timer.Callback() {
                @Override
                public void onTick(long millisUntilEnd) {
                    // it stops here eventually (and not in onEnd) to stop it before the 15 seconds have elapsed
                    if (millisUntilEnd <= 1000 && !isRecording) {
                        mCallback.onListenEnd();
                    }
                }

                @Override
                public void onEnd() {
                    // here we avoid that listening does not last longer than 4 intervals (therefore the 60 seconds limit), should not be confused with MAX_SPEECH_LENGTH_MILLIS because that represents the limit of voice, not of listen
                    timerNumber++;
                    if (timerNumber == 4) {
                        timerNumber = 0;
                        mCallback.onListenEnd();
                    } else {
                        timer.start();
                    }
                }
            });
        }
    }

    /**
     * Starts recording audio.
     *
     * <p>The caller is responsible for calling {@link #stop()} later.</p>
     */
    public void start() {

        // Stop recording if it is currently ongoing.
        stop();

        // Try to create a new recording session.
        mAudioRecord = createAudioRecord();
        if (mAudioRecord == null) {
            throw new RuntimeException("Cannot instantiate Recorder");
        }
        // Start recording.
        mAudioRecord.startRecording();  // here doesn't work with callback
        // Start processing the captured audio.
        mThread = new Thread(new ProcessVoice(), "processVoice");
        mThread.setPriority(Thread.MAX_PRIORITY);
        mThread.start();
    }

    /**
     * Stops recording audio.
     */
    public void stop() {
        if (mThread != null) {
            mThread.interrupt();
        }
        if (mAudioRecord != null) {
            mAudioRecord.stop();
            mAudioRecord.release();
            //mAudioRecord = null;
        }
        //mBuffer = null;
        dismiss();
        mCallback.onListenEnd();
    }

    /**
     * Dismisses the currently ongoing utterance.
     */
    public void dismiss() {  // that's why we always stop recognizing even when we have a final result
        if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            mCallback.onVoiceEnd();
        }
    }

    /**
     * Retrieves the sample rate currently used to record audio.
     *
     * @return The sample rate of recorded audio.
     */
    public int getSampleRate() {
        if (mAudioRecord != null) {
            return mAudioRecord.getSampleRate();
        }
        return 0;
    }

    /**
     * Creates a new {@link AudioRecord}.
     *
     * @return A newly created {@link AudioRecord}, or null if it cannot be created (missing
     * permissions?).
     */
    private AudioRecord createAudioRecord() {
        for (int sampleRate : SAMPLE_RATE_CANDIDATES) {
            final int sizeInBytes = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING);
            if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                continue;
            }
            final AudioRecord audioRecord;
            /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, sampleRate, CHANNEL, ENCODING, sizeInBytes);
            }else{
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, CHANNEL, ENCODING, sizeInBytes);
            }*/
            if (ActivityCompat.checkSelfPermission(myContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
               Log.d("CHUNG-", String.format("CHUNG- createAudioRecord -> error  Manifest.permission.RECORD_AUDIO"));
                return null;
            }
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, CHANNEL, ENCODING, sizeInBytes);
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                mBuffer = new byte[sizeInBytes * 2];  //attention here
                //mPrevBufferMaxSize = (int) Math.floor((((16f * sampleRate) / 8) * (((double)global.getPrevVoiceDuration()) /1000)) / mBuffer.length);
                mPrevBufferMaxSize = (int) Math.floor((((16f * sampleRate) / 8) * (((double)Recorder.DEFAULT_PREV_VOICE_DURATION) /1000)) / mBuffer.length);

                mPrevBuffer = new ArrayDeque<>();   // the prev buffer must contain PREV_VOICE_DURATION seconds of data prior to the buffer
                return audioRecord;
            } else {
                audioRecord.release();
            }
        }
        return null;
    }

    /**
     * Continuously processes the captured audio and notifies {@link #mCallback} of corresponding
     * events.
     * Always call the isHearing voice method and if it returns true and the time span from the last listening of the voice is greater than a tot (MAX_VALUE)
     * then call the onVoiceStarted method and then onVoice, otherwise only onVoice.
     */
    private class ProcessVoice implements Runnable {
        @Override
        public void run() {

                String OUTPUT_FILE = "CHUNGrecorded_audio.pcm";
                String OUTPUT_FILE_WAV = "CHUNGrecorded_audio.wav";
                File directory = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/YourAppName");
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                File outputFile;
                outputFile = new File(directory, OUTPUT_FILE);

                 File outputFileWav;
                outputFileWav = new File(directory, OUTPUT_FILE_WAV);
            fileWAVPath = outputFileWav.getPath();

                try {
                    Log.d("CHUNG-", String.format("CHUNG-  ProcessVoice outputFile.createNewFile()"));
                    outputFile.createNewFile();
                    outputFileWav.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                System.out.println(outputFile);


            try {
                os = new FileOutputStream(outputFile.getPath());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            //tạo 1 vòng lặp chay hoài trong 1 thread riêng
            while (!Thread.currentThread().isInterrupted()) {
                //bước 1 khai thác byte[] từ trong micro của phone ghi vào biến mBuffer
                //biến size này nó trả ra là 1920, và mBuffter có 1920 phần tử
                //size này có thể thay đổi tuỳ từng dong máy, như samsung là 2560, máy giả lập là 1920
                final int size = mAudioRecord.read(mBuffer, 0, mBuffer.length);
                Log.i("CHUNG-", "CHUNG-  ProcessVoice mAudioRecord.read " + size);



                //add biến mBuffer vaò array hàng đợi (mPrevBuffer)
                mPrevBuffer.addLast(mBuffer.clone());
                //nếu mà hàng đợi bị dài quá quy định thì cắt bỏ mBuffer đầu hàng, để dành chổ cho mBuffer tới sau.
                if (mPrevBuffer.size() > mPrevBufferMaxSize) {
                    mPrevBuffer.pollFirst();   // the excess buffer is eliminated, since the prevBuffer must only store the last buffers, the number is decided by prevBufferMaxSize
                }

                //ghi nhận thời gian hiện tai đang là bao nhiêu milliseconds, tính từ 0h 1/1/1970
                final long now = System.currentTimeMillis();

                //===nếu micro có voice data trong mBuffer===//
                if (isHearingVoice(mBuffer, size)) {
                    //nếu mà đang là trang thái dừng nghe, thi kich hoạt lại trạng thái nghe
                    if (mLastVoiceHeardMillis == Long.MAX_VALUE)
                    {    // use Long's maximum limit to indicate that we have no voice
                        mVoiceStartedMillis = now;
                        if (!isListening) {
                            //kích hoat lại trạng thái nghe
                            mCallback.onListenStart();
                        }

                        mCallback.onVoiceStart();
                        // we send the previous section (PREV_VOICE_DURATION seconds) when the voice is recognized
                        while (mPrevBuffer.size() > 0) {
                            //cắt phần tử đầu trong mPrevBuffer ra bằng pollFirst rồi bắn cái mBuffer đó vào callback
                            //hàm pollfirst vừa trả ra phần tữ đầu trong mPrevBuffer vừa làm mPrevBuffer giảm size
                            mCallback.onVoice(mPrevBuffer.pollFirst(), size);
                            //làm cho tới khi hết while

                            //===NEW==//

                            byte[] pcmData = mPrevBuffer.pollFirst();
                            try {
                                assert os != null;
                                if(pcmData != null) {
                                    os.write(pcmData);
                                }

                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                            //===NEW==//

                        }
                    } else {
                        //gởi buffer data co voice bên trong ra ngoài callback
                        mCallback.onVoice(mBuffer, size);

                        //===NEW==//
                        byte[] pcmData = mBuffer;
                        try {
                            assert os != null;
                            if(pcmData != null) {
                                os.write(pcmData);
                            }

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        //===NEW==//
                    }
                    mLastVoiceHeardMillis = now;

                    //nếu thơi gian hiên tai tính bằng millisecond - thơi gian bắt đầu nói mà vượt qua mức cho phép chờ lắng nghe thì coi như nói xong.
                    if (now - mVoiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS) {
                        end();
                        mCallback.onListenEnd();

                        File read = new File(outputFile.getPath());
                        File out = new File(outputFileWav.getPath());
                        try {

                            PCMToWAV(read, out, mAudioRecord.getChannelCount(), getSampleRate(), 16);

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                //===nếu micro KHONG CO voice data trong mBuffer===//
                else if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
                    mCallback.onVoice(mBuffer, size);
                    //===NEW==//
                    byte[] pcmData = mBuffer;
                    try {
                        assert os != null;
                        if(pcmData != null) {
                            os.write(pcmData);
                        }

                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    //===NEW==//

                    //if (now - mLastVoiceHeardMillis > global.getSpeechTimeout()) {
                    if (now - mLastVoiceHeardMillis >  Recorder.DEFAULT_SPEECH_TIMEOUT_MILLIS) {
                        end();

                        File read = new File(outputFile.getPath());
                        File out = new File(outputFileWav.getPath());
                        try {

                            PCMToWAV(read, out, mAudioRecord.getChannelCount(), getSampleRate(), 16);

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }

        private void end() {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            mCallback.onVoiceEnd();


        }

        private boolean isHearingVoice(byte[] buffer, int size) {
            //for duyệt qua cac buffer nằm ở vi trí chẵn
            for (int i = 0; i < size - 1; i += 2) {
                // The buffer has LINEAR16 in little endian.
                //Note that the default behavior of byte-to-int conversion is to preserve the sign of the value (remember byte is a signed type in Java). So for instance:
                //
                //byte b1 = -100;
                //int i1 = b1;
                //System.out.println(i1); // -100
                int s = buffer[i + 1];
                if (s < 0) s *= -1;
                //DỊCH 8 BIT QUA TRÁI NGHỈA LÀ S SẼ MŨ 8 LẦN LÊN
                s <<= 8;
                //đem s + với giá trị phần tữ trước đó, vi trí lẽ
                s += Math.abs(buffer[i]);
                //KHAI BÁO NGƯỠNG ÂM THANH, mà nếu vượt nguonng này thi coi như là có data voice thường là 2000
                //int amplitudeThreshold = global.getAmplitudeThreshold();
                int amplitudeThreshold = Recorder.DEFAULT_AMPLITUDE_THRESHOLD;
                //nếu s vượt ngưỡng âm thanh 2000 thì coi như có tiếng con người nói vào buffter
                if (s > amplitudeThreshold) {
                    return true;
                }
            }
            return false;
        }

    }



    //===========NEW====//


    public static abstract class Callback {
        private Recorder recorder;

        void setRecorder(Recorder recorder) {
            this.recorder = recorder;
        }


        public void onListenStart() {
            if (recorder != null) {
                recorder.timer.cancel();
                recorder.timer.start();
                recorder.isListening = true;
            }
        }

        /**
         * Called when the recorder starts hearing voice.
         */
        public void onVoiceStart() {
            if (recorder != null) {
                recorder.isRecording = true;
            }
        }

        /**
         * Called when the recorder is hearing voice.
         *
         * @param data The audio data in {@link AudioFormat#ENCODING_PCM_16BIT}.
         * @param size The peersSize of the actual data in {@code data}.
         */
        public void onVoice(@NonNull byte[] data, int size) {

        }

        /**
         * Called when the recorder stops hearing voice.
         */
        public void onVoiceEnd() {
            if (recorder != null) {
                recorder.isRecording = false;
                Log.d("CHUNG-", String.format("CHUNG- VOICE END -> onVoiceEnd " ));
            }
        }

        public void onListenEnd() {
            if (recorder != null) {
                if (recorder.isRecording) {
                    onVoiceEnd();
                }
                recorder.isListening = false;
                recorder.timer.cancel();
                Log.d("CHUNG-", String.format("CHUNG- LISTEN END -> onListenEnd " ));

            }
        }
    }

    public static abstract class SimpleCallback extends Callback {
        @Override
        void setRecorder(Recorder recorder) {
            super.setRecorder(recorder);
        }

        @Override
        public void onListenStart() {
        }

        @Override
        public void onVoiceStart() {
        }

        @Override
        public void onVoice(@NonNull byte[] data, int size) {
        }

        @Override
        public void onVoiceEnd() {
        }

        @Override
        public void onListenEnd() {
        }
    }





}
