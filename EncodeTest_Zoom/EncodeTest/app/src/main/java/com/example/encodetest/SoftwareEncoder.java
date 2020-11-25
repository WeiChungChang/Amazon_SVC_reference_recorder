package com.example.encodetest;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class SoftwareEncoder implements Encoder {
    // Encoder APIs
    @Override
    public void create() {
        return;
    }

    @Override
    public void encode(byte[] frameData) {
        System.arraycopy(frameData, 0, m_ColorConvertBuffer, 0, m_width * m_height);
        // from nv21 to nv12
        for (int i = 0; i < m_height / 2; i++) {
            for (int j = 0; j < m_width / 2; j++) {
                m_ColorConvertBuffer[m_width * m_height + i * m_width + j * 2 + 0] = frameData[m_width * m_height + i * m_width + j * 2 + 1];
                m_ColorConvertBuffer[m_width * m_height + i * m_width + j * 2 + 1] = frameData[m_width * m_height + i * m_width + j * 2 + 0];
            }
        }
        encodeFrame(m_pEncoder, m_ColorConvertBuffer, m_width, m_height);
    }

    // native APIs
    @Override
    public void stop() {
        stop(m_pEncoder);
     }

    @Override
    public void configure(String fileName) {
		// TO BE DONE
        return;
    }

    @Override
    public void configureLegacy(int width, int height, int frameRate) {
        createEncoder(width, height, frameRate);
        return;
    }

    public native String configEncoder(long pEncoder);

    public native int configEncoder2(long pEncoder,
        int width, int height, int temporalLayer,
        int spatialLayer, float fps, int idrInterval);

    public native int encodeFrame(long pEncoder,
        byte[] frame, int width, int height);

    public native long nativeCreateEncoder();
    
    public native void stop(long pEncoder);
    
    static {
        System.loadLibrary("native-lib");
    }


    protected byte[] m_ColorConvertBuffer = null;

    protected static final String TAG = "openh264";
    protected static final boolean VERBOSE = false;

    FileOutputStream m_outputStream = null;

    FileOutputStream m_outputStreamYuv = null;

    long m_frameIdx = 0;

    int m_width;
    int m_height;
    int m_frameRate;

    long m_pEncoder;

    protected int m_encodedSize = 0;
    
    
    protected boolean first = true;

    Context m_Context = null;

    int m_i_frame_interval = 1;
    int m_profile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;

    int m_bit_rate = 2000000;
    int m_bit_rate_mode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;

    int m_temporal_layers = 1;

    public SoftwareEncoder(Context c) {
        m_Context = c;
    }

    protected void createEncoder(
            int width, 
            int height, 
            int frameRate,
            int temporalLayer,
            int spatialLayer,
            int idrInterval
            )
    {
        m_pEncoder = nativeCreateEncoder();
        m_width = width;
        m_height = height;
        m_frameRate = frameRate;

        configEncoder(m_pEncoder);

        try {
            m_outputStream = new FileOutputStream("/sdcard/OpenH264_ref"
                + Integer.toString(m_bit_rate/1000) + ".264");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        m_ColorConvertBuffer = new byte[width * height * 3 / 2];
    }

    protected void createEncoder(int width, int height, int frameRate) {
        m_width     = width;
        m_height    = height;
        m_frameRate = frameRate;
        createEncoder(
                width,
                height,
                1,
                1,
                m_frameRate,
                30
                );
    }

    public void encodeFrame(byte[] frameData) {
        System.arraycopy(frameData, 0, m_ColorConvertBuffer, 0, m_width * m_height);

        // from nv21 to nv12
        int checksum = 0;
        for (int i = 0; i < m_height / 2; i++) {
            for (int j = 0; j < m_width / 2; j++) {
                m_ColorConvertBuffer[m_width * m_height + i * m_width + j * 2 + 0] = frameData[m_width * m_height + i * m_width + j * 2 + 1];
                m_ColorConvertBuffer[m_width * m_height + i * m_width + j * 2 + 1] = frameData[m_width * m_height + i * m_width + j * 2 + 0];
            }
        }
        Log.d(TAG, "encode frame! ");
        encodeFrame(m_pEncoder, m_ColorConvertBuffer, m_width, m_height);
    }
}
