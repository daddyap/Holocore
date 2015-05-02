package intents;

import resources.control.Intent;
import resources.network.ServerType;
import resources.network.UDPServer.UDPPacket;

public class InboundUdpPacketIntent extends Intent {
	
	public static final String TYPE = "InboundUdpPacketIntent";
	
	private UDPPacket packet;
	private ServerType type;
	
	public InboundUdpPacketIntent(ServerType type, UDPPacket p) {
		super(TYPE);
		setPacket(p);
		setServerType(type);
	}
	
	public void setPacket(UDPPacket p) {
		this.packet = p;
	}
	
	public void setServerType(ServerType type) {
		this.type = type;
	}
	
	public UDPPacket getPacket() {
		return packet;
	}
	
	public ServerType getServerType() {
		return type;
	}
	
}