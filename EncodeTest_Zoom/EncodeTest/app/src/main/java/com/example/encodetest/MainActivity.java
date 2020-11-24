package com.example.encodetest;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.io.IOException;

public class MainActivity extends Activity {
    public final static String MIME_TYPE = "video/avc";
    private static final int PERMISSION_REQUEST_ALL = 0;

    public static int m_width = 1280;
    public static int m_height = 720;
    public static int m_frameRate = 30;

    private boolean m_encode_started = false;

    protected HardwareEncoder m_encoder = null;

    protected RecordThread m_recordThread = new RecordThread();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        ) {
            start_work();
        }
        else{
            askPermission();
        }
    }

    public void start_work()
    {
        m_recordThread.start();

        setContentView(R.layout.activity_main);

        ((Button)findViewById(R.id.startEncode)).setOnClickListener(new start_encode_test());

        m_encoder = new HardwareEncoder(this);

        ((CameraSink)findViewById(R.id.cameraView)).setEncoder(m_encoder);
    }

    public void askPermission() {
        String[] str = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA};

        ActivityCompat.requestPermissions(this, str, PERMISSION_REQUEST_ALL);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_ALL: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    start_work();
                } else {
                }
            }
        }
        return;
    }


    private class start_encode_test implements View.OnClickListener
    {
        @Override
        public void onClick(View v) {
            if (!m_encode_started)
            {
                m_recordThread.getWorkHandler().sendEmptyMessage(RecordThread.MSG_START);

                ((CameraSink)findViewById(R.id.cameraView)).setRecordThread(m_recordThread);

                ((CameraSink)findViewById(R.id.cameraView)).startCapture(m_width, m_height, m_frameRate);
                ((Button)findViewById(R.id.startEncode)).setText("stop");
                m_encode_started = true;
            }
            else
            {
                ((CameraSink)findViewById(R.id.cameraView)).stopCapture();

                ((Button)findViewById(R.id.startEncode)).setText("start");
                m_encode_started = false;
            }
        }
    }





    public class RecordThread extends HandlerThread implements Handler.Callback{
        private Handler mWorkerHandler;

        public static final int MSG_START = 0x100;
        public static final int MSG_FRAME = 0x101;
        public static final int MSG_END = 0x102;

        public Object lock = new Object();
        protected boolean m_ready = false;

        public boolean getStatus(){
            boolean b = false;
            synchronized (lock)
            {
                b = m_ready;
            }
            return b;
        }

        public void setStatus(boolean b){
            synchronized (lock)
            {
                m_ready = b;
            }
        }

        public RecordThread() {
            super("record");
        }

        @Override
        protected void onLooperPrepared() {
            mWorkerHandler = new Handler(getLooper(), this);
        }

        public Handler getWorkHandler(){
            return mWorkerHandler;
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what){
                case MSG_START:
//                    try {
//                        MediaCodec m_codec = MediaCodec.createEncoderByType("video/avc");
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
                    m_encoder.createEncoder(m_width, m_height, m_frameRate);
                    setStatus(true);
                    break;
                case MSG_FRAME:
                    byte[] frame = msg.getData().getByteArray("data");
                    m_encoder.encodeFrame(frame);
                    setStatus(true);
                    break;
                case MSG_END:
                    m_encoder.stop();
                    break;
            }
            return false;
        }
    }
}

