package network;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import resources.SortedLinkedList;
import network.encryption.Encryption;
import network.packets.Packet;
import network.packets.soe.DataChannelA;
import network.packets.soe.Fragmented;
import network.packets.soe.MultiPacket;
import network.packets.soe.SessionRequest;
import network.packets.soe.SessionResponse;
import network.packets.swg.SWGPacket;

public class OutboundNetworkHandler {
	
	private final Queue <byte []> assembleQueue;
	private final SortedLinkedList <SequencedPacket> sequenced;
	private short sendSequence;
	private int crc;
	
	public OutboundNetworkHandler() {
		assembleQueue = new LinkedList<byte []>();
		sequenced = new SortedLinkedList<SequencedPacket>();
		sendSequence = 0;
		crc = 0;
	}
	
	public synchronized void reset() {
		sendSequence = 0;
		crc = 0;
	}
	
	public synchronized void setCrc(int crc) {
		this.crc = crc;
	}
	
	public synchronized int getCrc() {
		return crc;
	}
	
	public synchronized short getSentSequence() {
		return sendSequence;
	}
	
	public synchronized void onAcknowledge(short sequence) {
		synchronized (sequenced) {
			Iterator <SequencedPacket> it = sequenced.iterator();
			while (it.hasNext()) {
				SequencedPacket sp = it.next();
				if (sp.getSequence() <= sequence)
					it.remove();
				else
					break;
			}
		}
	}
	
	public synchronized void onOutOfOrder(short sequence) {
		synchronized (sequenced) {
			Iterator <SequencedPacket> it = sequenced.iterator();
			while (it.hasNext()) {
				SequencedPacket sp = it.next();
				if (sp.getSequence() <= sequence)
					pushAssembledEncrypted(sp.getPacket());
				else
					break;
			}
		}
	}
	
	public synchronized boolean hasAssembled() {
		synchronized (assembleQueue) {
			return !assembleQueue.isEmpty();
		}
	}
	
	public synchronized byte [] pollAssembled() {
		synchronized (assembleQueue) {
			return assembleQueue.poll();
		}
	}
	
	private void pushAssembledEncrypted(byte [] data) {
		data = Encryption.encode(data, crc);
		synchronized (assembleQueue) {
			assembleQueue.add(data);
		}
	}
	
	private void pushAssembledUnencrypted(byte [] data) {
		synchronized (assembleQueue) {
			assembleQueue.add(data);
		}
	}
	
	private void pushSequencedPacket(short sequence, byte [] packet) {
		synchronized (sequenced) {
			sequenced.add(new SequencedPacket(sequence, packet));
		}
	}
	
	public synchronized int assemble(Packet packet) {
		if (packet instanceof SessionRequest || packet instanceof SessionResponse) {
			pushAssembledUnencrypted(packet.encode().array());
			return 1;
		} else
			return assembleUnencrypted(packet);
	}
	
	private int assembleUnencrypted(Packet packet) {
		if (packet instanceof SWGPacket)
			return assembleSwg((SWGPacket) packet);
		else
			return assembleSoe(packet);
	}
	
	private int assembleSoe(Packet packet) {
		if (packet instanceof DataChannelA)
			return assembleDataChannelA((DataChannelA) packet);
		if (packet instanceof MultiPacket)
			return assembleMultiPacket((MultiPacket) packet);
		pushAssembledEncrypted(packet.encode().array());
		return 1;
	}
	
	private int assembleSwg(SWGPacket packet) {
		return assembleDataChannelA(new DataChannelA(packet));
	}
	
	private int assembleMultiPacket(MultiPacket m) {
		int len = m.getLength();
		if (len >= 493) {
			int count = getFragmentedPacketCount(len);
			int lastSeq = updateSequencesMulti((short)(sendSequence+count), m);
			for (Fragmented f : Fragmented.encode(m.encode(), sendSequence)) {
				byte [] encoded = f.encode().array();
				pushSequencedPacket(f.getSequence(), encoded);
				pushAssembledEncrypted(encoded);
			}
			sendSequence = (short) (lastSeq + 1);
			return count;
		} else {
			sendSequence = updateSequencesMulti(sendSequence, m);
			pushAssembledEncrypted(m.encode().array());
			return 1;
		}
	}
	
	private int assembleDataChannelA(DataChannelA d) {
		int len = d.getLength();
		if (len >= 493) {
			int count = getFragmentedPacketCount(len);
			int lastSeq = updateSequenceData((short)(sendSequence+count), d);
			for (Fragmented f : Fragmented.encode(d.encode(), sendSequence)) {
				byte [] encoded = f.encode().array();
				pushSequencedPacket(f.getSequence(), encoded);
				pushAssembledEncrypted(encoded);
			}
			sendSequence = (short) lastSeq;
			return count;
		} else {
			d.setSequence(sendSequence++);
			byte [] encoded = d.encode().array();
			pushSequencedPacket(d.getSequence(), encoded);
			pushAssembledEncrypted(encoded);
			return 1;
		}
	}
	
	private short updateSequencesMulti(short seq, MultiPacket m) {
		for (Packet p : m.getPackets()) {
			if (p instanceof DataChannelA)
				seq = updateSequenceData(seq, (DataChannelA) p);
		}
		return seq;
	}
	
	private short updateSequenceData(short seq, DataChannelA d) {
		d.setSequence(seq++);
		return seq;
	}
	
	private int getFragmentedPacketCount(int length) {
		return (int) Math.ceil((length+4)/489.0);
	}
	
	private static class SequencedPacket implements Comparable <SequencedPacket> {
		private final short sequence;
		private final byte [] packet;
		
		public SequencedPacket(short sequence, byte [] packet) {
			this.sequence = sequence;
			this.packet = packet;
		}
		
		public short getSequence() {
			return sequence;
		}
		
		public byte [] getPacket() {
			return packet;
		}
		
		@Override
		public int compareTo(SequencedPacket sp) {
			if (sequence < sp.getSequence())
				return -1;
			if (sequence == sp.getSequence())
				return 0;
			return 1;
		}
		
		@Override
		public boolean equals(Object o) {
			if (o == null)
				return false;
			if (o instanceof SequencedPacket)
				return ((SequencedPacket) o).getSequence() == sequence;
			return false;
		}
		
		@Override
		public int hashCode() {
			return sequence;
		}
		
	}
	
}