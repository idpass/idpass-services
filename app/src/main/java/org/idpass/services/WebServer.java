package org.idpass.services;


import android.hardware.usb.UsbManager;

import org.idpass.services.utils.RouterNanoHTTPD;

import java.io.IOException;

public class WebServer extends RouterNanoHTTPD {

    private static final int PORT = 8042;
    private UsbManager usbManager;

    /**
     * Create the server instance
     */
    public WebServer(UsbManager usbManager) throws IOException {
        super(PORT);
        this.usbManager = usbManager;
        addMappings();
    }

    /**
     * Add the routes Every route is an absolute path Parameters starts with ":"
     * Handler class should implement @UriResponder interface If the handler not
     * implement UriResponder interface - toString() is used
     */
    @Override
    public void addMappings() {
        super.addMappings();
        addRoute("/secugen/(.)*", SecuGenHandler.class, usbManager);
    }
}