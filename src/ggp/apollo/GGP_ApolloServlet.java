package ggp.apollo;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.servlet.http.*;

import org.datanucleus.store.query.AbstractQueryResult;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

@SuppressWarnings("serial")
public class GGP_ApolloServlet extends HttpServlet {    
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (req.getRequestURI().equals("/cron/scheduling_round")) {
            runSchedulingRound();
            resp.setContentType("text/plain");
            resp.getWriter().println("Starting scheduling round.");            
            return;
        }

        resp.setHeader("Access-Control-Allow-Origin", "apollo.ggp.org");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setHeader("Access-Control-Allow-Age", "86400");       
        
        if (req.getRequestURI().startsWith("/data/")) {
            respondToRPC(resp, req.getRequestURI().replaceFirst("/data/", ""));
            return;
        }
        
        String reqURI = req.getRequestURI();
        if (reqURI.endsWith("/")) {
            reqURI += "index.html";
        }
        
        if (reqURI.startsWith("/players/") && !reqURI.equals("/players/index.html")) {
            String playerName = reqURI.replaceFirst("/players/", "");
            if(Player.loadPlayer(playerName) == null) {
                resp.setStatus(404);
                return;
            }
            reqURI = "/players/playerPage.html";
        }
        if (reqURI.startsWith("/matches/") && !reqURI.equals("/matches/index.html")) {            
            String matchName = reqURI.replaceFirst("/matches/", "").replace("index.html", "");
            if(CondensedMatch.loadCondensedMatch("http://matches.ggp.org/matches/" + matchName) == null) {
                resp.setStatus(404);
                return;
            }
            reqURI = "/matches/matchPage.html";
        }

        boolean writeAsBinary = false;        
        if (reqURI.endsWith(".html")) {
            resp.setContentType("text/html");
        } else if (reqURI.endsWith(".xml")) {
            resp.setContentType("application/xml");
        } else if (reqURI.endsWith(".xsl")) {
            resp.setContentType("application/xml");
        } else if (reqURI.endsWith(".js")) {
            resp.setContentType("text/javascript");   
        } else if (reqURI.endsWith(".json")) {
            resp.setContentType("text/javascript");
        } else if (reqURI.endsWith(".png")) {
            resp.setContentType("image/png");
            writeAsBinary = true;
        } else if (reqURI.endsWith(".ico")) {
            resp.setContentType("image/png");
            writeAsBinary = true;
        } else {
            resp.setContentType("text/plain");
        }

