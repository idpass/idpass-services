package org.idpass.services;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Base64;
import android.util.Log;

import org.idpass.services.utils.RouterNanoHTTPD;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;

import SecuGen.FDxSDKPro.JSGFPLib;
import SecuGen.FDxSDKPro.SGFDxDeviceName;
import SecuGen.FDxSDKPro.SGFDxErrorCode;
import SecuGen.FDxSDKPro.SGFDxTemplateFormat;
import SecuGen.FDxSDKPro.SGFingerInfo;
import SecuGen.FDxSDKPro.SGImpressionType;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngWriter;
import fi.iki.elonen.NanoHTTPD;

import static SecuGen.FDxSDKPro.SGFDxTemplateFormat.TEMPLATE_FORMAT_ISO19794;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;


class SecugenException extends Exception
{
    public long code;
    public String message;

    public SecugenException(long code) {
        this.code = code;
        this.message = "";
    }

    public SecugenException(String message) {
        this.code = -1;
        this.message = message;
    }

    public SecugenException(long code, String message) {
        this.code = code;
        this.message = message;
    }
}

public class SecuGenHandler extends RouterNanoHTTPD.DefaultHandler {
    private static final String TAG = "SecuGenHandler";
    private boolean isDeviceOpened = false;

    private int mImageWidth;
    private int mImageHeight;
    private int mImageDPI;
//    private byte[] mRegisterImage;
//    private byte[] mVerifyImage;
//    private byte[] mRegisterTemplate;
//    private byte[] mVerifyTemplate;
    private int[] mMaxTemplateSize;

