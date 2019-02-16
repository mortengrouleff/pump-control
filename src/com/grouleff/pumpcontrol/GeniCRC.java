package com.grouleff.pumpcontrol;

/** CRC used on bus accoprding to "GENIbus Protocol Specification"
 *
 * "16 bit CCITT, polynomial is 0x1021, Start Delimiter excluded.
 *  Initialised to 0xFFFF, CRC value bit inverted after calculation.
 *  High order byte transmitted first. "
 *
 *  https://www.scribd.com/document/242204680/genispec-pdf
 *  http://forums.ni.com/ni/attachments/ni/170/717723/2/genispec.pdf
 */
public class GeniCRC {
	private static final int _0XFFFF = 0xffff;
	private static final int genpoly = 0x1021;
	
	private GeniCRC() {
		// static stuff only.
	}
	
	private static int /* unsigned 16 bit as int */ crchware(int data) {
		int accum = 0;
		int i;
		data <<= 8;
		for (i = 8; i > 0; i--) 	{
			if (((data ^ accum) & 0x8000) != 0) {
				accum = ((accum << 1) ^ genpoly) & _0XFFFF; 
			} else {
				accum = (accum << 1) & _0XFFFF;
			}
			data = (data << 1) & _0XFFFF;
		}
		return accum & _0XFFFF;
	}

	private static int[] generatecrctab() {
		int[] tab = new int[256];
		for (int i = 0; i < tab.length; i++) {
			tab[i] = crchware(i);
		}
		return tab;
	}

	/** unsigned 16 bit values as int, 256 values. */
	private static final int[] crctab = generatecrctab();

	/* 
		Transmitter: The CRC-Accumulator is initialized to 'all ones' and each byte, except the Start
		Delimiter, is processed through the crc_update function before being sent to the Drivers. Finally the
		CRC-Accumulator is inverted and its two bytes are appended to the telegram with high order byte first.
		These two bytes are what we define as the CRC-Value.
		
		Receiver: Performs a similar procedure. Initializes the CRC-Accumulator to 'all ones'. Then, each byte
		received, except the Start Delimiter, is processed through the crc_update function. When the CRC-
		Value bytes arrive they are inverted and then also processed through crc_update. If the CRC-
		Accumulator hereafter is equal to zero the received telegram is considered as sound.
	 */

	/**
	 * Implement "Final GENIbus CRC algorithm" from GENIspec.
	 * 
	 * @param raw - bytes of telegram
	 * @param start - offset of first byte to consider, typically 1 to skip "Start Delimiter"
	 * @param end - offset past last byte to include, typically offset where crc will be.
	 * @return checkValue - 16 bit unsigned as int.
	 */
	static int calculateTransmitterCheckValue(byte[] raw, int start, int end) {
		int accum = _0XFFFF;
		for (int i = start; i < end; i++) {
			int unsignedData = ((int)raw[i]) & 0xff;
			accum = ((accum << 8) ^ crctab[(accum >> 8) ^ unsignedData]) & _0XFFFF;
		}
		return ~accum & _0XFFFF;
	}
	
	/**
	 * Calculate checksum and write is as the two bytes pointed to by end.
	 * 
	 * @param raw - bytes of telegram
	 * @param start - offset of first byte to consider, typically 1 to skip "Start Delimiter"
	 * @param end - offset past last byte to include, also the offset where crc will be written.
	 */
	static void appendTransmitterCheckValue(byte[] raw, int start, int end) {
		int checkValue = calculateTransmitterCheckValue(raw, start, end);
		byte crc_hi = (byte)((checkValue >>> 8) & 0xff);
		byte crc_lo = (byte)(checkValue & 0xff);
		raw[end + 0] = crc_hi;
		raw[end + 1] = crc_lo;
	}

	/**
	 * Verify raw data against checkValue according to GENIbus CRC algorithm.
	 *  
	 * @param raw - bytes of telegram
	 * @param start - offset of first byte to consider, typically 1 to skip "Start Delimiter"
	 * @param end - offset past last byte to include, typicallly offset where crc will be written.
	 * @param checkValue - 16 bit unsigned as int.
	 * @return true if checkValue matches calculated crc of data.
	 */
	static boolean isReceivedCheckValueValid(byte[] raw, int start, int end, int checkValue) {
		return calculateTransmitterCheckValue(raw, start, end) == (checkValue & _0XFFFF);
	}

	
}
