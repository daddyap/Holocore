package network.packets;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;


public class Packet {
	public static Charset ascii   = Charset.forName("UTF-8");
	public static Charset unicode = Charset.forName("UTF-16LE");
	private InetAddress       address;
	private ByteBuffer        data;
	private int               port = 0;
	private int               opcode;
	
	public Packet() {
		data = ByteBuffer.allocate(2);
	}
	
	public Packet(ByteBuffer data) {
		decode(data);
	}
	
	public void setAddress(InetAddress address) {
		this.address = address;
	}
	
	public InetAddress getAddress() {
		return address;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public int getPort() {
		return port;
	}
	
	public void setOpcode(int opcode) {
		this.opcode = opcode;
	}
	
	public int getOpcode() {
		return opcode;
	}
	
	public static void addBoolean(ByteBuffer bb, boolean b) {
		bb.put(b ? (byte)1 : (byte)0);
	}
	
	public static void addAscii(ByteBuffer bb, String s) {
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.putShort((short)s.length());
		bb.put(s.getBytes(ascii));
	}
	
	public static void addUnicode(ByteBuffer bb, String s) {
		bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.putInt(s.length());
		bb.put(s.getBytes(unicode));
	}
	
	public static void addLong(ByteBuffer bb, long l) {
		bb.order(ByteOrder.LITTLE_ENDIAN).putLong(l);
	}
	
	public static void addInt(ByteBuffer bb, int i) {
		bb.order(ByteOrder.LITTLE_ENDIAN).putInt(i);
	}
	
	public static void addFloat(ByteBuffer bb, float f) {
		bb.putFloat(f);
	}
	
	public static void addShort(ByteBuffer bb, int i) {
		bb.order(ByteOrder.LITTLE_ENDIAN).putShort((short)i);
	}
	
	public static void addNetLong(ByteBuffer bb, long l) {
		bb.order(ByteOrder.BIG_ENDIAN).putLong(l);
	}
	
	public static void addNetInt(ByteBuffer bb, int i) {
		bb.order(ByteOrder.BIG_ENDIAN).putInt(i);
	}
	
	public static void addNetShort(ByteBuffer bb, int i) {
		bb.order(ByteOrder.BIG_ENDIAN).putShort((short)i);
	}
	
	public static void addByte(ByteBuffer bb, int b) {
		bb.put((byte)b);
	}
	
	public static void addArray(ByteBuffer bb, byte [] b) {
		addShort(bb, b.length);
		bb.put(b);
	}
	
	public static boolean getBoolean(ByteBuffer bb) {
		return getByte(bb) == 1 ? true : false;
	}
	
	public static String getAscii(ByteBuffer bb) {
		bb.order(ByteOrder.LITTLE_ENDIAN);
		short length = bb.getShort();
		if (length > bb.remaining())
			return "";
		byte [] str = new byte[length];
		bb.get(str);
		return new String(str, ascii);
	}
	
	public static String getUnicode(ByteBuffer bb) {
		bb.order(ByteOrder.LITTLE_ENDIAN);
		int length = bb.getInt() * 2;
		if (length > bb.remaining())
			return "";
		byte [] str = new byte[length];
		bb.get(str);
		return new String(str, unicode);
	}
	
	public static byte getByte(ByteBuffer bb) {
		return bb.get();
	}
	
	public static short getShort(ByteBuffer bb) {
		return bb.order(ByteOrder.LITTLE_ENDIAN).getShort();
	}
	
	public static int getInt(ByteBuffer bb) {
		return bb.order(ByteOrder.LITTLE_ENDIAN).getInt();
	}
	
	public static float getFloat(ByteBuffer bb) {
		return bb.getFloat();
	}
	
	public static long getLong(ByteBuffer bb) {
		return bb.order(ByteOrder.LITTLE_ENDIAN).getLong();
	}
	
	public static short getNetShort(ByteBuffer bb) {
		return bb.order(ByteOrder.BIG_ENDIAN).getShort();
	}
	
	public static int getNetInt(ByteBuffer bb) {
		return bb.order(ByteOrder.BIG_ENDIAN).getInt();
	}
	
	public static long getNetLong(ByteBuffer bb) {
		return bb.order(ByteOrder.BIG_ENDIAN).getLong();
	}
	
	public static byte [] getArray(ByteBuffer bb) {
		byte [] data = new byte[getShort(bb)];
		bb.get(data);
		return data;
	}
	
	public static byte [] getArray(ByteBuffer bb, int length) {
		byte [] data = new byte[length];
		bb.get(data);
		return data;
	}
	
	public void decode(ByteBuffer data) {
		data.position(0);
		this.data = data;
		opcode = getNetShort(data);
		data.position(0);
	}
	
	public ByteBuffer getData() {
		return data;
	}
	
	public ByteBuffer encode() {
		return data;
	}
	
}