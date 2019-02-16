package com.grouleff.pumpcontrol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;

public class Packet implements Cloneable {
	// Layout: Much like a GeniTelegram, except for field 2,3.
	// 0: 0x27, 0x26, 0x24 [SAME AS Geni]
	// 1: len of rest excluding checksum [SAME AS Geni]
	// 2: dst (pump - regardless of direction of packet, always the pump and never r10k) [UNLIKE Geni] 
	// 3: seq-number (geni has src here!) [UNLIKE Geni]
	// ... payload [SAME AS Geni]
	// (len+2)..(len+3) CRC [SAME AS Geni]

	private byte[] buffer = new byte[300];
	private int top = 0;
	private boolean complete;
	
	public void reset() {
		top = 0;
		complete = false;
	}
	
	public Packet() {
	}
	
	public Packet(byte[] completeTelegram) {
		// wrap a complete telegram as a packet...
		top = completeTelegram.length;
		System.arraycopy(completeTelegram, 0, buffer, 0, top);
	}

    @Override
	protected Object clone() throws CloneNotSupportedException {
		Packet clone = (Packet)super.clone();
		clone.buffer = new byte[buffer.length];
		System.arraycopy(buffer, 0, clone.buffer, 0, top);
		return clone;
	}
	
	public void readFrom(InputStream in) throws IOException {
		if (top < 2) {
			int readLen;
			try {
				readLen = in.read(buffer, top, 2 - top);
			} catch (SocketTimeoutException e) {
				readLen = e.bytesTransferred;
			}
			if (readLen >= 0) {
				top += readLen;
			} else {
				throw new IOException("end of stream inside telegram");
			}
			if (top > 0) { // Ensure that we have sync - this is the start of a packet.
				if (!isValidStartDelimiter(buffer[0])) {
					// See if we got the start as the next byte.
					if (isValidStartDelimiter(buffer[1])) {
						buffer[0] = buffer[1];
						top = 1;
					} else {
						top = 0;
					}
				}
			}
		} else {
			int len = 4 + ((int)buffer[1]) & 0xff; // Include room for header and crc as well.
			if (top < len) {
				int readLen;
				try {
					readLen = in.read(buffer, top, len - top);
				} catch (SocketTimeoutException e) {
					readLen = e.bytesTransferred;
				}
				if (readLen >= 0) {
					top += readLen;
					if (top == len) {
						complete = true;
					}
				} else {
					throw new IOException("end of stream inside telegram");
				}
			} else {
				complete = true;
			}
		}
	}

	private boolean isValidStartDelimiter(byte sd) {
		return (sd == 0x24 || sd == 0x26 || sd == 0x27 || sd == 0x30);
	}
	
	public void addByte(int b) {
		buffer[top] = (byte)b;
		top++;
	}
	
	public void addInt32Bit(int intVal32bits) {
		byte msb = (byte)((intVal32bits >>> 24) & 0xff);
		byte b2 = (byte)((intVal32bits >>> 16) & 0xff);
		byte b3 = (byte)((intVal32bits >>> 8) & 0xff);
		byte lsb = (byte)(intVal32bits & 0xff);
		addByte(msb);
		addByte(b2);
		addByte(b3);
		addByte(lsb);
	}
	
	public void addInt16Bit(int intVal16bits) {
		byte msb = (byte)((intVal16bits >>> 8) & 0xff);
		byte lsb = (byte)(intVal16bits & 0xff);
		addByte(msb);
		addByte(lsb);
	}

	public boolean isComplete() {
		return complete;
	}

	public int getTop() {
		return top;
	}
	
	public int getLength() {
		return buffer[1];
	}
	public int getLengthOfPayload() {
		return buffer[1] - 2;
	}

	public byte getGeniDstAndDeviceHandle() {
		return buffer[2];
	}

	public void setGeniDstAndDeviceHandle(byte d) {
		buffer[2] = d;
	}
	
	public byte getSeqnoAndSrcField() {
		return buffer[3];
	}

	public void setSeqnoAndSrcField(byte s) {
		buffer[3] = s;
	}

    public void writeTo(OutputStream outputStream) throws IOException {
        writeTo(outputStream, false);
    }
	public void writeTo(OutputStream outputStream, boolean chunked) throws IOException {
        System.err.println("send: " + this.toString());
	    int written = 0;
	    while (written < top) {
	        int todo = 1 + (117 * written + 7 * top) % 23;
	        if (todo > (top - written)) {
	            todo = top - written;
	        }
	        if (!chunked) {
	            todo = top;
	        }
	        outputStream.write(buffer, written, todo);
	        outputStream.flush();
            written += todo;
            if (written < top) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
	    }
	}

	public static Packet readFrom(InputStream inputStream, int timeout) throws IOException {
        Packet p = new Packet();
        long startedAt = System.currentTimeMillis();
        while (!p.isComplete()) {
            p.readFrom(inputStream);
            long time = System.currentTimeMillis() - startedAt;
            if (time > timeout) {
                break;
            }
        }
        System.err.println("Recv: " + p);
        return p;
	}
	
	public byte getByte(int i) {
		return buffer[i];
	}

	public byte getPayloadByte(int i) {
		return buffer[i + 4];
	}

	public int getCheckValue() {
		int hi = ((int)buffer[top - 2]) & 0xff;
		int lo = ((int)buffer[top - 1]) & 0xff;
		return (hi << 8) | lo;
	}

	public boolean isCheckSumValid() {
	    if (top < 4 || !isComplete()) {
	        return false;
	    }
		int checkValue = getCheckValue();
		int crc = calculatebufferChecksum();
//		System.out.println("checkValue=" + Long.toHexString(checkValue) + " calculated=" + Long.toHexString(crc));
		return crc == checkValue;
	}

    public void updateLengthAndCheckSum() {
        buffer[1] = (byte)(top - 4);
        GeniCRC.appendTransmitterCheckValue(buffer, 1, top - 2);
    }

	public int calculatebufferChecksum() {
		return GeniCRC.calculateTransmitterCheckValue(buffer, 1, top - 2);
	}

	private void hex(byte b, StringBuilder sb) {
		String hexString = Integer.toHexString(((int)b) & 0xff);
		if (hexString.length() < 2) {
			sb.append('0');
		}
		sb.append(hexString.toUpperCase());
	}
	
	@Override
	public String toString() {
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

    public Packet close() {
        addByte((byte)0);
        addByte((byte)0);
        updateLengthAndCheckSum();
        return this;
    }

    public void startDongleRslp(int header) {
        addByte(header);
        addByte(0); // Len - in close().
        addByte(0); // to dongle
        addByte(1); // seqno.
        
    }
}
