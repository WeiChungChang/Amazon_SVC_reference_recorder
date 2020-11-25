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

public class HardwareEncoder implements Encoder {
	// Encoder API
    @Override
    public void create() {
        return;
    }
    @Override
    public void encode(byte[] frameData) {
        encodeFrame(frameData);
    }
    @Override
    public void stop() {
        stop_internal();
    }
    @Override
    public void configure(String fileName) {
        return;
    }
    @Override
    public void configureLegacy(int width, int height, int frameRate) {
        Log.d(TAG, "width " + width + " height " + height + " fps " + frameRate);
        createEncoder(width, height, frameRate);
        return;
    }

    protected MediaCodec m_codec = null;
    MediaCodec.BufferInfo m_info = new MediaCodec.BufferInfo();

    protected byte[] m_ColorConvertBuffer = null;

    protected static final String TAG = "zoom";
    protected static final boolean VERBOSE = false;

    FileOutputStream m_outputStream = null;

    FileOutputStream m_outputStreamYuv = null;

    long m_frameIdx = 0;

    int m_width;
    int m_height;
    int m_frameRate;

    protected int m_encodedSize = 0;

    Context m_Context = null;

    // ****   parameter settings

    //int m_i_frame_interval = 30*60*60*12;
    int m_i_frame_interval = 1;
    //int m_profile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;
    int m_profile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
   
    int m_bit_rate = 2000000;
    int m_bit_rate_mode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;

    int m_temporal_layers = 1;

    // ****  end of parameter settings


    public HardwareEncoder(Context c) {
        m_Context = c;
    }

    protected void createEncoder(int width, int height, int frameRate) {
        m_width = width;
        m_height = height;
        m_frameRate = frameRate;

        if (m_codec != null) {
            m_codec.stop();
            m_codec.release();
            m_codec = null;
        }
        try {
            m_codec = MediaCodec.createEncoderByType("video/avc");

            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MainActivity.MIME_TYPE, m_width, m_height);

            MediaCodecInfo codecInfo = m_codec.getCodecInfo();

            m_ColorConvertBuffer = new byte[width * height * 3 / 2];

            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
            mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, m_bit_rate_mode);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, m_bit_rate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, m_frameRate);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, m_i_frame_interval);

            mediaFormat.setInteger(MediaFormat.KEY_PROFILE, m_profile);
            mediaFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4);

            if (m_temporal_layers != 1)
            {
                mediaFormat.setString(MediaFormat.KEY_TEMPORAL_LAYERING, "android.generic." + m_temporal_layers);
            }

            //mediaFormat.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL);

            m_codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            m_codec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            m_outputStream = new FileOutputStream("/sdcard/zoom_hwencode_test_"+Integer.toString(m_bit_rate/1000)+".264");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    public void encodeFrame(byte[] frameData) {
        int TIMEOUT_USEC = -1;

        int inputBufIndex = m_codec.dequeueInputBuffer(TIMEOUT_USEC);
        if (inputBufIndex >= 0) {
            long ptsUsec = computePresentationTime(m_frameIdx);

            ByteBuffer inputBuf = m_codec.getInputBuffer(inputBufIndex);

            System.arraycopy(frameData, 0, m_ColorConvertBuffer, 0, m_width * m_height);

            // from nv21 to nv12
            for (int i = 0; i < m_height / 2; i++) {
                for (int j = 0; j < m_width / 2; j++) {
                    m_ColorConvertBuffer[m_width * m_height + i * m_width + j * 2 + 0] = frameData[m_width * m_height + i * m_width + j * 2 + 1];
                    m_ColorConvertBuffer[m_width * m_height + i * m_width + j * 2 + 1] = frameData[m_width * m_height + i * m_width + j * 2 + 0];
                }
            }

            inputBuf.clear();
            inputBuf.put(m_ColorConvertBuffer);

            m_codec.queueInputBuffer(inputBufIndex, 0, frameData.length, ptsUsec, 0);

            m_frameIdx++;
        } else {
            Log.d(TAG, "failed to get one input buffer");
        }


        while (true) {
            int encoderStatus = m_codec.dequeueOutputBuffer(m_info, TIMEOUT_USEC);

            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            } else if (encoderStatus < 0) {
                break;
            } else {
                ByteBuffer encodedData = m_codec.getOutputBuffer(encoderStatus);
                if (encodedData == null) {
                    Log.d(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                }

                encodedData.position(m_info.offset);
                encodedData.limit(m_info.offset + m_info.size);

                {
                    byte[] data = new byte[m_info.size];
                    encodedData.get(data);
                    encodedData.position(m_info.offset);
                    try {
                        m_outputStream.write(data);
                    } catch (IOException ioe) {
                        Log.w(TAG, "failed to write bitstream");
                        throw new RuntimeException(ioe);
                    }
                    Log.d(TAG, "write NAL sz %d" + m_info.size);
                }

                m_encodedSize += m_info.size;

                m_codec.releaseOutputBuffer(encoderStatus, false);

                if (m_info.flags != 2)
                {
                    break;
                }
            }
        }
    }

    public void stop_internal()
    {
        Log.d(TAG, "total frames: " + m_frameIdx);

        if (m_codec != null)
        {
            m_codec.stop();
            m_codec.release();
            m_codec = null;
        }
    }

    private long computePresentationTime(long frameIndex) {
        return frameIndex * 1000000 / m_frameRate;
    }
}
