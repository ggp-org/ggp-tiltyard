package ggp.apollo.scheduling;

import ggp.apollo.TiltyardPublicKey;
import ggp.apollo.players.Player;
import ggp.apollo.scheduling.backends.BackendRegistration;
import ggp.apollo.scheduling.backends.Backends;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import util.configuration.RemoteResourceLoader;
import util.crypto.SignableJSON;

public class Scheduling {
    // Comment out games that are expensive for AppEngine-based players.
    private static final String[] someProperGames = {
            //"2pttc:2:v1",
//            "3pttc:3:v1",
            "3pConnectFour:3:v0",
//            "4pttc:4:v1",
            "blocker:2:v0",
            //"breakthrough:2:v0",
            "breakthroughSmall:2:v0",
            //"breakthroughWalls:2:v0",
            "biddingTicTacToe:2:v0",
//            //"chess:2",
            //"checkers:2:v1",
            "cittaceot:2:v0",
            "connectFour:2:v0",
            "connectFourSuicide:2:v0",
            //"dualConnect4:2:v0",
            //"eightPuzzle:1:v0",
            //"escortLatch:2:v0",
            "knightThrough:2:v0",
            //"knightsTour:1:v0",
//            //"pawnToQueen:2",
//            //"pawnWhopping:2",
            //"peg:1:v0",
            //"pegEuro:1:v0",
//            //"lightsOut:1",
            //"2pffa_zerosum:2:v0",
            //"qyshinsu:2:v0",
            //"sheepAndWolf:2:v0",
            "nineBoardTicTacToe:2:v0",
//            "snakeAssemblit:2:v0",
            //"ttcc4_2player:2:v0",
            "ticTacToe:2:v0"
//            "withConviction:2:v0"
    };    

    public static void runSchedulingRound() throws IOException {
        ServerState theState = ServerState.loadState();
        runSchedulingRound(theState);
        theState.save();
    }

    public static void runSchedulingRound(ServerState theState) throws IOException {
        List<Player> theAvailablePlayers = Player.loadEnabledPlayers();

        {
            // Find and clear all of the completed or wedged matches. For matches
            // which are still ongoing, mark the players in those matches as busy.
            Set<String> doneMatches = new HashSet<String>();
            Set<String> busyPlayerNames = new HashSet<String>();
            for (String matchURL : theState.getRunningMatches()) {
                try {
                    JSONObject theMatchInfo = RemoteResourceLoader.loadJSON(matchURL);
                    if (verifyApolloCryptography(theMatchInfo)) {
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
            // identifying themselves as available. Right now we do nothing with this information
            // except surface it in the UI.
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
                if (!p.isEnabled() || (p.isPingable() && !("available".equals(p.getPingStatus()))) || busyPlayerNames.contains(p.getName())) {
                    theAvailablePlayers.remove(i);
                }
            }
        }

        // Figure out how many players are available. If no players are available,
        // don't bother attempting to schedule a match.
        int readyPlayers = theAvailablePlayers.size();
        if (readyPlayers < 2) {
            return;
        }
        
        // Shuffle the list of known proper games, draw a game, and check whether
        // we have enough players available to play it. Repeat until we have a game.
        int nPlayersForGame;
        String theGameKey = null;
        String theGameVersion = null;
        List<String> theProperGames = Arrays.asList(someProperGames);
        do {
            Collections.shuffle(theProperGames);            
            nPlayersForGame = Integer.parseInt(theProperGames.get(0).split(":")[1]);
            if (readyPlayers >= nPlayersForGame){
                theGameKey = theProperGames.get(0).split(":")[0];
                theGameVersion = theProperGames.get(0).split(":")[2];
            }
        } while (theGameKey == null);

        // Eventually we should support other repository servers. Figure out how
        // to do this in a safe, secure fashion (since the repository server can
        // inject arbitrary javascript into the visualizations).
        String theGameURL = "http://games.ggp.org/base/games/" + theGameKey + "/" + theGameVersion + "/";

        // Assign available players to roles in the game.
        Collections.shuffle(theAvailablePlayers);
        String[] playerURLsForMatch = new String[nPlayersForGame];
        List<String> playerNamesForMatch = new ArrayList<String>();
        Set<Player> playersForMatch = new HashSet<Player>();
        for (Player p : theAvailablePlayers) {
            nPlayersForGame--;
            playerURLsForMatch[nPlayersForGame] = p.getURL();
            playerNamesForMatch.add(0,p.getName());
            playersForMatch.add(p);
                        
            if (nPlayersForGame == 0)
                break;
        }

        // Construct a JSON request to the Apollo backend with the information
        // needed to run a match of the selected game w/ the selected players.
        JSONObject theMatchRequest = new JSONObject();
        try {
            theMatchRequest.put("startClock", 120);
            theMatchRequest.put("playClock", 20);
            theMatchRequest.put("gameURL", theGameURL);
            theMatchRequest.put("matchId", "apollo." + theGameKey + "." + System.currentTimeMillis());
            theMatchRequest.put("players", playerURLsForMatch);
            theMatchRequest.put("playerNames", playerNamesForMatch);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        
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
            theBackends.save();
            return;
        }
        String theBackendAddress = validBackends.get(new Random().nextInt(validBackends.size()));
        theBackends.getBackendAddresses().retainAll(validBackends);
        theBackends.clearBackendErrors();
        theBackends.save();

        // Send the match request to the Apollo backend, and get back the URL
        // for the match on the spectator server.
        String theSpectatorURL = null;
        try {
            URL url = new URL("http://" + theBackendAddress + ":9124/" + URLEncoder.encode(theMatchRequest.toString(), "UTF-8"));
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            theSpectatorURL = reader.readLine();
            reader.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (!theSpectatorURL.equals("http://matches.ggp.org/matches/null/")) {
            theState.getRunningMatches().add(theSpectatorURL);
        }        
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

    public static boolean verifyApolloCryptography(JSONObject theMatchInfo) {
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