package ggp.apollo.scheduling;

import ggp.apollo.CondensedMatch;
import ggp.apollo.Game;
import ggp.apollo.Player;
import ggp.apollo.ServerState;

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
import java.util.Set;

import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

import util.configuration.RemoteResourceLoader;
import util.crypto.SignableJSON;

public class Scheduling {
    // Comment out games that are expensive for AppEngine-based players.
    private static final String[] someProperGames = {
            "2pttc:2",
            "3pttc:3",
            "3pConnectFour:3",
            "4pttc:4",
            "blocker:2",
            "breakthrough:2",
            "breakthroughSmall:2",
            "breakthroughWalls:2",
            "biddingTicTacToe:2",
            //"chess:2",
            "checkers:2",
            "cittaceot:2",
            "connectFour:2",
            "connectFourSuicide:2",            
            //"eightPuzzle:1",
            "escortLatch:2",
            "knightThrough:2",
            //"knightsTour:1",
            //"pawnToQueen:2",
            //"pawnWhopping:2",
            //"peg:1",
            //"pegEuro:1",
            //"lightsOut:1",
            "2pffa_zerosum:2",
            "qyshinsu:2",
            "sheepAndWolf:2",
            "nineBoardTicTacToe:2",
            "ttcc4_2player:2",
            "ticTacToe:2"
    };    

    public static void runSchedulingRound() throws IOException {
        ServerState theState = ServerState.loadState();
        theState.incrementSchedulingRound();
        runSchedulingRound(theState);
        theState.save();
    }

