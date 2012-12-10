package ggp.tiltyard.scheduling;

import ggp.tiltyard.TiltyardPublicKey;
import ggp.tiltyard.players.Player;
import ggp.tiltyard.scheduling.backends.BackendRegistration;
import ggp.tiltyard.scheduling.backends.Backends;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.prodeagle.java.counters.Counter;

import util.configuration.RemoteResourceLoader;
import util.crypto.SignableJSON;

public class Scheduling {
    // Eventually we should support other repository servers. Figure out how
    // to do this in a safe, secure fashion (since the repository server can
    // inject arbitrary javascript into the visualizations).	
	private static final String GAME_REPO_URL = "http://games.ggp.org/base/"; 

    public static void runSchedulingRound() throws IOException {
        ServerState theState = ServerState.loadState();
        runSchedulingRound(theState);
        theState.save();
    }

    public static void runSchedulingRound(ServerState theState) throws IOException {        
        List<Player> theAvailablePlayers = Player.loadEnabledPlayers();
        
        Counter.increment("Tiltyard.Scheduling.Round.Started");

        // Load the ongoing matches list from the database. When this query fails,
        // retry a few times to suss out transient errors.
        JSONObject activeSet = null;
        int nQueryAttempt = 0;
        while (true) {        	
	        try {
	            activeSet = RemoteResourceLoader.loadJSON("http://database.ggp.org/query/filterActiveSet,recent,90bd08a7df7b8113a45f1e537c1853c3974006b2");
	            break;
	        } catch (Exception e) {
	        	if (nQueryAttempt > 9) {
	        		throw new RuntimeException(e);
	        	}
	        }
	        nQueryAttempt++;
        }

        try {
            JSONArray activeMatchArray = activeSet.getJSONArray("queryMatches");
            Set<String> activeMatches = new HashSet<String>();
            for (int i = 0; i < activeMatchArray.length(); i++) {
                activeMatches.add(activeMatchArray.getString(i));
            }
            theState.getRunningMatches().addAll(activeMatches);
        } catch (JSONException je) {
            throw new RuntimeException(je);
        }
        
        {
            // Find and clear all of the completed or wedged matches. For matches
            // which are still ongoing, mark the players in those matches as busy.
            Set<String> doneMatches = new HashSet<String>();
            Set<String> busyPlayerNames = new HashSet<String>();
            for (String matchURL : theState.getRunningMatches()) {
                try {
                    JSONObject theMatchInfo = RemoteResourceLoader.loadJSON(matchURL);
                    if (verifyTiltyardCryptography(theMatchInfo)) {
                        List<String> matchPlayers = new ArrayList<String>();
                        {
                          JSONArray thePlayers = theMatchInfo.getJSONArray("playerNamesFromHost");
                          for (int i = 0; i < thePlayers.length(); i++) {
                            matchPlayers.add(thePlayers.getString(i));
                          }
                        }

                        if(theMatchInfo.getBoolean("isCompleted")) {
                          doneMatches.add(matchURL);
                          handleStrikesForPlayers(theMatchInfo, matchPlayers, theAvailablePlayers);
                        } else if (System.currentTimeMillis() > theMatchInfo.getLong("startTime") + 1000L*theMatchInfo.getInt("startClock") + 256L*1000L*theMatchInfo.getInt("playClock")) {
                          // Assume the match is wedged/completed after time sufficient for 256+ moves has passed.
                          doneMatches.add(matchURL);
                        } else {
                          busyPlayerNames.addAll(matchPlayers);
                        }
                    }
                } catch (Exception e) {
                    // For some reason the match isn't recorded on the match server, or the match server
                    // is down. Just keep it around, in case it becomes available later.
                }
            }
            theState.getRunningMatches().removeAll(doneMatches);

            // For all of the players listed as enabled, record whether or not they're actually
            // identifying themselves as available. For players that are marked as pingable, we
            // only assign them matches if they don't identify themselves as busy.
            for (int i = theAvailablePlayers.size()-1; i >= 0; i--) {
                Player p = theAvailablePlayers.get(i);
                if (p.isEnabled() && p.isPingable()) {
                    String thePingError = null;
                    String thePingStatus = null;                    
                    try {
                        String theProperURL = p.getURL();
                        if (!theProperURL.startsWith("http://")) {
                            theProperURL = "http://" + theProperURL;
                        }
                        thePingStatus = RemoteResourceLoader.postRawWithTimeout(theProperURL, "( ping )", 2500);
                        thePingError = "";
                    } catch (IOException e) {
                        thePingStatus = "error";
                        thePingError = e.toString();
                    }
                    p.setPingStatus(thePingStatus, thePingError);
                    p.save();
                }
            }

            // Remove all of the players that aren't actually available, either because
            // we know they're in a match, or because they say they're busy, or because
            // they're disabled.
            for (int i = theAvailablePlayers.size()-1; i >= 0; i--) {
                Player p = theAvailablePlayers.get(i);                
                if (!p.isEnabled() || (p.isPingable() && !("available".equals(p.getPingStatus().toLowerCase()))) || busyPlayerNames.contains(p.getName())) {
                    theAvailablePlayers.remove(i);
                }
            }
        }
        
        // At this point we've gotten everything up to date, and the only thing
        // left to do is schedule new matches. If the backends are being drained,
        // we don't schedule any new matches.
        if (theState.isDrained) return;

        // Figure out how many players are available. If no players are available,
        // don't bother attempting to schedule a match.
        int readyPlayers = theAvailablePlayers.size();
        if (readyPlayers == 0) return;
        
        Counter.increment("Tiltyard.Scheduling.Round.AvailablePlayers");
        
        // Load the aggregated game metadata from the base repository server.
        int nGamesLookupAttempt = 0;
        JSONObject metadataForGames;
        while (true) {        	
	        try {
	        	metadataForGames = RemoteResourceLoader.loadJSON(GAME_REPO_URL + "games/metadata");
	            break;
	        } catch (Exception e) {
	        	if (nGamesLookupAttempt > 9) {
	        		throw new RuntimeException(e);
	        	}
	        }
	        nGamesLookupAttempt++;
        }        

        // Collect all of the games which have visualizations, which we'll call the
        // set of "proper" games that will be played on Tiltyard.
        List<String> properGameKeys = new ArrayList<String>();
        Map<String, JSONObject> properGames = new HashMap<String, JSONObject>();        
        Iterator<?> itr = metadataForGames.keys();
        while (itr.hasNext()) {
        	String key = itr.next().toString();
        	try {
	        	JSONObject gameMetadata = metadataForGames.getJSONObject(key);
	        	if (gameMetadata.has("stylesheet")) {        		
	        		properGames.put(key, gameMetadata);
	        		properGameKeys.add(key);
	        	}
        	} catch (JSONException e) {
        		throw new RuntimeException(e);
        	}
        }
        
        // Shuffle the list of known proper games, draw a game, and get ready to play.
        Collections.shuffle(properGameKeys);
        String gameKey = properGameKeys.get(0);        
        int nPlayersForGame, gameVersion;
        try {
	        nPlayersForGame = properGames.get(gameKey).getInt("numRoles");
	        gameVersion = properGames.get(gameKey).getInt("version");
        } catch (JSONException e) {
        	throw new RuntimeException(e);
        }
        String theGameURL = GAME_REPO_URL + "games/" + gameKey + "/v" + gameVersion + "/";

        // Shuffle the set of available players and then assign them to roles
        // in the game until we run out of players or roles. If we run out of
        // players, assign random players to fill the remaining roles. Lastly
        // shuffle the mapping of players to roles so that the random players
        // aren't always playing the last roles in the game.
        List<Player> theChosenPlayers = new ArrayList<Player>();
        Collections.shuffle(theAvailablePlayers);
        for (Player p : theAvailablePlayers) {
        	nPlayersForGame--;
        	theChosenPlayers.add(p);
        	if (nPlayersForGame == 0)
        		break;
        }
        while (nPlayersForGame > 0) {
        	nPlayersForGame--;
        	// null stands in for a random player.
        	theChosenPlayers.add(null);
        }
        Collections.shuffle(theChosenPlayers);
        
        // Once the final mapping of players to roles has been chosen,
        // extract the names and URLs from the players into a form that
        // will appear in the match creation request.
        int i = 0;
        String[] playerURLsForMatch = new String[theChosenPlayers.size()];
        String[] playerNamesForMatch = new String[theChosenPlayers.size()];
        for (Player p : theChosenPlayers) {        	
        	if (p == null) {
        		playerURLsForMatch[i] = "127.0.0.1:12345";
        		playerNamesForMatch[i] = "Random";
        	} else {
        		playerURLsForMatch[i] = p.getURL();
        		playerNamesForMatch[i] = p.getName();
        	}
        	i++;
        }
        
        // Choose randomized start clocks and play clocks for the match.
        // Start clocks vary between 90 seconds and 180 seconds.
        // Play clocks vary between 15 seconds and 45 seconds.
        Random theRandom = new Random();
        int startClock = 90 + 10*theRandom.nextInt(10);
        int playClock = 15 + 5*theRandom.nextInt(7);
        	
        // Construct a JSON request to the Tiltyard backend with the information
        // needed to run a match of the selected game w/ the selected players.
        JSONObject theMatchRequest = new JSONObject();
        try {
            theMatchRequest.put("startClock", startClock);
            theMatchRequest.put("playClock", playClock);
            theMatchRequest.put("gameURL", theGameURL);
            theMatchRequest.put("matchId", "tiltyard." + gameKey + "." + System.currentTimeMillis());
            theMatchRequest.put("players", playerURLsForMatch);
            theMatchRequest.put("playerNames", playerNamesForMatch);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        
        // Find a backend server to run the match. As part of this process,
        // ping all of the registered backend servers to verify that they're
        // still available, and deregister those that aren't. Lastly choose
        // randomly from the remaining ones.
        Backends theBackends = Backends.loadBackends();
        List<String> validBackends = new ArrayList<String>();
        for (String theBackendAddress : theBackends.getBackendAddresses()) {
            try {
                URL url = new URL("http://" + theBackendAddress + ":9124/" + URLEncoder.encode("ping", "UTF-8"));
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                if(!BackendRegistration.verifyBackendPing(reader.readLine())) {
                    continue;
                }
                reader.close();
            } catch (Exception e) {
                continue;
            }
            validBackends.add(theBackendAddress);
        }
        if (validBackends.size() == 0) {
            theBackends.getBackendAddresses().clear();
            theBackends.addBackendError();
            Counter.increment("Tiltyard.Scheduling.Backend.Errors");
            theBackends.save();
            return;
        }
        // TODO(schreib): Eventually this might be a good place for load balancing
        // logic, to ensure the matches-per-backend load is distributed roughly evenly
        // rather than clobbering one unlucky backend. This may also be a good place
        // for rate-limiting logic to avoid overloading backends: we can always just
        // not start new matches if all of the backends are overloaded.
        String theBackendAddress = validBackends.get(new Random().nextInt(validBackends.size()));
        theBackends.getBackendAddresses().retainAll(validBackends);
        theBackends.clearBackendErrors();
        theBackends.save();
        
        // Send the match request to the Tiltyard backend, and get back the URL
        // for the match on the spectator server.
        int nStartMatchAttempt = 0;
        String theSpectatorURL = null;
        while (true) {        	
	        try {
	            URL url = new URL("http://" + theBackendAddress + ":9124/" + URLEncoder.encode(theMatchRequest.toString(), "UTF-8"));
	          	BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
	           	theSpectatorURL = reader.readLine();
	            reader.close();
	            break;
	        } catch (Exception e) {
	        	if (nStartMatchAttempt > 9) {
	        		throw new RuntimeException(e);
	        	}
	        }
	        nStartMatchAttempt++;
        }
        if (!theSpectatorURL.equals("http://matches.ggp.org/matches/null/")) {
            theState.getRunningMatches().add(theSpectatorURL);
        }
        
        Counter.increment("Tiltyard.Scheduling.Round.Success");
    }

    private static void handleStrikesForPlayers(JSONObject theMatchInfo, List<String> players, List<Player> thePlayers) {
        if (!theMatchInfo.has("errors")) return;
        boolean[] hasLegalMove = new boolean[players.size()];
        for (int i = 0; i < hasLegalMove.length; i++) {
            hasLegalMove[i] = false;
        }
        
        try {
            JSONArray theErrors = theMatchInfo.getJSONArray("errors");
            for (int i = 0; i < theErrors.length(); i++) {
                JSONArray moveErrors = theErrors.getJSONArray(i);
                for (int j = 0; j < moveErrors.length(); j++) {
                    if (moveErrors.getString(j).isEmpty()) {
                        hasLegalMove[j] = true;
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        
        for (int i = 0; i < thePlayers.size(); i++) {            
            int nPlayerIndex = players.indexOf(thePlayers.get(i).getName());
            if (nPlayerIndex > -1) {
                if (hasLegalMove[nPlayerIndex]) {
                    thePlayers.get(i).resetStrikes();
                } else {
                    thePlayers.get(i).addStrike();
                }
                thePlayers.get(i).save();
            }
        }
    }

    public static boolean verifyTiltyardCryptography(JSONObject theMatchInfo) {
        try {
            if (!SignableJSON.isSignedJSON(new JSONObject(theMatchInfo.toString()))) return false;
            if (!SignableJSON.verifySignedJSON(new JSONObject(theMatchInfo.toString()))) return false;
            if (!theMatchInfo.getString("matchHostPK").equals(TiltyardPublicKey.theKey)) return false;
            return true;
        } catch (Exception e) {
            return false;
        }        
    }
}