        try {
            if (writeAsBinary) {
                writeStaticBinaryPage(resp, reqURI.substring(1));
            } else {
                // Temporary limits on caching, for during development.
                resp.setHeader("Cache-Control", "no-cache");
                resp.setHeader("Pragma", "no-cache");
                writeStaticTextPage(resp, reqURI.substring(1));
            }
        } catch(IOException e) {
            resp.setStatus(404);
        }
    }

    // Comment out games that are expensive for AppEngine-based players.
    private final String[] someProperGames = {
            //"3pConnectFour:3",
            //"4pttc:4",
            //"blocker:2",
            //"breakthrough:2",
            //"breakthroughSmall:2",
            //"chess:2",
            //"checkers:2",
            "connectFour:2",
            "connectFourSuicide:2",            
            //"eightPuzzle:1",
            //"knightThrough:2",
            //"knightsTour:1",
            //"pawnToQueen:2",
            //"pawnWhopping:2",
            //"peg:1",
            //"pegEuro:1",            
            //"qyshinsu:2",
            "nineBoardTicTacToe:2",            
            //"ttcc4_2player:2",
            "ticTacToe:2"
    };

    public void runSchedulingRound() throws IOException {
        ServerState theState = ServerState.loadState();
        theState.incrementSchedulingRound();
        runSchedulingRound(theState);
        theState.save();
    }

    public void runSchedulingRound(ServerState theState) throws IOException {
        List<Player> thePlayers = new ArrayList<Player>(Player.loadPlayers());
        for (int i = thePlayers.size()-1; i >= 0; i--) {
            if (!thePlayers.get(i).isEnabled()) {
                thePlayers.remove(i);
            }
        }

        // Find and clear all of the completed or wedged matches. For matches
        // which are still ongoing, mark the players in those matches as busy.
        Set<String> doneMatches = new HashSet<String>();
        Set<String> busyPlayerNames = new HashSet<String>();
        for (String matchKey : theState.getRunningMatches()) {
            CondensedMatch m = CondensedMatch.loadCondensedMatch(matchKey);
            try {
                JSONObject theMatchInfo = RemoteResourceLoader.loadJSON(m.getSpectatorURL());
                if(theMatchInfo.getBoolean("isCompleted")) {
                    doneMatches.add(matchKey);
                } else if (System.currentTimeMillis() > theMatchInfo.getLong("startTime") + 1000L*theMatchInfo.getInt("startClock") + 256L*1000L*theMatchInfo.getInt("playClock")) {
                    // Assume the match is wedged/completed after time sufficient for 256+ moves has passed.
                    doneMatches.add(matchKey);
                } else {
                    busyPlayerNames.addAll(m.getPlayers());
                }
                m.condenseFullJSON(theMatchInfo);
                m.save();
            } catch (Exception e) {
                e.printStackTrace();
                throw new IOException(e);
            }
        }
        theState.getRunningMatches().removeAll(doneMatches);

        // Figure out how many players are available. If no players are available,
        // don't bother attempting to schedule a match.
        int readyPlayers = thePlayers.size() - busyPlayerNames.size();
        if (readyPlayers < 2) return;
        
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
        String theGameURL = "http://games.ggp.org/games/" + theGameKey + "/";        

        // Assign available players to roles in the game.
        String[] playerURLsForMatch = new String[nPlayersForGame];
        List<String> playerNamesForMatch = new ArrayList<String>();
        List<Player> playersForMatch = new ArrayList<Player>();
        for (Player p : thePlayers) {
            if (busyPlayerNames.contains(p.getName())) continue;
            nPlayersForGame--;            
            playerURLsForMatch[nPlayersForGame] = p.getURL();
            playerNamesForMatch.add(p.getName());
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
        theState.addRecentMatchURL(theSpectatorURL);
        theState.getRunningMatches().add(theSpectatorURL);
        theState.clearBackendErrors();
    }
    
    public void computeStatistics() {
        int nMatches = 0;
        int nMatchesFinished = 0;
        int nMatchesAbandoned = 0;
        int nMatchesStatErrors = 0;
        
        Map<String,WeightedAverage> playerAverageScore = new HashMap<String,WeightedAverage>();
        Map<String,WeightedAverage> playerDecayedAverageScore = new HashMap<String,WeightedAverage>();
        Map<String,Map<String,WeightedAverage>> averageScoreVersus = new HashMap<String,Map<String,WeightedAverage>>();
        
        WeightedAverage playersPerMatch = new WeightedAverage();
        WeightedAverage movesPerMatch = new WeightedAverage();

        long nComputeBeganAt = System.currentTimeMillis();
        PersistenceManager pm = Persistence.getPersistenceManager();
        try {
            Iterator<?> sqr = ((AbstractQueryResult) pm.newQuery(CondensedMatch.class).execute()).iterator();
            while (sqr.hasNext()) {
                CondensedMatch c = (CondensedMatch)sqr.next();
                if (!c.isReady()) continue;                            
                JSONObject theJSON = c.getCondensedJSON();
                
                nMatches++;
                try {
                    if (theJSON.getBoolean("isCompleted")) {
                        nMatchesFinished++;                        
                        movesPerMatch.addValue(theJSON.getInt("moveCount"));
                        
                        // Score-related statistics.
                        for (int i = 0; i < c.getPlayers().size(); i++) {
                            String aPlayer = c.getPlayers().get(i);
                            int aPlayerScore = theJSON.getJSONArray("goalValues").getInt(i);
                            
                            if (!playerAverageScore.containsKey(aPlayer)) {
                                playerAverageScore.put(aPlayer, new WeightedAverage());
                            }
                            playerAverageScore.get(aPlayer).addValue(aPlayerScore);
                            
                            double ageInDays = (double)(System.currentTimeMillis() - theJSON.getLong("startTime")) / (double)(86400000L);
                            if (!playerDecayedAverageScore.containsKey(aPlayer)) {
                                playerDecayedAverageScore.put(aPlayer, new WeightedAverage());
                            }
                            playerDecayedAverageScore.get(aPlayer).addValue(aPlayerScore, Math.pow(0.98, ageInDays));
                            
                            for (String bPlayer : c.getPlayers()) {
                                if (bPlayer.equals(aPlayer))
                                    continue;
                                if (!averageScoreVersus.containsKey(aPlayer)) {
                                    averageScoreVersus.put(aPlayer, new HashMap<String,WeightedAverage>());
                                }
                                if (!averageScoreVersus.get(aPlayer).containsKey(bPlayer)) {
                                    averageScoreVersus.get(aPlayer).put(bPlayer, new WeightedAverage());
                                }
                                averageScoreVersus.get(aPlayer).get(bPlayer).addValue(aPlayerScore);
                            }
                        }
                    } else {
                        nMatchesAbandoned++;
                    }
                    
                    playersPerMatch.addValue(theJSON.getJSONArray("gameRoleNames").length());
                } catch(JSONException ex) {
                    nMatchesStatErrors++;
                    throw new RuntimeException(ex);
                }
            }
        } catch(JDOObjectNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            pm.close();
        }
        long nComputeTime = System.currentTimeMillis() - nComputeBeganAt;
        
        // Store the statistics as a JSON object in the datastore.
        try {
            JSONObject overall = new JSONObject();
            Map<String, JSONObject> perPlayer = new HashMap<String, JSONObject>();
            
            // Store the overall statistics
            overall.put("matches", nMatches);
            overall.put("matchesFinished", nMatchesFinished);
            overall.put("matchesAbandoned", nMatchesAbandoned);
            overall.put("matchesAverageMoves", movesPerMatch.getWeightedAverage());
            overall.put("matchesAveragePlayers", playersPerMatch.getWeightedAverage());
            overall.put("matchesStatErrors", nMatchesStatErrors);
            overall.put("computeTime", nComputeTime);
            overall.put("leaderboard", playerAverageScore);
            overall.put("decayedLeaderboard", playerDecayedAverageScore);
            overall.put("computedAt", System.currentTimeMillis());

            // Store the per-player statistics
            for (String playerName : playerAverageScore.keySet()) {
                if (!perPlayer.containsKey(playerName)) {
                    perPlayer.put(playerName, new JSONObject());
                }
                perPlayer.get(playerName).put("averageScore", playerAverageScore.get(playerName));
            }
            for (String playerName : playerDecayedAverageScore.keySet()) {
                if (!perPlayer.containsKey(playerName)) {
                    perPlayer.put(playerName, new JSONObject());
                }
                perPlayer.get(playerName).put("decayedAverageScore", playerDecayedAverageScore.get(playerName));
            }            
            for (String playerName : averageScoreVersus.keySet()) {
                if (!perPlayer.containsKey(playerName)) {
                    perPlayer.put(playerName, new JSONObject());
                }
                perPlayer.get(playerName).put("averageScoreVersus", averageScoreVersus.get(playerName));
            }
            
            StoredStatistics s = new StoredStatistics();
            s.setOverallStats(overall);
            for (String playerName : perPlayer.keySet()) {
                s.setPlayerStats(playerName, perPlayer.get(playerName));
            }
            s.save();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }    
    
    public void writeStaticTextPage(HttpServletResponse resp, String theURI) throws IOException {
        FileReader fr = new FileReader(theURI);
        BufferedReader br = new BufferedReader(fr);
        StringBuffer response = new StringBuffer();
        
        String line;
        while( (line = br.readLine()) != null ) {
            response.append(line + "\n");
        }

        resp.getWriter().println(response.toString());
    }
    
    public void writeStaticBinaryPage(HttpServletResponse resp, String theURI) throws IOException {
        InputStream in = new FileInputStream(theURI);
        byte[] buf = new byte[1024];
        while (in.read(buf) > 0) {
            resp.getOutputStream().write(buf);
        }
        in.close();        
    }
    
    public void respondToRPC(HttpServletResponse resp, String theRPC) throws IOException {
        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();
        String userId = (user != null) ? user.getUserId() : "";

        try {
            if (theRPC.equals("players/")) {
                JSONObject theResponse = new JSONObject();
                for (Player p : Player.loadPlayers()) {
                    theResponse.put(p.getName(), p.asJSON(p.isOwner(userId), false));
                }
                resp.getWriter().println(theResponse.toString());
            } else if (theRPC.startsWith("players/")) {
                String thePlayer = theRPC.replaceFirst("players/", "");
                Player p = Player.loadPlayer(thePlayer);
                if (p == null) {
                    resp.setStatus(404);
                    return;
                }
                resp.getWriter().println(p.asJSON(p.isOwner(userId), true));
            } else if (theRPC.equals("matches/")) {
                // TODO: Should we have an RPC interface that lets you get
                // the list of all matches ever? This might not scale.
                resp.setStatus(404);
            } else if (theRPC.equals("matches/recent/")) {
                ServerState serverState = ServerState.loadState();
                JSONArray theMatches = new JSONArray();
                for (String recentMatchURL : serverState.getRecentMatchURLs()) {
                    CondensedMatch c = CondensedMatch.loadCondensedMatch(recentMatchURL);
                    if (!c.isReady()) continue;
                    theMatches.put(c.getCondensedJSON());
                }
                resp.getWriter().println(theMatches.toString());
            } else if (theRPC.equals("matches/ongoing/")) {
                ServerState serverState = ServerState.loadState();
                JSONArray theMatches = new JSONArray();
                for (String recentMatchURL : serverState.getRunningMatches()) {
                    CondensedMatch c = CondensedMatch.loadCondensedMatch(recentMatchURL);
                    if (!c.isReady()) continue;
                    theMatches.put(c.getCondensedJSON());
                }
                resp.getWriter().println(theMatches.toString());
            } else if (theRPC.equals("matches/ongoing/set")) {
                ServerState serverState = ServerState.loadState();                
                JSONArray theMatches = new JSONArray(serverState.getRunningMatches());
                resp.getWriter().println(theMatches.toString());                
            } else if (theRPC.startsWith("matches/")) {
                String theMatchURL = theRPC.replaceFirst("matches/", "");
                CondensedMatch c = CondensedMatch.loadCondensedMatch(theMatchURL);
                if (c == null || !c.isReady()) {
                    resp.setStatus(404);
                    return;
                }
                resp.getWriter().println(c.getCondensedJSON());
            } else if (theRPC.equals("serverState")) {
                ServerState serverState = ServerState.loadState();
                JSONObject theResponse = new JSONObject();
                theResponse.put("schedulingRound", serverState.getSchedulingRound());
                theResponse.put("backendErrors", serverState.getBackendErrors());
                resp.getWriter().println(theResponse.toString());
            } else if (theRPC.equals("login")) {
                JSONObject theResponse = new JSONObject();
                if (user != null) {                    
                    theResponse.put("nickname", user.getNickname());
                    theResponse.put("authDomain", user.getAuthDomain());
                    theResponse.put("federatedIdentity", user.getFederatedIdentity());
                    theResponse.put("emailAddress", user.getEmail());
                    theResponse.put("userId", user.getUserId());
                    theResponse.put("logoutURL", userService.createLogoutURL("http://apollo.ggp.org/REPLACEME"));
                    theResponse.put("loggedIn", true);
                } else {
                    Map<String, String> openIdProviders = new HashMap<String, String>();
                    openIdProviders = new HashMap<String, String>();
                    openIdProviders.put("google", "google.com/accounts/o8/id");
                    openIdProviders.put("yahoo", "yahoo.com");
                    openIdProviders.put("myspace", "myspace.com");
                    openIdProviders.put("aol", "aol.com");
                    openIdProviders.put("myopenid", "myopenid.com");

                    JSONObject theProviders = new JSONObject();
                    for (String providerName : openIdProviders.keySet()) {
                        String providerUrl = openIdProviders.get(providerName);
                        theProviders.put(providerName, userService.createLoginURL("http://apollo.ggp.org/REPLACEME", null, providerUrl, new HashSet<String>()));
                    }
                    theResponse.put("providers", theProviders);
                    theResponse.put("preferredOrder", new String[] {"google", "yahoo", "aol", "myspace", "myopenid"} );
                    theResponse.put("loggedIn", false);
                }
                resp.getWriter().println(theResponse.toString());
            } else if (theRPC.startsWith("statistics/")) {                
                String theStatistic = theRPC.replaceFirst("statistics/", "");                
                JSONObject theResponse = null;
                if (theStatistic.equals("overall")) {
                    StoredStatistics s = StoredStatistics.loadStatistics();
                    theResponse = s.getOverallStats();
                } else if (theStatistic.startsWith("players/")) {
                    StoredStatistics s = StoredStatistics.loadStatistics();
                    theStatistic = theStatistic.replaceFirst("players/", "");
                    theResponse = s.getPlayerStats(theStatistic);
                } else if (theStatistic.startsWith("game/")) {
                    StoredStatistics s = StoredStatistics.loadStatistics();
                    theStatistic = theStatistic.replaceFirst("game/", "");
                    theResponse = s.getGameStats(theStatistic);
                } else if (theStatistic.equals("refresh")) {
                    computeStatistics();
                    StoredStatistics s = StoredStatistics.loadStatistics();
                    theResponse = s.getOverallStats();
                }
                if (theResponse != null) {
                    resp.getWriter().println(theResponse.toString());
                } else {
                    resp.setStatus(404);
                }
            } else {
                resp.setStatus(404);
            }
        } catch(JSONException e) {
            throw new IOException(e);
        }
    }
    
    public static String sanitize(String x) {
        // TODO: Force the string to be ASCII?
        return x.replaceAll("<", "&lt;")
                .replaceAll(">", "&rt;")
                .replaceAll("\"", "&quot;")
                .trim();
    }
    
    public static String sanitizeHarder(String x) {
        StringBuilder theString = new StringBuilder();
        
        for (int i = 0; i < x.length(); i++) {
            char c = x.charAt(i);
            if (Character.isLetterOrDigit(c) ||
                c == '-' || c == '_') {
                theString.append(c);
            }
        }
        
        return sanitize(theString.toString());
    }    
    
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Access-Control-Allow-Origin", "apollo.ggp.org");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setHeader("Access-Control-Allow-Age", "86400");

        String theURI = req.getRequestURI();
        
        BufferedReader br = new BufferedReader(new InputStreamReader(req.getInputStream()));
        int contentLength = Integer.parseInt(req.getHeader("Content-Length").trim());
        StringBuilder theInput = new StringBuilder();
        for (int i = 0; i < contentLength; i++) {
            theInput.append((char)br.read());
        }
        String in = theInput.toString().trim();
        
        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();
        String userId = (user != null) ? user.getUserId() : "";

        try {
            if (theURI.equals("/data/updatePlayer") && userId.length() > 0) {
                JSONObject playerInfo = new JSONObject(in);
                String theName = sanitizeHarder(playerInfo.getString("name"));
                if (!theName.equals(playerInfo.getString("name"))) {
                    resp.setStatus(404);
                    return;                    
                }
                
                Player p = Player.loadPlayer(theName);
                if (p == null) {
                    p = new Player(theName, sanitize(playerInfo.getString("theURL")), userId);
                } else if (!p.isOwner(userId)) {
                    resp.setStatus(404);
                    return;
                }

                String gdlVersion = playerInfo.getString("gdlVersion");                
                if (!gdlVersion.equals("GDLv1") && !gdlVersion.equals("GDLv2")) {
                    gdlVersion = "GDLv1";
                }
                
                p.setEnabled(playerInfo.getBoolean("isEnabled"));
                p.setGdlVersion(gdlVersion);
                p.setURL(sanitize(playerInfo.getString("theURL")));
                p.setVisibleEmail(sanitize(playerInfo.getString("visibleEmail")));
                p.save();

                resp.getWriter().println(p.asJSON(true, false));
            } else {
                resp.setStatus(404);
            }
        } catch(JSONException e) {
            throw new IOException(e);
        }        
    }
    
    public void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {  
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "*");
        resp.setHeader("Access-Control-Allow-Age", "86400");    
    }
}