    public static void runSchedulingRound(ServerState theState) throws IOException {
        List<Player> theAvailablePlayers = new ArrayList<Player>(Player.loadPlayers());

        {
            // Find and clear all of the completed or wedged matches. For matches
            // which are still ongoing, mark the players in those matches as busy.
            Set<String> doneMatches = new HashSet<String>();
            Set<String> busyPlayerNames = new HashSet<String>();
            for (String matchKey : theState.getRunningMatches()) {
                CondensedMatch m = CondensedMatch.loadCondensedMatch(matchKey);                                
                try {
                    JSONObject theMatchInfo = RemoteResourceLoader.loadJSON(m.getSpectatorURL());
                    if (verifyApolloCryptography(theMatchInfo)) {
                        if(theMatchInfo.getBoolean("isCompleted")) {
                            doneMatches.add(matchKey);
                            handleStrikesForPlayers(theMatchInfo, m.getPlayers(), theAvailablePlayers);
                        } else if (System.currentTimeMillis() > theMatchInfo.getLong("startTime") + 1000L*theMatchInfo.getInt("startClock") + 256L*1000L*theMatchInfo.getInt("playClock")) {
                            // Assume the match is wedged/completed after time sufficient for 256+ moves has passed.
                            doneMatches.add(matchKey);
                        } else {
                            busyPlayerNames.addAll(m.getPlayers());
                        }
                        m.condenseFullJSON(theMatchInfo);
                        m.save();
                    }
                } catch (Exception e) {
                    if (m.getCreationDate() != null && (System.currentTimeMillis() - m.getCreationDate().getTime()) > 21600000) {
                        doneMatches.add(matchKey);
                    } else {
                        // For some reason the match isn't recorded on the match server, or the match server
                        // is down. We'll wait for six hours and then assume the match is done, leaving our condensed
                        // version of the match empty but freeing up the players.
                    }
                }
            }
            theState.getRunningMatches().removeAll(doneMatches);

            // For all of the players listed as enabled, record whether or not they're actually
            // identifying themselves as available. Right now we do nothing with this information
            // except surface it in the UI.
            for (int i = theAvailablePlayers.size()-1; i >= 0; i--) {
                Player p = theAvailablePlayers.get(i);
                if (p.isEnabled()) {
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
                if (!p.isEnabled() || !("available".equals(p.getPingStatus())) || busyPlayerNames.contains(p.getName())) {
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
        List<String> theProperGames = Arrays.asList(someProperGames);
        do {
            Collections.shuffle(theProperGames);            
            nPlayersForGame = Integer.parseInt(theProperGames.get(0).split(":")[1]);
            if (readyPlayers >= nPlayersForGame){
                theGameKey = theProperGames.get(0).split(":")[0];
            }
        } while (theGameKey == null);

        // Eventually we should support other repository servers. Figure out how
        // to do this in a safe, secure fashion (since the repository server can
        // inject arbitrary javascript into the visualizations).
        String theGameURL = "http://games.ggp.org/games/" + theGameKey + "/v0/";
        Game theGame = Game.loadGame(theGameURL);
        if (theGame == null) {
            theGame = new Game(theGameURL);
        }

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
            theMatchRequest.put("startClock", 45);
            theMatchRequest.put("playClock", 15);
            theMatchRequest.put("gameURL", theGameURL);
            theMatchRequest.put("matchId", "apollo." + theGameKey + "." + System.currentTimeMillis());
            theMatchRequest.put("players", playerURLsForMatch);
            theMatchRequest.put("playerNames", playerNamesForMatch);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        // Send the match request to the Apollo backend, and get back the URL
        // for the match on the spectator server.
        String theSpectatorURL = null;
        try {
            URL url = new URL(theState.getBackendAddress() + URLEncoder.encode(theMatchRequest.toString(), "UTF-8"));
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            theSpectatorURL = reader.readLine();
            reader.close();
        } catch (Exception e) {
            theState.incrementBackendErrors();
            return;
        }        

        // Store the known match in the datastore for lookup later.
        CondensedMatch c = new CondensedMatch(theSpectatorURL, playerNamesForMatch);
        try {
            // Attempt to populate the condensed match information immediately,
            // if we can pull that out from the spectator server.
            c.condenseFullJSON(RemoteResourceLoader.loadJSON(theSpectatorURL));
            c.save();
        } catch (Exception e) {
            ;
        }
        for (Player p : playersForMatch) {
            p.addRecentMatchURL(theSpectatorURL);
            p.save();
        }
        theGame.addRecentMatchURL(theSpectatorURL);
        theGame.save();
        theState.addRecentMatchURL(theSpectatorURL);
        theState.getRunningMatches().add(theSpectatorURL);
        theState.clearBackendErrors();
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
    
    public static final String apolloPublicKey = "0MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAgjPUn3Zkr1u+BQb2fMOUcypSsJY4c/IRFDaA5Gjg022gZCY+a5yC61nSIwYnfTdWcnDEadUvbLWvD3IXmhxKZY69k6GpfgGZBp90bS918vuFiRQ16UcEzSloeVQs0jt7Nq+9EKvBBlULGxZcwXH30G+wIyoo/9qGJOwN+XmhFj9PC/WbPGvzB8ABKo08XIGyqDbv+xF0xVw0Pdfd2sYKUuSQawIFHxQBztbySTydl5r5qUwETxw5JuZkuK0c3cNer7M24/fokGuyukmnBI3k6V9lkguAOzVXjnknKaEAh4KassLwQK9Byc84hEyFFZk4USTneS2Kz3ZcjxRGOYjWKMHEVJVsbR2rHA7nN1PZbk14bNdemwwAbwYB2ONWe3Bhmg9JY2USdChqlR+dD0NfWPzEWV1hgt2o7X9OhB2B5sOrnsaJrkBDkbwa7yC4Y3E8AEV8KekQrNLOynoKbh7cZHs4bPKBKULnhAKzy22XoHYMw9G5vsXlMx+jLpyUhwrzAgMBAAE=";
    public static boolean verifyApolloCryptography(JSONObject theMatchInfo) {
        try {
            if (!SignableJSON.isSignedJSON(new JSONObject(theMatchInfo.toString()))) return false;
            if (!SignableJSON.verifySignedJSON(new JSONObject(theMatchInfo.toString()))) return false;
            if (!theMatchInfo.getString("matchHostPK").equals(apolloPublicKey)) return false;
            return true;
        } catch (Exception e) {
            return false;
        }        
    }
}