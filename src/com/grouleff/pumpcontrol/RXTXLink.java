package com.grouleff.pumpcontrol;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RXTXLink  {

    public static int BPS = 115200;
    protected SerialPort serialPort;
    protected final String portName;

    public RXTXLink(String portName) {
        this.portName = portName;
    }

    public void ensureConnected() {
        if (serialPort == null) {
            try {
                connect(portName);
            } catch (Exception e) {
                ensureSocketClosed();
                e.printStackTrace();
            }
        }
    }

    public OutputStream getOutputStream() throws IOException {
        ensureConnected();
        return serialPort.getOutputStream();
    }

    public InputStream getInputStream() throws IOException {
        ensureConnected();
        return  serialPort.getInputStream();
    }

    private void ensureSocketClosed() {
        if (serialPort != null) {
            System.err.println("Lost connection to Serial Port");
            serialPort.close();
        }
        serialPort = null;
    }

    private void connect(String portName) throws Exception {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if (portIdentifier.isCurrentlyOwned()) {
            System.err.println("Error: Port is currently in use");
        } else {
            CommPort commPort = portIdentifier.open(this.getClass().getName(), 2000);

            if (commPort instanceof SerialPort) {
                serialPort = (SerialPort) commPort;
                initCommPort();
            } else {
                System.err.println("Error: Only serial ports!");
            }
        }
    }

    protected void initCommPort() throws UnsupportedCommOperationException {
        serialPort.setSerialPortParams(BPS, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
        serialPort.setInputBufferSize(500);
        serialPort.enableReceiveTimeout(MI301DongleProxy.SO_TIMEOUT);
        serialPort.disableReceiveFraming();
    }
}
