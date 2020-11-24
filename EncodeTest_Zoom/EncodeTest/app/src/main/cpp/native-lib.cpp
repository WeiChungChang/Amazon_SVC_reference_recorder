#include <jni.h>
#include <string>
#include <codec_api.h>
#include <android/log.h>

static FILE* gFp;

int FillSpecificParameters (SEncParamExt& sParam, int w, int h, int tl, int sl, float fps, int idr) {
    /* Test for temporal, spatial, SNR scalability */
    sParam.iUsageType = CAMERA_VIDEO_REAL_TIME;
    sParam.fMaxFrameRate  = fps;                // input frame rate
    sParam.iPicWidth      = w;                 // width of picture in samples
    sParam.iPicHeight     = h;                  // height of picture in samples
    sParam.iTargetBitrate = 180000*10;              // target bitrate desired
    sParam.iMaxBitrate    = UNSPECIFIED_BIT_RATE;
    sParam.iRCMode        = RC_QUALITY_MODE;      //  rc mode control
    sParam.iTemporalLayerNum = tl;    // layer number at temporal level
    sParam.iSpatialLayerNum  = sl;    // layer number at spatial level
    sParam.bEnableDenoise    = 0;    // denoise control
    sParam.bEnableBackgroundDetection = 1; // background detection control
    sParam.bEnableAdaptiveQuant       = 1; // adaptive quantization control
    sParam.bEnableFrameSkip           = 1; // frame skipping
    sParam.bEnableLongTermReference   = 0; // long term reference control 
    sParam.iLtrMarkPeriod = 30;
    sParam.uiIntraPeriod  = idr;           // period of Intra frame
    sParam.eSpsPpsIdStrategy = INCREASING_ID;
    sParam.iComplexityMode = LOW_COMPLEXITY;
    sParam.bSimulcastAVC         = false;
    sParam.bPrefixNalAddingCtrl  = true;
    sParam.bPrefixNalAddingCtrl  = true;
    int iIndexLayer = 0;

    sParam.sSpatialLayers[iIndexLayer].uiProfileIdc       = PRO_SCALABLE_BASELINE;
    sParam.sSpatialLayers[iIndexLayer].iVideoWidth        = w;
    sParam.sSpatialLayers[iIndexLayer].iVideoHeight       = h;
    sParam.sSpatialLayers[iIndexLayer].fFrameRate         = fps;
    sParam.sSpatialLayers[iIndexLayer].iSpatialBitrate    = 180000*10;
    sParam.sSpatialLayers[iIndexLayer].iMaxSpatialBitrate = UNSPECIFIED_BIT_RATE;

    sParam.sSpatialLayers[iIndexLayer].sSliceArgument.uiSliceMode = SM_SINGLE_SLICE;
    sParam.sSpatialLayers[iIndexLayer].sSliceArgument.uiSliceNum = 1;

    float fMaxFr = sParam.sSpatialLayers[0].fFrameRate;
    sParam.fMaxFrameRate = fMaxFr;

    return 0;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_example_encodetest_SoftwareEncoder_nativeCreateEncoder(
        JNIEnv* env,
        jobject /* this */)
{
    ISVCEncoder *encoder_ = nullptr;
    WelsCreateSVCEncoder (&encoder_);
    gFp = fopen("/sdcard/sw.264","w+");
    return (jlong)encoder_;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_encodetest_SoftwareEncoder_configEncoder(
        JNIEnv* env,
        jobject /* this */,
        jlong pEncoder)
{
	ISVCEncoder *encoder_ = (ISVCEncoder*)pEncoder;
    SEncParamExt sSvcParam;
    encoder_->GetDefaultParams(&sSvcParam);
    FillSpecificParameters(sSvcParam, 640, 480, 1, 1, 30, 30);
    encoder_->InitializeExt (&sSvcParam);
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_encodetest_SoftwareEncoder_configEncoder2(
        JNIEnv* env,
        jobject /* this */,
        jlong pEncoder,
        jint width,
        jint height,
        jint temporalLayer,
        jint spatialLayer,
        jfloat fps,
        jint idrInterval)
{
	ISVCEncoder *encoder_ = (ISVCEncoder*)pEncoder;
    SEncParamExt sSvcParam;
    encoder_->GetDefaultParams(&sSvcParam);
    FillSpecificParameters(
        sSvcParam, width, height, temporalLayer,
        spatialLayer, fps, idrInterval);
    encoder_->InitializeExt (&sSvcParam);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_encodetest_SoftwareEncoder_encodeFrame(
        JNIEnv* env,
        jobject /* this */,
        jlong pEncoder,
        jbyteArray frame,
        jint width,
        jint height)
{
    jboolean isCopy;
    char* framePtr = NULL;
    framePtr= (char*)(env->GetByteArrayElements(frame, &isCopy));

    ISVCEncoder *encoder_ = (ISVCEncoder*)pEncoder;
    SFrameBSInfo info;
    SSourcePicture pic;
    //SFrameBSInfo info;
    memset (&info, 0, sizeof (SFrameBSInfo));
    //SSourcePicture pic;
    memset (&pic, 0, sizeof (SSourcePicture));
    pic.iPicWidth = width;
    pic.iPicHeight = height;
    pic.iColorFormat = videoFormatI420;
    pic.iStride[0] = width;
    pic.iStride[1] = pic.iStride[2] = width >> 1;
    pic.pData[0] = (unsigned char*)framePtr;
    pic.pData[1] = pic.pData[0] + width * height;
    pic.pData[2] = pic.pData[1] + (width * height >> 2);

    int rv = encoder_->EncodeFrame (&pic, &info);
    if(rv != 0)
        __android_log_assert("EncodeFrame failed", "JNI", nullptr);

    if (info.eFrameType != videoFrameTypeSkip /*&& cbk != nullptr*/) 
    {
        //output bitstream
        for (int iLayer=0; iLayer < info.iLayerNum; iLayer++)
        {
            SLayerBSInfo* pLayerBsInfo = &info.sLayerInfo[iLayer];
            unsigned char *outBuf = pLayerBsInfo->pBsBuf;
            int iLayerSize = 0;
            int offset = 0;
            //int iNalIdx = pLayerBsInfo->iNalCount - 1;
            int iNalIdx = 0;
            do {
                int type = outBuf[offset+4];
                int nal_ref_idc = (type >> 5) & 3;
                int nal_unit_type = type & 0x1f;

                iLayerSize += pLayerBsInfo->pNalLengthInByte[iNalIdx];
                offset += pLayerBsInfo->pNalLengthInByte[iNalIdx];
                ++iNalIdx;
                __android_log_print(ANDROID_LOG_VERBOSE, 
                    "openh264", "iLayerSize = %d, nal type %d, ref_idc = %d", 
                    iLayerSize, nal_unit_type, nal_ref_idc);
            } while (iNalIdx < pLayerBsInfo->iNalCount);
			{
				fwrite(outBuf, iLayerSize, 1, gFp);
				fflush(gFp);
			}
        }
    }

    env->ReleaseByteArrayElements(frame, (signed char*)framePtr, 0);
    return 0;
}    

extern "C"
JNIEXPORT void JNICALL
Java_com_example_encodetest_SoftwareEncoder_stop(
        JNIEnv* env,
        jobject /* this */,
        jlong pEncoder)
{
	ISVCEncoder *encoder_ = (ISVCEncoder*)pEncoder;
    if (encoder_) {
        encoder_->Uninitialize();
        WelsDestroySVCEncoder (encoder_);
    }
}
