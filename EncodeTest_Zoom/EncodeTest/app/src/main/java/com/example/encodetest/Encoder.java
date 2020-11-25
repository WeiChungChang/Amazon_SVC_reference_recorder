package com.example.encodetest;

public interface Encoder {
    void create();
    void encode(byte[] frameData);
    void stop();
    void configure(String fileName);
    
    // remove me once cofig file has been implemented.
    void configureLegacy(int width, int height, int frameRate);
}
