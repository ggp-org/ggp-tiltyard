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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.*;

import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

@SuppressWarnings("serial")
public class GGP_ApolloServlet extends HttpServlet {    
    private static final Map<String, String> openIdProviders;
    static {
        openIdProviders = new HashMap<String, String>();
        openIdProviders.put("google", "google.com/accounts/o8/id");
        openIdProviders.put("yahoo", "yahoo.com");
        openIdProviders.put("myspace", "myspace.com");
        openIdProviders.put("aol", "aol.com");
        openIdProviders.put("myopenid", "myopenid.com");
    }
    
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
        
        StringBuilder specialContent = new StringBuilder();
        if (reqURI.equals("/index.html")) {            
            // Sample user login service
            UserService userService = UserServiceFactory.getUserService();
            User user = userService.getCurrentUser();        
            if (user != null) {
                if (user.getNickname().isEmpty()) {
                    specialContent.append("Hello!");
                } else {
                    specialContent.append("Hello, <b>" + user.getNickname() + "</b>!");
                }
                specialContent.append(" You are logged in, but you can <a href=\""
                        + userService.createLogoutURL(req.getRequestURI())
                        + "\">sign out</a> if you'd like.");
            } else {
                specialContent.append("Sign in using OpenID via ");
                for (String providerName : openIdProviders.keySet()) {
                    String providerUrl = openIdProviders.get(providerName);
                    String loginUrl = userService.createLoginURL(req
                            .getRequestURI(), null, providerUrl, new HashSet<String>());
                    specialContent.append(" <a href=\"" + loginUrl + "\"><img src=\"static/images/" + providerName + ".png\"></img></a> ");
                }
            }
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
                writeStaticTextPage(resp, reqURI.substring(1), specialContent.toString());
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
    
    public void writeStaticTextPage(HttpServletResponse resp, String theURI, String specialContent) throws IOException {
        FileReader fr = new FileReader(theURI);
        BufferedReader br = new BufferedReader(fr);
        StringBuffer response = new StringBuffer();
        
        String line;
        while( (line = br.readLine()) != null ) {
            response.append(line + "\n");
        }

        String theResponse = response.toString();
        if (specialContent.length() > 0) {
            theResponse = theResponse.replace("[SPECIAL_PAGE_CONTENT]", specialContent);
        }

        resp.getWriter().println(theResponse);
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
            } else if (theRPC.equals("userDebug")) {
                StringBuilder theResponse = new StringBuilder();
                if (user != null) {
                    if (user.getNickname().isEmpty()) {
                        theResponse.append("Hello!");
                    } else {
                        theResponse.append("Hello, <b>" + user.getNickname() + "</b>!");
                    }
                    theResponse.append(" You are logged in.<br>");
                    theResponse.append("Your auth domain is <i>" + user.getAuthDomain() + "</i>. <br>");
                    theResponse.append("Your federated identity is <i>" + user.getFederatedIdentity() + "</i>. <br>");
                    if (user.getEmail().isEmpty()) {
                        theResponse.append("You don't have an email address associated with this account.<br>");
                    } else {
                        theResponse.append("Your email address is <i>" + user.getEmail() + "</i>. <br>");
                    }
                    theResponse.append("Your user ID is <i>" + user.getUserId() + "</i>.");                
                } else {
                    theResponse.append("You are not logged in.");
                }
                resp.getWriter().println(theResponse.toString());
            } else {
                resp.setStatus(404);
            }
        } catch(JSONException e) {
            throw new IOException(e);
        }
    }
}