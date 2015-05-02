package resources.player;

import java.io.Serializable;

import network.packets.Packet;
import resources.control.Service;
import resources.objects.SWGObject;
import resources.objects.creature.CreatureObject;
import resources.objects.player.PlayerObject;

public class Player implements Serializable, Comparable<Player> {
	
	private static final long serialVersionUID = 1L;
	
	private transient Service playerManager;
	
	private long networkId;
	private PlayerState state		= PlayerState.DISCONNECTED;
	
	private String username			= "";
	private int userId				= 0;
	private byte [] sessionToken	= new byte[0];
	private int connectionId		= 0;
	private AccessLevel accessLevel	= AccessLevel.PLAYER;
	
	public int galaxyId				= 0;
	private CreatureObject creatureObject= null;
	private long lastInboundMessage	= 0;
	
	public Player() {
		this.playerManager = null;
	}
	
	public Player(Service playerManager, long networkId) {
		this.playerManager = playerManager;
		setNetworkId(networkId);
	}
	
	public void setPlayerManager(Service playerManager) {
		this.playerManager = playerManager;
	}
	
	public void setNetworkId(long networkId) {
		this.networkId = networkId;
	}
	
	public void setPlayerState(PlayerState state) {
		this.state = state;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public void setUserId(int userId) {
		this.userId = userId;
	}
	
	public void setConnectionId(int connId) {
		this.connectionId = connId;
	}
	
	public void setSessionToken(byte [] sessionToken) {
		this.sessionToken = sessionToken;
	}
	
	public void setAccessLevel(AccessLevel accessLevel) {
		this.accessLevel = accessLevel;
	}
	
	public void setGalaxyId(int galaxyId) {
		this.galaxyId = galaxyId;
	}
	
	public void setCreatureObject(CreatureObject obj) {
		this.creatureObject = obj;
	}
	
	public void updateLastPacketTimestamp() {
		lastInboundMessage = System.nanoTime();
	}
	
	public long getNetworkId() {
		return networkId;
	}
	
	public PlayerState getPlayerState() {
		return state;
	}
	
	public String getUsername() {
		return username;
	}
	
	public String getCharacterName() {
		if (creatureObject != null)
			return creatureObject.getName();
		return "";
	}
	
	public int getUserId() {
		return userId;
	}
	
	public int getConnectionId() {
		return connectionId;
	}
	
	public byte [] getSessionToken() {
		return sessionToken;
	}
	
	public AccessLevel getAccessLevel() {
		return accessLevel;
	}
	
	public int getGalaxyId() {
		return galaxyId;
	}
	
	public CreatureObject getCreatureObject() {
		return creatureObject;
	}
	
	public PlayerObject getPlayerObject() {
		if(creatureObject != null){
			SWGObject player = creatureObject.getSlottedObject("ghost");
			if (player instanceof PlayerObject)
				return (PlayerObject) player;			
		}
		return null;
	}
	
	public double getTimeSinceLastPacket() {
		return (System.nanoTime()-lastInboundMessage)/1E6;
	}
	
	public void sendPacket(Packet ... packets) {
		if (playerManager != null)
			playerManager.sendPacket(this, packets);
		else System.err.println("Couldn't send packet due to playerManager being null.");
	}
	
	@Override
	public String toString() {
		String str = "Player[";
		str += "ID=" + userId + " / " + getCreatureObject().getObjectId();
		str += " NAME=" + username + " / " + getCreatureObject().getName();
		str += " LEVEL=" + accessLevel;
		str += " STATE=" + state;
		return str + "]";
	}
	
	@Override
	public int compareTo(Player p) {
		return creatureObject.compareTo(p.getCreatureObject());
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Player))
			return false;
		return creatureObject.equals(((Player)o).getCreatureObject());
	}
	
	@Override
	public int hashCode() {
		return Long.valueOf(creatureObject.getObjectId()).hashCode() ^ getUserId();
	}
	
}