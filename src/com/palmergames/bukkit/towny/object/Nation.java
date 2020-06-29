package com.palmergames.bukkit.towny.object;

import com.palmergames.bukkit.config.ConfigNodes;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.event.NationAddTownEvent;
import com.palmergames.bukkit.towny.event.NationRemoveTownEvent;
import com.palmergames.bukkit.towny.event.NationTagChangeEvent;
import com.palmergames.bukkit.towny.exceptions.AlreadyRegisteredException;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.exceptions.EmptyNationException;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.invites.Invite;
import com.palmergames.bukkit.towny.invites.InviteHandler;
import com.palmergames.bukkit.towny.invites.exceptions.TooManyInvitesException;
import com.palmergames.bukkit.towny.object.metadata.CustomDataField;
import com.palmergames.bukkit.towny.permissions.TownyPerms;
import com.palmergames.bukkit.towny.war.siegewar.SiegeWarMembershipController;
import com.palmergames.bukkit.towny.war.siegewar.objects.Siege;
import com.palmergames.bukkit.towny.war.siegewar.enums.SiegeStatus;
import com.palmergames.bukkit.towny.war.flagwar.FlagWar;
import com.palmergames.bukkit.util.BukkitTools;
import com.palmergames.util.StringMgmt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class Nation extends TownyObject implements ResidentList, TownyInviter, Bank {

	private static final String ECONOMY_ACCOUNT_PREFIX = TownySettings.getNationAccountPrefix();

	//private List<Resident> assistants = new ArrayList<Resident>();
	private List<Town> towns = new ArrayList<>();
	private List<Nation> allies = new ArrayList<>();
	private List<Nation> enemies = new ArrayList<>();
	private List<Siege> sieges = new ArrayList<>();
	private Town capital;
	private double taxes, spawnCost;
	private boolean neutral = false;
	private String nationBoard = TownySettings.getNationDefaultBoard();
	private String mapColorHexCode = "";
	private String tag = "";
	public UUID uuid;
	private long registered;
	private Location nationSpawn;
	private boolean isPublic = TownySettings.getNationDefaultPublic();
	private boolean isOpen = TownySettings.getNationDefaultOpen();
	private transient List<Invite> receivedinvites = new ArrayList<>();
	private transient List<Invite> sentinvites = new ArrayList<>();
	private transient List<Invite> sentallyinvites = new ArrayList<>();
	private transient EconomyAccount account;

	public Nation(String name) {
		super(name);
	}

	public void setTag(String text) throws TownyException {

		if (text.length() > 4) {
			throw new TownyException(TownySettings.getLangString("msg_err_tag_too_long"));
		}
		this.tag = text.toUpperCase().trim();
		Bukkit.getPluginManager().callEvent(new NationTagChangeEvent(this, this.tag));
	}

	public String getTag() {

		return tag;
	}

	public boolean hasTag() {

		return !tag.isEmpty();
	}

	public void addAlly(Nation nation) throws AlreadyRegisteredException {

		if (hasAlly(nation))
			throw new AlreadyRegisteredException();
		else {
			try {
				removeEnemy(nation);
			} catch (NotRegisteredException ignored) {}
			getAllies().add(nation);
		}
	}

	public boolean removeAlly(Nation nation) throws NotRegisteredException {

		if (!hasAlly(nation))
			throw new NotRegisteredException();
		else {
			boolean result = getAllies().remove(nation);

			if(result && TownySettings.getWarSiegeEnabled())
				SiegeWarMembershipController.evaluateNationRemoveAlly(this, nation);

			return result;
		}
	}

	public boolean removeAllAllies() {

		for (Nation ally : new ArrayList<>(getAllies()))
			try {
				removeAlly(ally);
				ally.removeAlly(this);
			} catch (NotRegisteredException ignored) {}
		return getAllies().size() == 0;
	}

	public boolean hasAlly(Nation nation) {

		return getAllies().contains(nation);
	}

	public boolean hasMutualAlly(Nation nation) {
		return (getAllies().contains(nation) && nation.getAllies().contains(this));
	}

	public boolean IsAlliedWith(Nation nation) {

		return getAllies().contains(nation);
	}

	public void addEnemy(Nation nation) throws AlreadyRegisteredException {

		if (hasEnemy(nation))
			throw new AlreadyRegisteredException();
		else {
			try {
				removeAlly(nation);
			} catch (NotRegisteredException ignored) {}
			getEnemies().add(nation);
		}

	}

	public boolean removeEnemy(Nation nation) throws NotRegisteredException {

		if (!hasEnemy(nation))
			throw new NotRegisteredException();
		else
			return getEnemies().remove(nation);
	}

	public boolean removeAllEnemies() {

		for (Nation enemy : new ArrayList<>(getEnemies()))
			try {
				removeEnemy(enemy);
				enemy.removeEnemy(this);
			} catch (NotRegisteredException ignored) {}
		return getAllies().size() == 0;
	}

	public boolean hasEnemy(Nation nation) {

		return getEnemies().contains(nation);
	}

	public List<Town> getTowns() {

		return towns;
	}

	public boolean isKing(Resident resident) {

		return hasCapital() && getCapital().isMayor(resident);
	}

	public boolean hasCapital() {

		return getCapital() != null;
	}

	public boolean hasAssistant(Resident resident) {

		return getAssistants().contains(resident);
	}

	public boolean isCapital(Town town) {

		return town == getCapital();
	}

	public boolean hasTown(String name) {

		for (Town town : towns)
			if (town.getName().equalsIgnoreCase(name))
				return true;
		return false;
	}

	public boolean hasTown(Town town) {

		return towns.contains(town);
	}

	public void addTown(Town town) throws AlreadyRegisteredException {

		if (hasTown(town))
			throw new AlreadyRegisteredException();
		else if (town.hasNation())
			throw new AlreadyRegisteredException();
		else {
			towns.add(town);
			town.setNation(this);
			
			BukkitTools.getPluginManager().callEvent(new NationAddTownEvent(town, this));
		}
	}

	public void setCapital(Town capital) {

		this.capital = capital;
		try {
			TownyPerms.assignPermissions(capital.getMayor(), null);
		} catch (Exception e) {
			// Dummy catch to prevent errors on startup when setting nation.
		}
		//TownyUniverse.getInstance().getDataSource().saveNation(this);
	}

	public Town getCapital() {

		return capital;
	}

	public Location getNationSpawn() throws TownyException {
		if(nationSpawn == null){
			throw new TownyException(TownySettings.getLangString("msg_err_nation_has_not_set_a_spawn_location"));
		}

		return nationSpawn;
	}

	public boolean hasNationSpawn(){
		return (nationSpawn != null);
	}

	public void setNationSpawn(Location spawn) throws TownyException {
		if (TownyAPI.getInstance().isWilderness(spawn))
			throw new TownyException(String.format(TownySettings.getLangString("msg_cache_block_error_wild"), "set spawn"));

		TownBlock townBlock = TownyAPI.getInstance().getTownBlock(spawn);

		if(TownySettings.getBoolean(ConfigNodes.GNATION_SETTINGS_CAPITAL_SPAWN)){
			if(this.capital == null){
				throw new TownyException(TownySettings.getLangString("msg_err_spawn_not_within_capital"));
			}
			if(!townBlock.hasTown()){
				throw new TownyException(TownySettings.getLangString("msg_err_spawn_not_within_capital"));
			}
			if(townBlock.getTown() != this.getCapital()){
				throw new TownyException(TownySettings.getLangString("msg_err_spawn_not_within_capital"));
			}
		} else {
			if(!townBlock.hasTown()){
				throw new TownyException(TownySettings.getLangString("msg_err_spawn_not_within_nationtowns"));
			}

			if(!towns.contains(townBlock.getTown())){
				throw new TownyException(TownySettings.getLangString("msg_err_spawn_not_within_nationtowns"));
			}
		}

		this.nationSpawn = spawn;
	}

	/**
	 * Only to be called from the Loading methods.
	 *
	 * @param nationSpawn - Location to set as Nation Spawn
	 */
	public void forceSetNationSpawn(Location nationSpawn){
		this.nationSpawn = nationSpawn;
	}

	//TODO: Remove
	public boolean setAllegiance(String type, Nation nation) {

		try {
			if (type.equalsIgnoreCase("ally")) {
				removeEnemy(nation);
				addAlly(nation);
				if (!hasEnemy(nation) && hasAlly(nation))
					return true;
			} else if (type.equalsIgnoreCase("peaceful") || type.equalsIgnoreCase("neutral")) {
				removeEnemy(nation);
				removeAlly(nation);
				if (!hasEnemy(nation) && !hasAlly(nation))
					return true;
			} else if (type.equalsIgnoreCase("enemy")) {
				removeAlly(nation);
				addEnemy(nation);
				if (hasEnemy(nation) && !hasAlly(nation))
					return true;
			}
		} catch (AlreadyRegisteredException | NotRegisteredException x) {
			return false;
		}
		
		return false;
	}

	public List<Resident> getAssistants() {

		List<Resident> assistants = new ArrayList<>();
		
		for (Town town: towns)
		for (Resident assistant: town.getResidents()) {
			if (assistant.hasNationRank("assistant"))
				assistants.add(assistant);
		}
		return assistants;
	}

	public void setEnemies(List<Nation> enemies) {

		this.enemies = enemies;
	}

	public List<Nation> getEnemies() {

		return enemies;
	}

	public void setAllies(List<Nation> allies) {

		this.allies = allies;
	}

	public List<Nation> getAllies() {

		return allies;
	}

	public List<Nation> getMutualAllies() {
		List<Nation> result = new ArrayList<>();
		for(Nation ally: getAllies()) {
			if(ally.hasAlly(this))
				result.add(ally);
		}
		return result;
	}


	public int getNumTowns() {

		return towns.size();
	}

	public int getNumResidents() {

		int numResidents = 0;
		for (Town town : getTowns())
			numResidents += town.getNumResidents();
		return numResidents;
	}

	public void removeTown(Town town) throws EmptyNationException, NotRegisteredException {

		if (!hasTown(town))
			throw new NotRegisteredException();
		else {

			boolean isCapital = town.isCapital();

			if(TownySettings.getWarSiegeEnabled())
				SiegeWarMembershipController.evaluateNationRemoveTown(town);

			remove(town);
			TownyUniverse.getInstance().getDataSource().saveNation(this);

			if (getNumTowns() == 0) {
				throw new EmptyNationException(this);
			} else if (isCapital) {
				int numResidents = 0;
				Town tempCapital = null;
				for (Town newCapital : getTowns())
					if (newCapital.getNumResidents() > numResidents) {
						tempCapital = newCapital;
						numResidents = newCapital.getNumResidents();
					}

				if (tempCapital != null) {
					setCapital(tempCapital);
					TownyUniverse.getInstance().getDataSource().saveNation(this);
				}

			}
		}
	}

	private void remove(Town town) {

		//removeAssistantsIn(town);
		try {
			town.setNation(null);
		} catch (AlreadyRegisteredException ignored) {
		}

		//Reset occupation to false
		town.setOccupied(false);

		/*
		 * Remove all resident titles/nationRanks before saving the town itself.
		 */
		List<Resident> titleRemove = new ArrayList<>(town.getResidents());

		for (Resident res : titleRemove) {
			if (res.hasTitle() || res.hasSurname()) {
				res.setTitle("");
				res.setSurname("");
			}
			res.updatePermsForNationRemoval(); // Clears the nationRanks.
			TownyUniverse.getInstance().getDataSource().saveResident(res);
		}
		
		towns.remove(town);
		
		BukkitTools.getPluginManager().callEvent(new NationRemoveTownEvent(town, this));
	}

	public void addSiege(Siege siege) {
		sieges.add(siege);
	}
	
	public void removeSiege(Siege siege) {
		sieges.remove(siege);
	}

	private void removeAllTowns() {

		for (Town town : new ArrayList<>(towns))
			remove(town);
	}

	private void removeAllSieges() {

		for (Siege siege : new ArrayList<>(sieges))
			sieges.remove(siege);
	}

	public void setTaxes(double taxes) {
		this.taxes = Math.min(taxes, TownySettings.getMaxNationTax());
	}

	public double getTaxes() {

		setTaxes(taxes); //make sure the tax level is right.
		return taxes;
	}

	public void clear() {

		//TODO: Check cleanup
		removeAllAllies();
		removeAllEnemies();
		removeAllTowns();
		removeAllSieges();
		capital = null;
	}

	/**
	 * Method for rechecking town distances to a new nation capital/moved 
	 * nation capital homeblock. Results in towns whose homeblocks are no 
	 * longer close enough to the capital homeblock being removed from 
	 * the nation.
	 * 
	 * @throws TownyException - Generic TownyException
	 */
	public void recheckTownDistance() throws TownyException {
		if(capital != null) {
			if (TownySettings.getNationRequiresProximity() > 0) {
				final Coord capitalCoord = capital.getHomeBlock().getCoord();
				Iterator<Town> it = towns.iterator();
				while(it.hasNext()) {
					Town town = it.next();
					Coord townCoord = town.getHomeBlock().getCoord();
					if (!capital.getHomeBlock().getWorld().getName().equals(town.getHomeBlock().getWorld().getName())) {
						it.remove();
						continue;
					}

					final double distance = Math.sqrt(Math.pow(capitalCoord.getX() - townCoord.getX(), 2) + Math.pow(capitalCoord.getZ() - townCoord.getZ(), 2));
					if (distance > TownySettings.getNationRequiresProximity()) {
						TownyMessaging.sendPrefixedTownMessage(town, String.format(TownySettings.getLangString("msg_town_left_nation"), this.getName()));
						TownyMessaging.sendPrefixedNationMessage(this, String.format(TownySettings.getLangString("msg_nation_town_left"), town.getName()));
						this.remove(town);
						it.remove();
					}
				}
			}
		}
	}
	
	/**
	 * A dry-run method for rechecking town distances to a new nation capital/
	 * moved nation capital homeblock.
	 * 
	 * @param towns - The list of towns to check.
	 * @throws TownyException - Generic TownyException
	 * @return removedTowns - A list of Towns which would be removed under a real recheckTownDistance().
	 */
	public List<Town> recheckTownDistanceDryRun(List<Town> towns) throws TownyException {
		List<Town> removedTowns = new ArrayList<>();
		if(capital != null) {
			if (TownySettings.getNationRequiresProximity() > 0) {
				final Coord capitalCoord = capital.getHomeBlock().getCoord();
				
				for (Town town : towns) {
					Coord townCoord = town.getHomeBlock().getCoord();
					if (!capital.getHomeblockWorld().equals(town.getHomeblockWorld())) {
						continue;
					}
					final double distance = Math.sqrt(Math.pow(capitalCoord.getX() - townCoord.getX(), 2) + Math.pow(capitalCoord.getZ() - townCoord.getZ(), 2));
					if (distance > TownySettings.getNationRequiresProximity()) {
						removedTowns.add(town);
					}
				}
			}
		}
		return removedTowns;
	}	

	public void setNeutral(boolean neutral) throws TownyException {

		if (!TownySettings.isDeclaringNeutral() && neutral)
			throw new TownyException(TownySettings.getLangString("msg_err_fight_like_king"));
		else {
			if (neutral) {
				for (Resident resident : getResidents()) {
					FlagWar.removeAttackerFlags(resident.getName());
				}
			}
			this.neutral = neutral;
		}
	}

	public boolean isNeutral() {

		return neutral;
	}

	public void setKing(Resident king) throws TownyException {

		if (!hasResident(king))
			throw new TownyException(TownySettings.getLangString("msg_err_king_not_in_nation"));
		if (!king.isMayor())
			throw new TownyException(TownySettings.getLangString("msg_err_new_king_notmayor"));
		setCapital(king.getTown());
		TownyUniverse.getInstance().getDataSource().saveNation(this);
	}

	public boolean hasResident(Resident resident) {

		for (Town town : getTowns())
			if (town.hasResident(resident))
				return true;
		return false;
	}
	
	public void collect(double amount) throws EconomyException {
		
		if (TownySettings.isUsingEconomy()) {
			double bankcap = TownySettings.getNationBankCap();
			if (bankcap > 0) {
				if (amount + this.getAccount().getHoldingBalance() > bankcap) {
					TownyMessaging.sendPrefixedNationMessage(this, String.format(TownySettings.getLangString("msg_err_deposit_capped"), bankcap));
					return;
				}
			}
			
			this.getAccount().collect(amount, null);
		}

	}

	@Override
	public void withdrawFromBank(Resident resident, int amount) throws EconomyException, TownyException {

		//if (!isKing(resident))// && !hasAssistant(resident))
		//	throw new TownyException(TownySettings.getLangString("msg_no_access_nation_bank"));

		if (TownySettings.isUsingEconomy()) {
			if (!getAccount().payTo(amount, resident, "Nation Withdraw"))
				throw new TownyException(TownySettings.getLangString("msg_err_no_money"));
		} else
			throw new TownyException(TownySettings.getLangString("msg_err_no_economy"));
	}

	@Override
	public List<Resident> getResidents() {

		List<Resident> out = new ArrayList<>();
		for (Town town : getTowns())
			out.addAll(town.getResidents());
		return out;
	}

	@Override
	public List<String> getTreeString(int depth) {

		List<String> out = new ArrayList<>();
		out.add(getTreeDepth(depth) + "Nation (" + getName() + ")");
		out.add(getTreeDepth(depth + 1) + "Capital: " + getCapital().getName());
		
		List<Resident> assistants = getAssistants();
		
		if (assistants.size() > 0)
			out.add(getTreeDepth(depth + 1) + "Assistants (" + assistants.size() + "): " + Arrays.toString(assistants.toArray(new Resident[0])));
		if (getAllies().size() > 0)
			out.add(getTreeDepth(depth + 1) + "Allies (" + getAllies().size() + "): " + Arrays.toString(getAllies().toArray(new Nation[0])));
		if (getEnemies().size() > 0)
			out.add(getTreeDepth(depth + 1) + "Enemies (" + getEnemies().size() + "): " + Arrays.toString(getEnemies().toArray(new Nation[0])));
		out.add(getTreeDepth(depth + 1) + "Towns (" + getTowns().size() + "):");
		for (Town town : getTowns())
			out.addAll(town.getTreeString(depth + 2));
		return out;
	}

	@Override
	public boolean hasResident(String name) {

		for (Town town : getTowns())
			if (town.hasResident(name))
				return true;
		return false;
	}

	@Override
	public List<Resident> getOutlaws() {

		List<Resident> out = new ArrayList<>();
		for (Town town : getTowns())
			out.addAll(town.getOutlaws());
		return out;
	}

	public UUID getUuid() {
		return uuid;
	}

	public void setUuid(UUID uuid) {
		this.uuid = uuid;
	}

	public boolean hasValidUUID() {
		return uuid != null;
	}

	public long getRegistered() {
		return registered;
	}

	public void setRegistered(long registered) {
		this.registered = registered;
	}

	@Override
	public List<Invite> getReceivedInvites() {
		return receivedinvites;
	}

	@Override
	public void newReceivedInvite(Invite invite) throws TooManyInvitesException {
		if (receivedinvites.size() <= (InviteHandler.getReceivedInvitesMaxAmount(this) -1)) {
			receivedinvites.add(invite);
		} else {
			throw new TooManyInvitesException(String.format(TownySettings.getLangString("msg_err_nation_has_too_many_requests"),this.getName()));
		}
	}

	@Override
	public void deleteReceivedInvite(Invite invite) {
		receivedinvites.remove(invite);
	}

	@Override
	public List<Invite> getSentInvites() {
		return sentinvites;
	}

	@Override
	public void newSentInvite(Invite invite) throws TooManyInvitesException {
		if (sentinvites.size() <= (InviteHandler.getSentInvitesMaxAmount(this) -1)) {
			sentinvites.add(invite);
		} else {
			throw new TooManyInvitesException(TownySettings.getLangString("msg_err_nation_sent_too_many_invites"));
		}
	}

	@Override
	public void deleteSentInvite(Invite invite) {
		sentinvites.remove(invite);
	}
	
	public void newSentAllyInvite(Invite invite) throws TooManyInvitesException {
		if (sentallyinvites.size() <= InviteHandler.getSentAllyRequestsMaxAmount(this) -1) {
			sentallyinvites.add(invite);
		} else {
			throw new TooManyInvitesException(TownySettings.getLangString("msg_err_nation_sent_too_many_requests"));
		}
	}
	
	public void deleteSentAllyInvite(Invite invite) {
		sentallyinvites.remove(invite);
	}
	
	public List<Invite> getSentAllyInvites() {
		return sentallyinvites;
	}
	
	public void setNationBoard(String nationBoard) {

		this.nationBoard = nationBoard;
	}

	public String getNationBoard() {
		return nationBoard;
	}

    public void setPublic(boolean isPublic) {

        this.isPublic = isPublic;
    }

    public boolean isPublic() {

        return isPublic;
    }
    
    public void setOpen(boolean isOpen) {
    	
    	this.isOpen = isOpen;
    }
    
    public boolean isOpen() {
    	
    	return isOpen;
    }
    
	public void setSpawnCost(double spawnCost) {

		this.spawnCost = spawnCost;
	}

	public double getSpawnCost() {

		return spawnCost;
	}
	
	public List<Town> getTownsUnderSiegeAttack() {
		List<Town> result = new ArrayList<>();
		for(Siege siege: sieges) {
			result.add(siege.getDefendingTown());
		}
		return result;
	}

	//Note - Do not return a town if our nation just invaded it
	public List<Town> getTownsUnderSiegeDefence() {
		List<Town> result = new ArrayList<Town>();
		for(Town town: towns) {
			if(town.hasSiege() && town.getSiege().getAttackingNation() != this) {
				result.add(town);
			}
		}
		return result;
	}

	public List<Siege> getActiveAttackSieges() {
		List<Siege> result = new ArrayList<>();
		for(Siege siege: sieges) {
			if(siege.getStatus().isActive()) {
				result.add(siege);
			}
		}
		return result;
	}

	public List<Siege> getActiveDefenceSieges(Town townToExclude) {
		List<Siege> result = new ArrayList<>();
		for(Town town: towns) {
			if(town == townToExclude)
				continue;

			if(town.hasSiege() && town.getSiege().getStatus().isActive()) {
				result.add(town.getSiege());
			}
		}
		return result;
	}

	public int getNumActiveAttackSieges() {
		int result = 0;
		for(Siege siege: sieges) {
			if(siege.getStatus().isActive()) 
				result++;
		}
		return result;
	}

	public boolean isNationAttackingTown(Town town) {
		return town.hasSiege()
				&& town.getSiege().getStatus().isActive()
				&& town.getSiege().getAttackingNation() == this;
	}

	public List<Siege> getSieges() {
		return sieges;
	}

	public int getNumTownblocks() {
		int townBlocksClaimed = 0;
		for (Town towns : this.getTowns()) {
			townBlocksClaimed = townBlocksClaimed + towns.getTownBlocks().size();
		}
		return townBlocksClaimed;
	}
	
	public Resident getKing() {
		return capital.getMayor();
	}

	@Override
	public String getFormattedName() {
		return TownySettings.getNationPrefix(this) + this.getName().replaceAll("_", " ")
			+ TownySettings.getNationPostfix(this);
	}
	
	@Override
	public void addMetaData(CustomDataField<?> md) {
		super.addMetaData(md);

		TownyUniverse.getInstance().getDataSource().saveNation(this);
	}

	public void removeMetaData(CustomDataField md) {
		super.removeMetaData(md);

		TownyUniverse.getInstance().getDataSource().saveNation(this);
	}

	@Override
	public EconomyAccount getAccount() {

		if (account == null) {

			String accountName = StringMgmt.trimMaxLength(Nation.ECONOMY_ACCOUNT_PREFIX + getName(), 32);
			World world;

			if (hasCapital() && getCapital().hasWorld()) {
				world = BukkitTools.getWorld(getCapital().getHomeblockWorld().getName());
			} else {
				world = BukkitTools.getWorlds().get(0);
			}

			account = new EconomyAccount(accountName, world);
		}

		
		return account;
	}

	/**
	 * Shows if the nation is allied with the specified nation.
	 * 
	 * @param nation The nation that is allied.
	 * @return true if it is allied, false otherwise.
	 */
	public boolean isAlliedWith(Nation nation) {
		return allies.contains(nation);
	}

	/**
	 * @deprecated As of 0.96.0.0+ please use {@link EconomyAccount#getWorld()} instead.
	 *
	 * @return The world this resides in.
	 */
	@Deprecated
	public World getBukkitWorld() {
		if (hasCapital() && getCapital().hasWorld()) {
			return BukkitTools.getWorld(getCapital().getHomeblockWorld().getName());
		} else {
			return BukkitTools.getWorlds().get(0);
		}
	}

	/**
	 * @deprecated As of 0.96.0.0+ please use {@link EconomyAccount#getName()} instead.
	 *
	 * @return The name of the economy account.
	 */
	@Deprecated
	public String getEconomyName() {
		return StringMgmt.trimMaxLength(Nation.ECONOMY_ACCOUNT_PREFIX + getName(), 32);
	}
	
	
	/**
	 * @deprecated as of 0.95.2.15, please use {@link EconomyAccount#getHoldingBalance()} instead.
	 * 
	 * @return the holding balance of the economy account.
	 * @throws EconomyException When an economy error occurs
	 */
	@Deprecated
	public double getHoldingBalance() throws EconomyException {
		try {
			return getAccount().getHoldingBalance();
		} catch (NoClassDefFoundError e) {
			e.printStackTrace();
			throw new EconomyException("Economy error getting holdings for " + getEconomyName());
		}
	}

	/**
	 * @deprecated As of 0.95.1.15, please use {@link EconomyAccount#pay(double, String)} instead.
	 *
	 * @param amount value to deduct from the player's account
	 * @param reason leger memo stating why amount is deducted
	 * @return true if successful
	 * @throws EconomyException if the transaction fails
	 */
	@Deprecated
	public boolean pay(double amount, String reason) throws EconomyException {
		return getAccount().pay(amount, reason);
	}

	/**
	 * @deprecated As of 0.95.1.15, please use {@link EconomyAccount#collect(double, String)} instead.
	 *
	 * @param amount currency to collect
	 * @param reason memo regarding transaction
	 * @return collected or pay to server account   
	 * @throws EconomyException if transaction fails
	 */
	@Deprecated
	public boolean collect(double amount, String reason) throws EconomyException {
		return getAccount().collect(amount, reason);
	}

	public String getMapColorHexCode() {
		return mapColorHexCode;
	}

	public void setMapColorHexCode(String mapColorHexCode) {
		this.mapColorHexCode = mapColorHexCode;
	}
}
