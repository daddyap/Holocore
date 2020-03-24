/***********************************************************************************
 * Copyright (c) 2018 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * --------------------------------------------------------------------------------*
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 *                                                                                 *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 *                                                                                 *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.               *
 ***********************************************************************************/
package com.projectswg.holocore.resources.support.objects.swg.creature;

import com.projectswg.common.data.CRC;
import com.projectswg.common.data.HologramColour;
import com.projectswg.common.data.encodables.mongo.MongoData;
import com.projectswg.common.data.encodables.tangible.Posture;
import com.projectswg.common.data.encodables.tangible.Race;
import com.projectswg.common.data.location.Location;
import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.encoding.StringType;
import com.projectswg.common.network.NetBuffer;
import com.projectswg.common.network.NetBufferStream;
import com.projectswg.common.network.packets.swg.zone.baselines.Baseline.BaselineType;
import com.projectswg.common.network.packets.swg.zone.deltas.DeltasMessage;
import com.projectswg.common.network.packets.swg.zone.object_controller.PostureUpdate;
import com.projectswg.holocore.resources.gameplay.crafting.trade.TradeSession;
import com.projectswg.holocore.resources.gameplay.player.group.GroupInviterData;
import com.projectswg.holocore.resources.support.data.collections.SWGSet;
import com.projectswg.holocore.resources.support.data.persistable.SWGObjectFactory;
import com.projectswg.holocore.resources.support.global.network.BaselineBuilder;
import com.projectswg.holocore.resources.support.global.player.Player;
import com.projectswg.holocore.resources.support.global.player.PlayerState;
import com.projectswg.holocore.resources.support.objects.awareness.AwarenessType;
import com.projectswg.holocore.resources.support.objects.swg.SWGObject;
import com.projectswg.holocore.resources.support.objects.swg.creature.attributes.Attributes;
import com.projectswg.holocore.resources.support.objects.swg.creature.attributes.AttributesMutable;
import com.projectswg.holocore.resources.support.objects.swg.player.PlayerObject;
import com.projectswg.holocore.resources.support.objects.swg.tangible.OptionFlag;
import com.projectswg.holocore.resources.support.objects.swg.tangible.TangibleObject;
import com.projectswg.holocore.resources.support.objects.swg.weapon.WeaponObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class 
CreatureObject extends TangibleObject {
	
	private transient long lastReserveOperation		= 0;
	
	private final CreatureObjectAwareness		awareness		= new CreatureObjectAwareness(this);
	private final CreatureObjectClientServerNP	creo4 			= new CreatureObjectClientServerNP(this);
	private final CreatureObjectSharedNP		creo6 			= new CreatureObjectSharedNP(this);
	private final Map<CreatureObject, Integer>	damageMap 		= new HashMap<>();
	private final List<CreatureObject>			sentDuels		= new ArrayList<>();
	private final Set<Container>				containersOpen	= ConcurrentHashMap.newKeySet();
	private final List<DeltasMessage>			pendingDeltas	= new ArrayList<>();
	private final AtomicReference<Player>		owner			= new AtomicReference<>(null);
	
	private Posture	posture					= Posture.UPRIGHT;
	private Race	race					= Race.HUMAN_MALE;
	private double	height					= 0;
	private byte 	factionRank				= 0;
	private long 	ownerId					= 0;
	private int 	battleFatigue			= 0;
	private long 	statesBitmask			= 0;
	private long	lastTransform			= 0;
	private long	lastCombat				= 0;
	private TradeSession tradeSession		= null;
	
	private SWGSet<String> skills					= new SWGSet<>(1, 3, StringType.ASCII);
	private AttributesMutable baseAttributes;
	
	
	public CreatureObject(long objectId) {
		super(objectId, BaselineType.CREO);
		this.baseAttributes = new AttributesMutable(this, 1, 2);
		initBaseAttributes();
		getAwareness().setAware(AwarenessType.SELF, List.of(this));
	}
	
	public void flushAwareness() {
		Player owner = getOwnerShallow();
		if (getTerrain() == Terrain.GONE || owner == null || owner.getPlayerState() == PlayerState.DISCONNECTED) {
			awareness.flushNoPlayer();
		} else {
			awareness.flush(owner);
			sendAndFlushAllDeltas();
		}
	}
	
	public void resetObjectsAware() {
		awareness.resetObjectsAware();
	}
	
	@Override
	public void addObject(SWGObject obj) {
		super.addObject(obj);
		if (obj.getSlotArrangement() != -1 && !(obj instanceof PlayerObject) && !super.hasOptionFlags(OptionFlag.MOUNT)) {
			addEquipment(obj);
		}
	}
	
	@Override
	public void removeObject(SWGObject obj) {
		super.removeObject(obj);
		removeEquipment(obj);
	}
	
	public void setOwner(@Nullable Player owner) {
		Player previous = this.owner.getAndSet(owner);
		if (previous != null)
			previous.setCreatureObject(null);
		if (owner != null)
			owner.setCreatureObject(this);
	}
	
	@Override
	@Nullable
	public Player getOwner() {
		return owner.get();
	}
	
	@Override
	@Nullable
	public Player getOwnerShallow() {
		return owner.get();
	}
	
	@NotNull
	public SWGObject getInventory() {
		SWGObject inventory = getSlottedObject("inventory");
		assert inventory != null;
		return inventory;
	}
	
	@NotNull
	public SWGObject getDatapad() {
		SWGObject datapad = getSlottedObject("datapad");
		assert datapad != null;
		return datapad;
	}
	
	@Override
	@Nullable
	public SWGObject getEffectiveParent() {
		return isStatesBitmask(CreatureState.RIDING_MOUNT) ? null : getParent();
	}
	
	@Override
	protected void handleSlotReplacement(SWGObject oldParent, SWGObject obj, List<String> slots) {
		SWGObject inventory = getSlottedObject("inventory");
		for (String slot : slots) {
			SWGObject slotObj = getSlottedObject(slot);
			if (slotObj != null && slotObj != inventory) {
				slotObj.moveToContainer(inventory);
			}
		}
	}
	
	@Override
	protected void onAddedChild(SWGObject child) {
		if (!isPlayer())
			return;
		super.onAddedChild(child);
		Set<SWGObject> children = new HashSet<>(getAwareness().getAware(AwarenessType.SELF));
		getAllChildren(children, child);
		getAwareness().setAware(AwarenessType.SELF, children);
	}
	
	@Override
	protected void onRemovedChild(SWGObject child) {
		if (!isPlayer())
			return;
		super.onRemovedChild(child);
		Set<SWGObject> children = new HashSet<>(getAwareness().getAware(AwarenessType.SELF));
		{
			Set<SWGObject> removed = new HashSet<>();
			getAllChildren(removed, child);
			children.removeAll(removed);
			assert !removed.contains(this);
		}
		getAwareness().setAware(AwarenessType.SELF, children);
	}
	
	private void getAllChildren(Collection<SWGObject> children, SWGObject child) {
		children.add(child);
		for (SWGObject obj : child.getSlottedObjects())
			getAllChildren(children, obj);
		for (SWGObject obj : child.getContainedObjects())
			getAllChildren(children, obj);
	}
	
	public boolean isWithinAwarenessRange(SWGObject target) {
		assert isPlayer();
		
		Player owner = getOwnerShallow();
		if (owner == null || owner.getPlayerState() == PlayerState.DISCONNECTED || !target.isVisible(this))
			return false;
		
		SWGObject myParent = getSuperParent();
		SWGObject targetParent = target.getSuperParent();
		if (myParent != null && myParent == targetParent)
			return true;
		
		switch (target.getBaselineType()) {
			case WAYP:
				return false;
			case SCLT:
			case BUIO:
				return true;
			case CREO:
				return flatDistanceTo(target) <= 200;
			default:
				return flatDistanceTo(target) <= 400;
		}
	}
	
	@Override
	public boolean isVisible(CreatureObject target) {
		return !isLoggedOutPlayer() && super.isVisible(target);
	}

	private void addEquipment(SWGObject obj) {
		creo6.addEquipment(obj, this);
	}

	private void removeEquipment(SWGObject obj) {
		creo6.removeEquipment(obj, this);
	}
	
	public void addAppearanceItem(SWGObject obj) {
		creo6.addAppearanceItem(obj, this);
	}

	public void removeAppearanceItem(SWGObject obj) {
		creo6.removeAppearanceItem(obj, this);
	}
	
	public boolean isContainerOpen(SWGObject obj, String slot) {
		return containersOpen.contains(new Container(obj, slot));
	}
	
	public boolean openContainer(SWGObject obj, String slot) {
		return containersOpen.add(new Container(obj, slot));
	}
	
	public boolean closeAllContainers() {
		boolean empty = containersOpen.isEmpty();
		containersOpen.clear();
		return !empty;
	}
	
	public boolean closeContainer(SWGObject obj, String slot) {
		return containersOpen.remove(new Container(obj, slot));
	}
	
	public void setTeleportDestination(SWGObject parent, Location location) {
		awareness.setTeleportDestination(parent, location);
	}
	
	public void addDelta(DeltasMessage delta) {
		synchronized (pendingDeltas) {
			pendingDeltas.add(delta);
		}
	}
	
	public void clearDeltas() {
		if (pendingDeltas.isEmpty())
			return;
		synchronized (pendingDeltas) {
			pendingDeltas.clear();
		}
	}
	
	public void sendAndFlushAllDeltas() {
		if (pendingDeltas.isEmpty())
			return;
		synchronized (pendingDeltas) {
			Player owner = getOwner();
			if (owner != null) {
				for (DeltasMessage delta : pendingDeltas) {
					if (awareness.isAware(delta.getObjectId()))
						owner.sendPacket(delta);
				}
			}
			pendingDeltas.clear();
		}
	}
	
	public boolean addSkill(String skill) {
		boolean added = skills.add(skill);
		if (added)
			skills.sendDeltaMessage(this);
		return added;
	}
	
	public boolean hasSkill(String skillName) {
		return skills.contains(skillName);
	}
	
	public Set<String> getSkills() {
		return Collections.unmodifiableSet(skills);
	}
	
	public Posture getPosture() {
		return posture;
	}
	
	public Race getRace() {
		return race;
	}
	
	public double getHeight() {
		return height;
	}
	
	public int getGuildId() {
		return creo6.getGuildId();
	}
	
	public short getLevel() {
		return creo6.getLevel();
	}
	
	public int getLevelHealthGranted() {
		return creo6.getLevelHealthGranted();
	}
	
	public CreatureDifficulty getDifficulty() {
		return creo6.getDifficulty();
	}
	
	public double getTimeSinceLastTransform() {
		return (System.nanoTime()-lastTransform)/1E6;
	}
	
	public double getTimeSinceLastCombat() {
		return (System.nanoTime() - lastCombat) / 1E6;
	}
	
	public PlayerObject getPlayerObject() {
		return (PlayerObject) getSlottedObject("ghost");
	}
	
	public boolean isPlayer() {
		return getSlottedObject("ghost") != null;
	}
	
	public boolean isLoggedInPlayer() {
		return getOwnerShallow() != null && isPlayer();
	}
	
	public boolean isLoggedOutPlayer() {
		return getOwner() == null && isPlayer();
	}	
	
	public TradeSession getTradeSession() {
		return tradeSession;
	}

	public void setTradeSession(TradeSession tradeSession) {
		this.tradeSession = tradeSession;
	}

	public void setPosture(Posture posture) {
		this.posture = posture;
		sendObservers(new PostureUpdate(getObjectId(), posture));
		sendDelta(3, 13, posture.getId());
	}
	
	public void setRace(Race race) {
		this.race = race;
	}
	
	public boolean canPerformGalacticReserveTransaction() {
		return (System.nanoTime() - lastReserveOperation) / 1E9 >= 15*60;
	}
	
	public void updateLastGalacticReserveTime() {
		lastReserveOperation = System.nanoTime();
	}
	
	public void inheritMovement(CreatureObject vehicle) {
		setWalkSpeed(vehicle.getRunSpeed() / 2);
		setRunSpeed(vehicle.getRunSpeed());
		setAccelScale(vehicle.getAccelScale());
		setTurnScale(vehicle.getTurnScale());
		setMovementScale(vehicle.getMovementScale());
	}
	
	public void resetMovement() {
		setWalkSpeed(1.549);
		setRunSpeed(7.3);
		setAccelScale(1);
		setTurnScale(1);
		setMovementScale(1);
	}
	
	/*
	 * =====-----  -----=====
	 * ===== Baseline 4 =====
	 * =====-----  -----=====
	 */
	
	public double getAccelPercent() {
		return creo4.getAccelPercent();
	}
	
	public void setAccelPercent(double accelPercent) {
		creo4.setAccelPercent((float) accelPercent);
	}
	
	public float getAccelScale() {
		return creo4.getAccelScale();
	}
	
	public void setAccelScale(double accelScale) {
		creo4.setAccelScale((float) accelScale);
	}
	
	@NotNull
	public Attributes getBonusAttributes() {
		return creo4.getBonusAttributes();
	}
	
	public void adjustSkillmod(@NotNull String skillModName, int base, int modifier) {
		creo4.adjustSkillmod(skillModName, base, modifier);
	}
	
	public int getSkillModValue(@NotNull String skillModName) {
		return creo4.getSkillModValue(skillModName);
	}
	
	public float getMovementPercent() {
		return creo4.getMovementPercent();
	}
	
	public void setMovementPercent(double movementPercent) {
		creo4.setMovementPercent((float) movementPercent);
	}
	
	public float getMovementScale() {
		return creo4.getMovementScale();
	}
	
	public void setMovementScale(double movementScale) {
		creo4.setMovementScale((float) movementScale);
	}
	
	public long getPerformanceListenTarget() {
		return creo4.getPerformanceListenTarget();
	}
	
	public void setPerformanceListenTarget(long performanceListenTarget) {
		creo4.setPerformanceListenTarget(performanceListenTarget);
	}
	
	public float getRunSpeed() {
		return creo4.getRunSpeed();
	}
	
	public void setRunSpeed(double runSpeed) {
		creo4.setRunSpeed((float) runSpeed);
	}
	
	public float getSlopeModAngle() {
		return creo4.getSlopeModAngle();
	}
	
	public void setSlopeModAngle(double slopeModAngle) {
		creo4.setSlopeModAngle((float) slopeModAngle);
	}
	
	public float getSlopeModPercent() {
		return creo4.getSlopeModPercent();
	}
	
	public void setSlopeModPercent(double slopeModPercent) {
		creo4.setSlopeModPercent((float) slopeModPercent);
	}
	
	public float getTurnScale() {
		return creo4.getTurnScale();
	}
	
	public void setTurnScale(double turnScale) {
		creo4.setTurnScale((float) turnScale);
	}
	
	public float getWalkSpeed() {
		return creo4.getWalkSpeed();
	}
	
	public void setWalkSpeed(double walkSpeed) {
		creo4.setWalkSpeed((float) walkSpeed);
	}
	
	public float getWaterModPercent() {
		return creo4.getWaterModPercent();
	}
	
	public void setWaterModPercent(double waterModPercent) {
		creo4.setWaterModPercent((float) waterModPercent);
	}
	
	@NotNull
	public Set<GroupMissionCriticalObject> getMissionCriticalObjects() {
		return creo4.getMissionCriticalObjects();
	}
	
	public void setMissionCriticalObjects(@NotNull Set<GroupMissionCriticalObject> missionCriticalObjects) {
		creo4.setMissionCriticalObjects(missionCriticalObjects);
	}
	
	@NotNull
	public Set<String> getCommands() {
		return creo4.getCommands();
	}
	
	public void addCommand(@NotNull String command) {
		creo4.addCommand(command);
	}
	
	public void addCommand(@NotNull String... commands) {
		creo4.addCommands(commands);
	}
	
	public void removeCommand(@NotNull String command) {
		creo4.removeCommand(command);
	}
	
	public boolean hasCommand(@NotNull String command) {
		return creo4.hasCommand(command);
	}
	
	public int getTotalLevelXp() {
		return creo4.getTotalLevelXp();
	}
	
	public void setTotalLevelXp(int totalLevelXp) {
		creo4.setTotalLevelXp(totalLevelXp);
	}
	
	public void setHeight(double height) {
		this.height = height;
		sendDelta(3, 16, height);
	}
	
	public void setGuildId(int guildId) {
		creo6.setGuildId(guildId);
		sendDelta(6, 15, guildId);
	}
	
	public void setLevel(int level) {
		creo6.setLevel(level);
		sendDelta(6, 8, (short) level);
	}
	
	public void setLevelHealthGranted(int levelHealthGranted) {
		creo6.setLevelHealthGranted(levelHealthGranted);
		sendDelta(6, 9, levelHealthGranted);
	}
	
	public void setDifficulty(CreatureDifficulty difficulty) {
		creo6.setDifficulty(difficulty);
		sendDelta(6, 26, difficulty.getDifficulty());
	}
	
	public void updateLastTransformTime() {
		lastTransform = System.nanoTime();
	}
	
	public void updateLastCombatTime() {
		lastCombat = System.nanoTime();
	}
	
	public String getMoodAnimation() {
		return creo6.getMoodAnimation();
	}

	public void setMoodAnimation(String moodAnimation) {
		creo6.setMoodAnimation(moodAnimation);
		sendDelta(6, 11, moodAnimation, StringType.ASCII);
	}

	public boolean isBeast() {
		return creo6.isBeast();
	}

	public void setBeast(boolean beast) {
		creo6.setBeast(beast);
		sendDelta(6, 31, beast);
	}

	public String getAnimation() {
		return creo6.getAnimation();
	}

	public void setAnimation(String animation) {
		creo6.setAnimation(animation);
		sendDelta(6, 10, animation, StringType.ASCII);
	}

	public WeaponObject getEquippedWeapon() {
		return getSlottedObjects().stream()
				.filter(obj -> obj.getObjectId() == creo6.getEquippedWeapon())
				.map(WeaponObject.class::cast)
				.findFirst()
				.orElse(null);
	}

	public void setEquippedWeapon(WeaponObject weapon) {
		WeaponObject equippedWeapon;
		
		if(weapon == null)
			equippedWeapon = (WeaponObject) getSlottedObject("default_weapon");
		else
			equippedWeapon = weapon;
		
		creo6.setEquippedWeapon(equippedWeapon.getObjectId());
		sendDelta(6, 12, equippedWeapon.getObjectId());
	}

	public byte getMoodId() {
		return creo6.getMoodId();
	}

	public void setMoodId(byte moodId) {
		creo6.setMoodId(moodId);
		sendDelta(6, 18, moodId);
	}

	public long getLookAtTargetId() {
		return creo6.getLookAtTargetId();
	}

	public void setLookAtTargetId(long lookAtTargetId) {
		creo6.setLookAtTargetId(lookAtTargetId);
		sendDelta(6, 16, lookAtTargetId);
	}

	public long getIntendedTargetId() {
		return creo6.getIntendedTargetId();
	}

	public void setIntendedTargetId(long intendedTargetId) {
		creo6.setIntendedTargetId(intendedTargetId);
		sendDelta(6, 17, intendedTargetId);
	}

	public int getPerformanceCounter() {
		return creo6.getPerformanceCounter();
	}

	public void setPerformanceCounter(int performanceCounter) {
		creo6.setPerformanceCounter(performanceCounter);
		sendDelta(6, 19, performanceCounter);
	}

	public int getPerformanceId() {
		return creo6.getPerformanceId();
	}

	public void setPerformanceId(int performanceId) {
		creo6.setPerformanceId(performanceId);
		sendDelta(6, 20, performanceId);
	}

	public String getCostume() {
		return creo6.getCostume();
	}

	public void setCostume(String costume) {
		creo6.setCostume(costume);
		sendDelta(6, 24, costume, StringType.ASCII);
	}

	public long getGroupId() {
		return creo6.getGroupId();
	}

	public void updateGroupInviteData(Player sender, long groupId, String name) {
		creo6.updateGroupInviteData(sender, groupId, name);
		sendDelta(6, 14, creo6.getInviterData());
	}

	public GroupInviterData getInviterData() {
		return creo6.getInviterData();
	}

	public void setGroupId(long groupId) {
		creo6.setGroupId(groupId);
		sendDelta(6, 13, groupId);
	}

	public byte getFactionRank() {
		return factionRank;
	}

	public void setFactionRank(byte factionRank) {
		this.factionRank = factionRank;
		sendDelta(3, 14, factionRank);
	}

	public long getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(long ownerId) {
		this.ownerId = ownerId;
		sendDelta(3, 15, ownerId);
	}

	public int getBattleFatigue() {
		return battleFatigue;
	}

	public void setBattleFatigue(int battleFatigue) {
		this.battleFatigue = battleFatigue;
		sendDelta(3, 17, battleFatigue);
	}

	public long getStatesBitmask() {
		return statesBitmask;
	}
	
	public boolean isStatesBitmask(CreatureState ... states) {
		for (CreatureState state : states) {
			if ((statesBitmask & state.getBitmask()) == 0)
				return false;
		}
		return true;
	}

	public void setStatesBitmask(CreatureState ... states) {
		for (CreatureState state : states)
			statesBitmask |= state.getBitmask();
		sendDelta(3, 18, statesBitmask);
	}

	public void toggleStatesBitmask(CreatureState ... states) {
		for (CreatureState state : states)
			statesBitmask ^= state.getBitmask();
		sendDelta(3, 18, statesBitmask);
	}

	public void clearStatesBitmask(CreatureState ... states) {
		for (CreatureState state : states)
			statesBitmask &= ~state.getBitmask();
		sendDelta(3, 18, statesBitmask);
	}

	public void clearAllStatesBitmask() {
		statesBitmask = 0;
		sendDelta(3, 18, statesBitmask);
	}
	
	public void addBuff(Buff buff) {
		creo6.putBuff(buff, this);
	}
	
	public Buff removeBuff(CRC buffCrc) {
		return creo6.removeBuff(buffCrc, this);
	}
	
	public boolean hasBuff(String buffName) {
		return getBuffEntries(buff -> CRC.getCrc(buffName.toLowerCase(Locale.ENGLISH)) == buff.getCrc()).count() > 0;
	}
	
	public Stream<Buff> getBuffEntries(Predicate<Buff> predicate) {
		return creo6.getBuffEntries(predicate);
	}
	
	public void adjustBuffStackCount(CRC buffCrc, int adjustment) {
		creo6.adjustBuffStackCount(buffCrc, adjustment, this);
	}
	
	public void setBuffDuration(CRC buffCrc, int playTime, int duration) {
		creo6.setBuffDuration(buffCrc, playTime, duration, this);
	}
	
	public boolean isVisible() {
		return creo6.isVisible();
	}

	public void setVisible(boolean visible) {
		creo6.setVisible(visible);
		sendDelta(6, 25, visible);
	}

	public boolean isPerforming() {
		return creo6.isPerforming();
	}

	public void setPerforming(boolean performing) {
		creo6.setPerforming(performing);
		sendDelta(6, 27, performing);
	}
	
	public HologramColour getHologramColor() {
		return creo6.getHologramColor();
	}
	
	public void setHologramColour(HologramColour hologramColour) {
		creo6.setHologramColour(hologramColour);
		sendDelta(6, 29, hologramColour.getValue());
	}

	public boolean isShownOnRadar() {
		return creo6.isShownOnRadar();
	}

	public void setShownOnRadar(boolean shownOnRadar) {
		creo6.setShownOnRadar(shownOnRadar);
		sendDelta(6, 30, shownOnRadar);
	}

	public int getHealth() {
		return creo6.getHealth();
	}
	
	public int getMaxHealth() {
		return creo6.getMaxHealth();
	}
	
	public int getBaseHealth() {
		return baseAttributes.getHealth();
	}
	
	public int getAction() {
		return creo6.getAction();
	}
	
	public int getMaxAction() {
		return creo6.getMaxAction();
	}
	
	public int getBaseAction() {
		return baseAttributes.getAction();
	}
	
	public int getMind() {
		return creo6.getMind();
	}
	
	public int getMaxMind() {
		return creo6.getMaxMind();
	}
	
	public int getBaseMind() {
		return baseAttributes.getMind();
	}
	
	public void setBaseHealth(int baseHealth) {
		baseAttributes.setHealth(baseHealth);
	}
	
	public void setHealth(int health) {
		creo6.setHealth(health);
	}
	
	public void modifyHealth(int mod) {
		creo6.modifyHealth(mod);
	}
	
	public void setMaxHealth(int maxHealth) {
		creo6.setMaxHealth(maxHealth);
	}
	
	public void setBaseAction(int baseAction) {
		baseAttributes.setAction(baseAction);
	}
	
	public void setAction(int action) {
		creo6.setAction(action);
	}
	
	public void modifyAction(int mod) {
		creo6.modifyAction(mod);
	}
	
	public void setMaxAction(int maxAction) {
		creo6.setMaxAction(maxAction);
	}
	
	public void setMind(int mind) {
		creo6.setMind(mind);
	}
	
	public void modifyMind(int mod) {
		creo6.modifyMind(mod);
	}
	
	public void setMaxMind(int maxMind) {
		creo6.setMaxMind(maxMind);
	}
	
	private void initBaseAttributes() {
		baseAttributes.setHealth(1000);
		baseAttributes.setAction(300);
		baseAttributes.setMind(300);
	}
	
	public Collection<SWGObject> getItemsByTemplate(String slotName, String template) {
		Collection<SWGObject> items = new ArrayList<>(getContainedObjects()); // We also search the creature itself - not just the inventory.
		SWGObject container = getSlottedObject(slotName);
		Collection<SWGObject> candidateChildren;
		
		for(SWGObject candidate : container.getContainedObjects()) {
			
			if(candidate.getTemplate().equals(template)) {
				items.add(candidate);
			} else {
				// check the children. This way we're also searching containers, such as backpacks.
				candidateChildren = candidate.getContainedObjects();
				
				for(SWGObject candidateChild : candidateChildren) {
					if(candidate.getTemplate().equals(template)) {
						items.add(candidateChild);
					}
				}
			}
		}
		return items;
	}
	
	public Map<CreatureObject, Integer> getDamageMap(){
		return Collections.unmodifiableMap(damageMap);
	}
	
	public CreatureObject getHighestDamageDealer(){
		synchronized (damageMap){
			return damageMap.keySet().stream().max(Comparator.comparingInt(damageMap::get)).orElse(null);
		}
	}
	
	public void handleDamage(CreatureObject attacker, int damage){
		synchronized (damageMap){
			if(damageMap.containsKey(attacker))
				damageMap.put(attacker, damageMap.get(attacker) + damage);
			else 
				damageMap.put(attacker, damage);
		}
	}
	
	public boolean hasSentDuelRequestToPlayer(CreatureObject player) {
		return sentDuels.contains(player);
	}
	
	public boolean isDuelingPlayer(CreatureObject player) {
		return hasSentDuelRequestToPlayer(player) && player.hasSentDuelRequestToPlayer(this);
	}
	
	public void addPlayerToSentDuels(CreatureObject player) {
		sentDuels.add(player);
	}
	
	public void removePlayerFromSentDuels(CreatureObject player) {
		sentDuels.remove(player);
	}
	
	public boolean isBaselinesSent(SWGObject obj) {
		return awareness.isAware(obj);
	}
	
	@Override
	public void createBaseline1(Player target, BaselineBuilder bb) {
		super.createBaseline1(target, bb); // 2 variables
		bb.addObject(baseAttributes); // Attributes player has without any gear on -- 2
		bb.addObject(skills); // 3
		
		bb.incrementOperandCount(2);
	}
	
	@Override
	public void createBaseline3(Player target, BaselineBuilder bb) {
		super.createBaseline3(target, bb); // 13 variables - TANO3 (9) + BASE3 (4)
		if (getStringId().toString().equals("@obj_n:unknown_object"))
			return;
		bb.addByte(posture.getId()); // 13
		bb.addByte(factionRank); // 14
		bb.addLong(ownerId); // 15
		bb.addFloat((float) height); // 16
		bb.addInt(battleFatigue); // 17
		bb.addLong(statesBitmask); // 18
		
		bb.incrementOperandCount(6);
	}
	
	@Override
	public void createBaseline4(Player target, BaselineBuilder bb) {
		super.createBaseline4(target, bb); // 0 variables
		if (getStringId().toString().equals("@obj_n:unknown_object"))
			return;
		creo4.createBaseline4(bb);
	}
	
	@Override
	public void createBaseline6(Player target, BaselineBuilder bb) {
		super.createBaseline6(target, bb); // 8 variables - TANO6 (6) + BASE6 (2)
		if (getStringId().toString().equals("@obj_n:unknown_object"))
			return;
		creo6.createBaseline6(target, bb);
	}
	
	@Override
	protected void parseBaseline1(NetBuffer buffer) {
		super.parseBaseline1(buffer);
		baseAttributes.decode(buffer);
		skills = SWGSet.getSwgSet(buffer, 1, 3, StringType.ASCII);
	}
	
	@Override
	protected void parseBaseline3(NetBuffer buffer) {
		super.parseBaseline3(buffer);
		posture = Posture.getFromId(buffer.getByte());
		factionRank = buffer.getByte();
		ownerId = buffer.getLong();
		height = buffer.getFloat();
		battleFatigue = buffer.getInt();
		statesBitmask = buffer.getLong();
	}
	
	@Override
	protected void parseBaseline4(NetBuffer buffer) {
		super.parseBaseline4(buffer);
		creo4.parseBaseline4(buffer);
	}
	
	@Override
	protected void parseBaseline6(NetBuffer buffer) {
		super.parseBaseline6(buffer);
		creo6.parseBaseline6(buffer);
	}
	
	@Override
	public void saveMongo(MongoData data) {
		super.saveMongo(data);
		creo4.saveMongo(data.getDocument("base4"));
		creo6.saveMongo(data.getDocument("base6"));
		data.putString("posture", posture.name());
		data.putString("race", race.name());
		data.putDouble("height", height);
		data.putInteger("battleFatigue", battleFatigue);
		data.putLong("ownerId", ownerId);
		data.putLong("statesBitmask", statesBitmask);
		data.putInteger("factionRank", factionRank);
		data.putArray("skills", skills);
		data.putDocument("baseAttributes", baseAttributes);
	}
	
	@Override
	public void readMongo(MongoData data) {
		super.readMongo(data);
		skills.clear();
		
		creo4.readMongo(data.getDocument("base4"));
		creo6.readMongo(data.getDocument("base6"));
		posture = Posture.valueOf(data.getString("posture", posture.name()));
		race = Race.valueOf(data.getString("race", race.name()));
		height = data.getDouble("height", height);
		battleFatigue = data.getInteger("battleFatigue", battleFatigue);
		ownerId = data.getLong("ownerId", ownerId);
		statesBitmask = data.getLong("statesBitmask", statesBitmask);
		factionRank = (byte) data.getInteger("factionRank", factionRank);
		skills.addAll(data.getArray("skills", String.class));
		data.getDocument("baseAttributes", baseAttributes);
	}
	
	@Override
	public void save(NetBufferStream stream) {
		super.save(stream);
		stream.addByte(3);
		creo4.save(stream);
		creo6.save(stream);
		stream.addAscii(posture.name());
		stream.addAscii(race.name());
		stream.addFloat((float) height);
		stream.addInt(battleFatigue);
		stream.addInt(getCashBalance());
		stream.addInt(getBankBalance());
		stream.addLong(ownerId);
		stream.addLong(statesBitmask);
		stream.addByte(factionRank);
		synchronized (skills) {
			stream.addList(skills, stream::addAscii);
		}
		baseAttributes.save(stream);
	}
	
	@Override
	public void read(NetBufferStream stream) {
		super.read(stream);
		switch(stream.getByte()) {
			case 0: readVersion0(stream); break;
			case 1: readVersion1(stream); break;
			case 2: readVersion2(stream); break;
			case 3: readVersion3(stream); break;
		}
		
	}
	
	private void readVersion0(NetBufferStream stream) {
		creo4.read(stream);
		creo6.read(stream);
		posture = Posture.valueOf(stream.getAscii());
		race = Race.valueOf(stream.getAscii());
		height = stream.getFloat();
		battleFatigue = stream.getInt();
		setCashBalance(stream.getInt());
		setBankBalance(stream.getInt());
		stream.getLong();
		ownerId = stream.getLong();
		statesBitmask = stream.getLong();
		factionRank = stream.getByte();
		if (stream.getBoolean()) {
			SWGObject defaultWeapon = SWGObjectFactory.create(stream);
			defaultWeapon.moveToContainer(this);	// The weapon will be moved into the default_weapon slot
		}
		stream.getList((i) -> skills.add(stream.getAscii()));
		readAttributes((byte) 0, baseAttributes, stream);
	}
	
	private void readVersion1(NetBufferStream stream) {
		creo4.read(stream);
		creo6.read(stream);
		posture = Posture.valueOf(stream.getAscii());
		race = Race.valueOf(stream.getAscii());
		height = stream.getFloat();
		battleFatigue = stream.getInt();
		setCashBalance(stream.getInt());
		setBankBalance(stream.getInt());
		stream.getLong();
		ownerId = stream.getLong();
		statesBitmask = stream.getLong();
		factionRank = stream.getByte();
		stream.getList((i) -> skills.add(stream.getAscii()));
		readAttributes((byte) 1, baseAttributes, stream);
	}
	
	private void readVersion2(NetBufferStream stream) {
		creo4.read(stream);
		creo6.read(stream);
		posture = Posture.valueOf(stream.getAscii());
		race = Race.valueOf(stream.getAscii());
		height = stream.getFloat();
		battleFatigue = stream.getInt();
		setCashBalance(stream.getInt());
		setBankBalance(stream.getInt());
		ownerId = stream.getLong();
		statesBitmask = stream.getLong();
		factionRank = stream.getByte();
		stream.getList((i) -> skills.add(stream.getAscii()));
		readAttributes((byte) 2, baseAttributes, stream);
	}
	
	private void readVersion3(NetBufferStream stream) {
		creo4.read(stream);
		creo6.read(stream);
		posture = Posture.valueOf(stream.getAscii());
		race = Race.valueOf(stream.getAscii());
		height = stream.getFloat();
		battleFatigue = stream.getInt();
		setCashBalance(stream.getInt());
		setBankBalance(stream.getInt());
		ownerId = stream.getLong();
		statesBitmask = stream.getLong();
		factionRank = stream.getByte();
		stream.getList((i) -> skills.add(stream.getAscii()));
		baseAttributes.read(stream);
	}
	
	private static void readAttributes(byte ver, AttributesMutable attributes, NetBufferStream stream) {
		if (ver <= 2) {
			int [] array = new int[6];
			stream.getList((i) -> array[i] = stream.getInt());
			attributes.setHealth(array[0]);
			attributes.setHealthRegen(array[1]);
			attributes.setAction(array[2]);
			attributes.setActionRegen(array[3]);
			attributes.setMind(array[4]);
			attributes.setMindRegen(array[5]);
		} else {
			attributes.read(stream);
		}
		
	}
	
	private static class Container {
		
		private final SWGObject container;
		private final String slot;
		private final int hash;
		
		public Container(SWGObject container, String slot) {
			this.container = container;
			this.slot = slot;
			this.hash = Objects.hash(container, slot);
		}
		
		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			Container container1 = (Container) o;
			return Objects.equals(container, container1.container) && Objects.equals(slot, container1.slot);
		}
		
		@Override
		public int hashCode() {
			return hash;
		}
	}
	
}
