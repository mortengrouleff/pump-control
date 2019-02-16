package com.grouleff.pumpcontrol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;

public class Client extends Thread {

	private static final int REVC_PKG_TIMEOUT = 2000;
    private static final long IDLE_TIME_OUT = 60 * 1000;
    private final Socket clientSocket;
    private RXTXLink dongle;

	public Client(Socket clientSocket, String name) {
	    super(name);
		this.clientSocket = clientSocket;
		setDaemon(true);
		start();
	}

	@Override
	public void run() {
		try {
	        clientSocket.setSoTimeout(MI301DongleProxy.SO_TIMEOUT);
	        dongle = MI301DongleProxy.getLink(); // throws if it fails...
	        resetDongle();
	        long lastUse = System.currentTimeMillis();
			while (true) {
				int len = handle();
				if (len > 0) {
				    lastUse = System.currentTimeMillis();
				}
				if (System.currentTimeMillis() - lastUse > IDLE_TIME_OUT) {
				    return;
				}
			}
		} catch (java.net.SocketException e) {
			//fine.
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
		    if (dongle != null) {
		        MI301DongleProxy.releaseLink(dongle);
		    }
		    try {
                clientSocket.close();
            } catch (IOException e) {
            }
		}
	}
	
    private void resetDongle() throws IOException {
        Packet reset = rslpBegin(2);
        reset.addByte((byte)3);
        reset.addByte((byte)7); // reset dongle.
        reset.addByte((byte)0);
        reset.addByte((byte)0);
        reset.updateLengthAndCheckSum();
        reset.writeTo(dongle.getOutputStream(), false);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }
    }

    private Packet rslpBegin(int payloadLength) {
        Packet rslp = new Packet();
        rslp.addByte((byte)0x27);
        rslp.addByte((byte)(payloadLength + 2));
        rslp.addByte((byte)0); // to dongle
        rslp.addByte((byte)1); // seqno.
        return rslp;
    }

    Random random = new Random();
    public int copyPacket(InputStream inputStream, OutputStream outputStream, String dir) throws IOException {
        Packet in = new Packet();
        long startedAt = System.currentTimeMillis();
        while (!in.isComplete()) {
            in.readFrom(inputStream);
            long time = System.currentTimeMillis() - startedAt;
            if (in.getTop() == 0 && time > REVC_PKG_TIMEOUT) {
                break; // If nothing has been received, break out of loop.
            }
            if (in.getTop() > 0 && time > IDLE_TIME_OUT) {
                break; // Give up after idle timeout.
            }
        }
        if (in.isComplete()) {
            System.out.println(dir + " " + in);
            in.writeTo(outputStream, false);
            return in.getLength();
        } else {
            System.out.println(dir + " INCOMPLETE  " + in);
            return 0;
        }
    }
	private int handle() throws IOException {
	    int len = copyPacket(clientSocket.getInputStream(), dongle.getOutputStream(), ">>>");
	    len += copyPacket(dongle.getInputStream(), clientSocket.getOutputStream(), "<<<");
	    return len;
	}

    private static String hex(byte[] buffer, int top) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < top; i++) {
            hex(buffer[i], sb);
            if (((i+1) % 16) == 0) {
                sb.append("\n     ");
            } else {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
    
    private static void hex(byte b, StringBuilder sb) {
        String hexString = Integer.toHexString(((int)b) & 0xff);
        if (hexString.length() < 2) {
            sb.append('0');
        }
        sb.append(hexString.toUpperCase());
    }

}

