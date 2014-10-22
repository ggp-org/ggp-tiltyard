package ggp.tiltyard.scheduling;

import external.JSON.JSONException;
import external.JSON.JSONObject;
import ggp.tiltyard.hosting.Hosting;
import ggp.tiltyard.players.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import org.ggp.galaxy.shared.persistence.Persistence;

@PersistenceCapable
public class PendingMatch {	
    @PrimaryKey @Persistent private String pendingKey;

    @Persistent private String gameURL;
    @Persistent private String[] playerCodes;    
    @Persistent private int previewClock;
    @Persistent private int startClock;
    @Persistent private int playClock;
    @Persistent private long expiresAt;
    
    public PendingMatch(String gameURL, List<String> playerCodes, int previewClock, int startClock, int playClock, long expiresAt) {
    	pendingKey = "pendingMatch." + System.currentTimeMillis();
    	
    	this.gameURL = gameURL;
    	this.playerCodes = playerCodes.toArray(new String[]{});
    	this.previewClock = previewClock;
    	this.startClock = startClock;
    	this.playClock = playClock;
    	this.expiresAt = expiresAt;
    	
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();
    }
    
    public JSONObject toJSON() throws JSONException {
    	JSONObject pending = new JSONObject();
    	pending.put("gameMetaURL", gameURL);
    	
    	List<String> playerNames = new ArrayList<String>();
    	for (int i = 0; i < playerCodes.length; i++) {
    		if (playerCodes[i].toLowerCase().equals("random")) {
    			playerNames.add("Random");
    		} else if (playerCodes[i].startsWith("tiltyard://")) {
    			playerNames.add(playerCodes[i].substring("tiltyard://".length()));
    		} else {
    			playerNames.add("");
    		}
    	}
    	pending.put("playerNames", playerNames);
    	pending.put("expiresAt", expiresAt);
    	return pending;
    }
    
    public String considerStarting(List<Player> availablePlayers) {
    	// First, abandon the match immediately if it has expired.
    	if (System.currentTimeMillis() > expiresAt) {
    		delete();
    		return null;
    	}
    	
    	Map<String,Player> playersByName = new HashMap<String,Player>();
    	for (Player p : availablePlayers) {
    		playersByName.put(p.getName(),p);
    	}
    	
    	// Determine the set of players involved with the match. There
    	// are four types of players that can participate in matches on
    	// Tiltyard: humans, random, Tiltyard-registered computers, and
    	// computers identified by URL. Each of these has a corresponding
    	// set of "player codes" that allow players in any of these four
    	// classes to be uniquely identified in a single string.
    	//
        // This code parses these player codes that can be sent to the
        // match hosting system to indicate which players to use. There
        // are four types of valid match codes:
        //
        // empty string         = a human player
        // "random"             = a random player
        // "tiltyard://foo"     = player named "foo" on Tiltyard
        // any URL              = remote player at that URL
		//
    	Set<Player> usedPlayers = new HashSet<Player>();
        List<String> playerURLs = new ArrayList<String>();
        List<String> playerNames = new ArrayList<String>();
        List<String> playerRegions = new ArrayList<String>();
        for (String code : playerCodes) {
        	if (code.isEmpty()) {
        		playerNames.add("");
        		playerURLs.add(null);
        		playerRegions.add(Player.REGION_ANY);
        	} else if (code.toLowerCase().equals("random")) {
        		playerNames.add("Random");
        		playerURLs.add(null);
        		playerRegions.add(Player.REGION_ANY);
        	} else if (code.startsWith("tiltyard://")) {
        		code = code.substring("tiltyard://".length());
        		if (playersByName.containsKey(code)) {
        			Player p = playersByName.get(code);
        			playerNames.add(p.getName());
        			playerURLs.add(p.getURL());
        			playerRegions.add(Player.REGION_ANY);
        			usedPlayers.add(p);
        		} else {
        			return null;
        		}
        	} else {
        		playerNames.add("");
        		playerURLs.add(code);
        		playerRegions.add(Player.REGION_ANY);
        	}
        }
        
    	String matchKey = Hosting.startMatch(gameURL, playerURLs, playerNames, playerRegions, previewClock, startClock, playClock);
    	if (matchKey != null) {
    		availablePlayers.removeAll(usedPlayers);
    	}
    	delete();
    	return matchKey;
    }
    
    private void delete() {
    	Persistence.clearSpecific(pendingKey, PendingMatch.class);
    }
    
    /* Static accessor methods */
    public static Set<PendingMatch> loadPendingMatches() {
        return Persistence.loadAll(PendingMatch.class);
    }
}