    @Override
    public String getText() {
        return "not implemented";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public NanoHTTPD.Response.IStatus getStatus() {
        return NanoHTTPD.Response.Status.OK;
    }

    public NanoHTTPD.Response get(RouterNanoHTTPD.UriResource uriResource, Map<String, String> urlParams, NanoHTTPD.IHTTPSession session) {
        String path = session.getUri();
        UsbManager usbManager = uriResource.initParameter(UsbManager.class);
        JSGFPLib sgfplib = new JSGFPLib(usbManager);

        try {
            try {
                if (path.equals("/secugen/open")) {
                    openDevice(sgfplib);
                    return deviceInfo(sgfplib);
                } else if (path.equals("/secugen/info")) {
                    return deviceInfo(sgfplib);
                } else if (path.equals("/secugen/capture")) {
                    return capture(sgfplib);

                } else {
                    return newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND,
                            NanoHTTPD.MIME_PLAINTEXT,
                            "Not Found");
                }
            } catch (SecugenException e) {
                Log.e(TAG, e.message);
                e.printStackTrace();

                JSONObject jsonResponse = new JSONObject();
                jsonResponse.put("error", 1);
                jsonResponse.put("errorCode", e.code);
                jsonResponse.put("errorMessage", e.message);

                return newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                        "application/json",
                        jsonResponse.toString());
            }

        } catch (JSONException e) {
            e.printStackTrace();
            return newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "JSONException");
        }

    }

    private void debugMessage(String message) {
        Log.w(TAG, message);
    }


    private NanoHTTPD.Response capture(JSGFPLib sgfplib) throws SecugenException, JSONException {
        JSONObject jsonResponse = new JSONObject();

        if (!isDeviceOpened) {
            openDevice(sgfplib);
        }

        long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;
        byte[] buffer = new byte[mImageWidth*mImageHeight];
        dwTimeStart = System.currentTimeMillis();
//        long result = sgfplib.GetImageEx(buffer, 10000,50);
        long result = sgfplib.GetImage(buffer);

        if (result != SGFDxErrorCode.SGFDX_ERROR_NONE) {
            String message = "Error while getting image: " + String.valueOf(result);
            throw new SecugenException(result, message);
        }

        long nfiq = sgfplib.ComputeNFIQ(buffer, mImageWidth, mImageHeight);

        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("getImageEx(10000,50) ret:" + result + " [" + dwTimeElapsed + "ms]" + nfiq +"\n");

        byte[] pngImage = toPNG(buffer, mImageWidth, mImageHeight);

//        jsonResponse.put("image",
//                "data:image/png;base64," + Base64.encodeToString(pngImage, Base64.NO_WRAP));
        jsonResponse.put("nfiq", nfiq);
        int quality = getQuality(sgfplib, buffer);
        jsonResponse.put("quality", quality);
//
        byte[] template = getTemplate(sgfplib, buffer, 0, quality);
        jsonResponse.put("template", Base64.encodeToString(template, Base64.NO_WRAP));


//        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
//                "image/png", new ByteArrayInputStream(pngImage), pngImage.length);
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                "application/json",
                jsonResponse.toString());
    }


    private void openDevice(JSGFPLib sgfplib) throws SecugenException {
        long error = sgfplib.Init(SGFDxDeviceName.SG_DEV_AUTO);
        isDeviceOpened = false;

        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE){
            if (error == SGFDxErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND) {
                String message = "The attached fingerprint device is not supported on Android";
                throw new SecugenException(error, message);
            } else {
                String message = "Fingerprint device initialization failed!";
                throw new SecugenException(error, message);
            }
        }
        else {
            UsbDevice usbDevice = sgfplib.GetUsbDevice();
            if (usbDevice == null) {
                String message = "SecuGen fingerprint sensor not found!";
                throw new SecugenException(message);
            }
            else {
                boolean hasPermission = sgfplib.GetUsbManager().hasPermission(usbDevice);
                if (!hasPermission) {
                    String message = "Missing USB Permission";
                    throw new SecugenException(message);
                }
                else {
                    debugMessage("Opening SecuGen Device\n");
                    error = sgfplib.OpenDevice(0);
                    debugMessage("OpenDevice() ret: " + error + "\n");
                    if (error == SGFDxErrorCode.SGFDX_ERROR_NONE) {
                        SecuGen.FDxSDKPro.SGDeviceInfoParam deviceInfo = new SecuGen.FDxSDKPro.SGDeviceInfoParam();

                        error = sgfplib.GetDeviceInfo(deviceInfo);
                        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE) {
                            String message = "Error while getting device info";
                            throw new SecugenException(error, message);
                        }

                        mImageWidth  = deviceInfo.imageWidth;
                        mImageHeight = deviceInfo.imageHeight;
                        mImageDPI    = deviceInfo.imageDPI;
                        debugMessage("Image width: " + mImageWidth + "\n");
                        debugMessage("Image height: " + mImageHeight + "\n");
                        debugMessage("Image resolution: " + mImageDPI + "\n");
                        debugMessage("Serial Number: " + new String(deviceInfo.deviceSN()) + "\n");

                        mMaxTemplateSize = new int[1];
                        sgfplib.SetTemplateFormat(SGFDxTemplateFormat.TEMPLATE_FORMAT_SG400);
                        sgfplib.GetMaxTemplateSize(mMaxTemplateSize);
                        debugMessage("TEMPLATE_FORMAT_SG400 SIZE: " + mMaxTemplateSize[0] + "\n");

                        isDeviceOpened = true;
                    } else {
                        String message = "Waiting for USB Permission";
                        throw new SecugenException(error, message);
                    }
                }
            }
        }
    }

    private NanoHTTPD.Response deviceInfo(JSGFPLib sgfplib) throws JSONException, SecugenException {
        JSONObject jsonResponse = new JSONObject();

        SecuGen.FDxSDKPro.SGDeviceInfoParam deviceInfo = new SecuGen.FDxSDKPro.SGDeviceInfoParam();
        long error = sgfplib.GetDeviceInfo(deviceInfo);

        debugMessage("GetDeviceInfo() ret: " + error + "\n");
        debugMessage("Image width: " + mImageWidth + "\n");
        debugMessage("Image height: " + mImageHeight + "\n");
        debugMessage("Image resolution: " + mImageDPI + "\n");
        debugMessage("Serial Number: " + new String(deviceInfo.deviceSN()) + "\n");

        jsonResponse.put("deviceInfo", error);
        jsonResponse.put("imageWidth", mImageWidth);
        jsonResponse.put("imageHeight", mImageHeight);
        jsonResponse.put("imageDPI", mImageDPI);
        jsonResponse.put("maxTemplateSize", mMaxTemplateSize[0]);
        jsonResponse.put("deviceSN", new String(deviceInfo.deviceSN()));

        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                "application/json",
                jsonResponse.toString());
    }

    private byte[] toPNG(byte[] imageData, int imageWidth, int imageHeight) {
        ImageInfo imi = new ImageInfo(imageWidth, imageHeight, 8, false, true, false);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PngWriter pngw = new PngWriter(stream, imi);

        for (int i = 0; i < imageHeight; i++) {
            int[] ints = new int[imageWidth];
            for (int j = 0; j < imageWidth; j++) {
                ints[j] = imageData[i * imageWidth + j];
            }
            pngw.writeRowInt(ints);
        }
        pngw.end();

        return stream.toByteArray();
    }

    private int getQuality(JSGFPLib sgfplib, byte[] rawImage) throws SecugenException {
        int[] imageQuality = {0};
        long error = sgfplib.GetImageQuality(mImageWidth, mImageHeight, rawImage, imageQuality);
        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE) {
            debugMessage("Error while getting image quality: " + String.valueOf(error));
            throw new SecugenException(error, "Error while getting image quality");
        }
        return imageQuality[0];
    }


    private byte[] getTemplate(JSGFPLib sgfplib, byte[] imageData, int fingerPosition, int imageQuality) throws SecugenException {
        int[] maxTemplateSize = {0};

        sgfplib.SetTemplateFormat(TEMPLATE_FORMAT_ISO19794);
        sgfplib.GetMaxTemplateSize(maxTemplateSize);

        SGFingerInfo fingerInfo = new SGFingerInfo();
        fingerInfo.FingerNumber = fingerPosition;
        fingerInfo.ImageQuality = imageQuality;
        fingerInfo.ImpressionType = SGImpressionType.SG_IMPTYPE_LP;
        fingerInfo.ViewNumber = 1;

        byte[] template = new byte[maxTemplateSize[0]];
        long error = sgfplib.CreateTemplate(fingerInfo, imageData, template);
        if (error != (int)SGFDxErrorCode.SGFDX_ERROR_NONE) {
            throw new SecugenException(error, "Error while getting image template");
        }

        int[] templateSize = {0};
        sgfplib.GetTemplateSize(template, templateSize);

        return Arrays.copyOf(template, templateSize[0]);
    }


}