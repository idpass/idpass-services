package org.idpass.services;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import SecuGen.FDxSDKPro.JSGFPLib;
import SecuGen.FDxSDKPro.SGFDxDeviceName;
import SecuGen.FDxSDKPro.SGFDxErrorCode;
import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngWriter;
import fi.iki.elonen.NanoHTTPD;


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

public class SecuGenWebServer extends NanoHTTPD {
    private static final String TAG = "SecuGenWebServer";
    private JSGFPLib sgfplib;

    private int mImageWidth;
    private int mImageHeight;
    private int mImageDPI;

    private boolean bSecuGenDeviceOpened = false;

    /**
     * Constructs an HTTP server on given port.
     */
    public SecuGenWebServer(UsbManager usbManager) {
        super(8080);

        sgfplib = new JSGFPLib(usbManager);
    }


    @Override
    public Response serve(IHTTPSession session) {
        String path = session.getUri();

        try {
            try {
                if (path.equals("/open")) {
                    return openDevice();
                } else if (path.equals("/capture")) {
                    return capture();

                } else {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND,
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

                return newFixedLengthResponse(Response.Status.OK,
                        "application/json",
                        jsonResponse.toString());
            }

        } catch (JSONException e) {
            e.printStackTrace();
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "JSONException");
        }
    }




    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    private void debugMessage(String message) {
        Log.d(TAG, message);
    }


    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    public NanoHTTPD.Response capture() throws SecugenException, JSONException {
        JSONObject jsonResponse = new JSONObject();

        openDevice();

        long dwTimeStart = 0, dwTimeEnd = 0, dwTimeElapsed = 0;
        byte[] buffer = new byte[mImageWidth*mImageHeight];
        dwTimeStart = System.currentTimeMillis();
        long result = sgfplib.GetImageEx(buffer, 10000,50);

        if (result != SGFDxErrorCode.SGFDX_ERROR_NONE) {
            String message = "Error while getting image: " + String.valueOf(result);
            throw new SecugenException(result, message);
        }

        long nfiq = sgfplib.ComputeNFIQ(buffer, mImageWidth, mImageHeight);

        dwTimeEnd = System.currentTimeMillis();
        dwTimeElapsed = dwTimeEnd-dwTimeStart;
        debugMessage("getImageEx(10000,50) ret:" + result + " [" + dwTimeElapsed + "ms]" + nfiq +"\n");

        byte[] pngImage = toPNG(buffer, mImageWidth, mImageHeight);

        jsonResponse.put("image",
                "data:image/png;base64," + Base64.encodeToString(pngImage, Base64.NO_WRAP));
        jsonResponse.put("nfiq", nfiq);

        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                "application/json",
                jsonResponse.toString());
    }


    //////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////
    public Response openDevice() throws JSONException, SecugenException {
        JSONObject jsonResponse = new JSONObject();
        long error = sgfplib.Init( SGFDxDeviceName.SG_DEV_AUTO);
        if (error != SGFDxErrorCode.SGFDX_ERROR_NONE){
            if (error == SGFDxErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND) {
                String message = "The attached fingerprint device is not supported on Android";
                throw new SecugenException(message);
            } else {
                String message = "Fingerprint device initialization failed!";
                throw new SecugenException(message);
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
                    if (error == SGFDxErrorCode.SGFDX_ERROR_NONE)
                    {
                        bSecuGenDeviceOpened = true;
                        SecuGen.FDxSDKPro.SGDeviceInfoParam deviceInfo = new SecuGen.FDxSDKPro.SGDeviceInfoParam();
                        error = sgfplib.GetDeviceInfo(deviceInfo);
                        debugMessage("GetDeviceInfo() ret: " + error + "\n");
                        jsonResponse.put("deviceInfo", error);

                        mImageWidth = deviceInfo.imageWidth;
                        mImageHeight= deviceInfo.imageHeight;
                        mImageDPI = deviceInfo.imageDPI;
                        debugMessage("Image width: " + mImageWidth + "\n");
                        jsonResponse.put("imageWidth", mImageWidth);
                        debugMessage("Image height: " + mImageHeight + "\n");
                        jsonResponse.put("imageHeight", mImageHeight);
                        debugMessage("Image resolution: " + mImageDPI + "\n");
                        jsonResponse.put("imageDPI", mImageDPI);
                        debugMessage("Serial Number: " + new String(deviceInfo.deviceSN()) + "\n");
                        jsonResponse.put("deviceSN", new String(deviceInfo.deviceSN()));

                        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                                "application/json",
                                jsonResponse.toString());
                    }
                    else {
                        String message = "Waiting for USB Permission";
                        throw new SecugenException(message);
                    }
                }
            }
        }
    }

    public byte[] toPNG(byte[] imageData, int imageWidth, int imageHeight) {
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

}