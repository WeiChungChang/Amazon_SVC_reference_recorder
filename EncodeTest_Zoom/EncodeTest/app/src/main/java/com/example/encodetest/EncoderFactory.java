package com.example.encodetest;

import android.content.Context;

public class EncoderFactory {
    //use getShape method to get object of type shape
    Encoder m_sw = null;
    Encoder m_hw = null;

    private Context m_context;

    public EncoderFactory(Context c) {
        m_context = c;
    }

    public Encoder getEncoder(String encoderType){
        if(encoderType == null){
            return null;
        }
        if(encoderType.equalsIgnoreCase("HW")){
            if (m_sw == null) {
                m_sw = new SoftwareEncoder(m_context);
            }
            return m_sw;
        } else if(encoderType.equalsIgnoreCase("SW")){
            if (m_hw == null) {
                m_hw = new HardwareEncoder(m_context);
            }
            return m_hw;
        }
        return null;
    }
}

