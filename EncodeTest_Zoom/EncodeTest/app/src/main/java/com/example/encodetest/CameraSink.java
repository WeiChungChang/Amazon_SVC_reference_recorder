package com.example.encodetest;

import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Message;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.IOException;

public class CameraSink extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback
{
    private SurfaceHolder mHolder = null;
    private Camera mCamera = null;
    private Context m_Context;

    private int width;
    private int height;
    private int frameRate;

    protected MainActivity.RecordThread m_recordThread = null;

    protected Encoder encoder = null;

    protected byte[] cacheData = null;

    public CameraSink(Context context)
    {
        super(context);

        m_Context = context;

        mHolder = this.getHolder();
        mHolder.addCallback(this);

        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public CameraSink(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        m_Context = context;

        mHolder = this.getHolder();
        mHolder.addCallback(this);

        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void setRecordThread(MainActivity.RecordThread thread)
    {
        m_recordThread = thread;
    }

    //public void setEncoder(HardwareEncoder e)
    public void setEncoder(Encoder e)
    {
        encoder = e;
    }


    public void surfaceChanged(SurfaceHolder holder, int format, int w,
                               int h) {
    }

    public void surfaceCreated(SurfaceHolder holder) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null)
        {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    public void startCapture(int w, int h, int f)
    {
        width = w;
        height = h;
        frameRate = f;

        cacheData = new byte[w * h * 3 / 2];

        int n = Camera.getNumberOfCameras();

        mCamera = Camera.open(0);
        try {
            mCamera.setPreviewDisplay(mHolder);

            Camera.Parameters parameters = mCamera.getParameters();

            parameters.setPreviewSize(w, h);
            parameters.setPreviewFrameRate(frameRate);

            mCamera.setPreviewCallback(this);

            mCamera.setParameters(parameters);
            mCamera.startPreview();
        } catch (IOException e) {
            mCamera.release();
        }
    }

    public void stopCapture()
    {
        mCamera.stopPreview();
        mCamera.setPreviewCallback(null);
        mCamera.release();
        mCamera = null;

        m_recordThread.getWorkHandler().sendEmptyMessage(MainActivity.RecordThread.MSG_END);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        int a = data.length;

        if (data.length > 0)
        {
            if (m_recordThread.getStatus())
            {
                m_recordThread.setStatus(false);

                System.arraycopy(data, 0, cacheData, 0, width*height*3/2);

                Message msg = new Message();
                Bundle bundle = new Bundle();
                bundle.putByteArray("data", cacheData);
                msg.what = MainActivity.RecordThread.MSG_FRAME;
                msg.setData(bundle);
                m_recordThread.getWorkHandler().sendMessage(msg);

            }
        }
    }
}

