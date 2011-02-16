package ggp.apollo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import javax.servlet.http.*;

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

        resp.getWriter().println("<html><head><title>GGP Apollo Server</title></head><body>");
        resp.getWriter().println("Welcome to the Apollo gaming server.<br>");

        // Output a sorted list of the recorded matches.
        ServerState theState = ServerState.loadState();
        resp.getWriter().println("Currently on scheduling round " + theState.getSchedulingRound() + ".<br>");
        resp.getWriter().println("Consecutive backend errors so far: " + theState.getBackendErrors() + ".<br> <ul>");
        
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG, Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("PST"));
        List<String> theDataStrings = new ArrayList<String>();
        for(KnownMatch m : KnownMatch.loadKnownMatches()) {
            String isOngoing = theState.getRunningMatches().contains(m.getTimeStamp()) ? " <b>(Ongoing!)</b>" : "";
            theDataStrings.add(m.getTimeStamp() + ": Match started on " + df.format(new Date(Long.parseLong(m.getTimeStamp()))) + " with " + m.getPlayers().length + " players: <a href='" + m.getSpectatorURL() + "viz.html'>Spectator View</a>" + isOngoing);
        }
        Collections.sort(theDataStrings);
        Collections.reverse(theDataStrings);
        for(String s : theDataStrings) {
            resp.getWriter().println("<li>" + s.substring(15));        
        }

        resp.getWriter().println("</ul></body></html>");
    }

    private final String[] theActivePlayers = {
            "0.player.ggp.org:80",
            "1.player.ggp.org:80",
            "2.player.ggp.org:80",
            "3.player.ggp.org:80",
            "4.player.ggp.org:80"
    };
    
    private final String[] someProperGames = {
            "3pConnectFour:3",
            "4pttc:4",
            "blocker:2",
            "breakthrough:2",
            "breakthroughSmall:2",            
            "chess:2",
            "checkers:2",
            "connectFour:2",
            "connectFourSuicide:2",            
            "eightPuzzle:1",
            "knightThrough:2",
            "knightsTour:1",
            "pawnToQueen:2",
            "pawnWhopping:2",
            "peg:1",
            "pegEuro:1",            
            "qyshinsu:2",
            "nineBoardTicTacToe:2",
            "ticTacToe:2",
            "ttcc4_2player:2"
    };

    public void runSchedulingRound() throws IOException {
        ServerState theState = ServerState.loadState();
        theState.incrementSchedulingRound();
        runSchedulingRound(theState);
        theState.save();
    }
    
    public void runSchedulingRound(ServerState theState) throws IOException {
        boolean[] playerBusy = new boolean[theActivePlayers.length];
        Arrays.fill(playerBusy, false);

        // Find and clear all of the completed or wedged matches. For matches
        // which are still ongoing, mark the players in those matches as busy.
        Set<String> doneMatches = new HashSet<String>();
        for (String matchKey : theState.getRunningMatches()) {
            KnownMatch m = KnownMatch.loadKnownMatch(matchKey);
            try {
                JSONObject theMatchInfo = RemoteResourceLoader.loadJSON(m.getSpectatorURL());
                if(theMatchInfo.getBoolean("isCompleted")) {
                    doneMatches.add(matchKey);
                } else if (System.currentTimeMillis() > theMatchInfo.getLong("startTime") + 1000L*theMatchInfo.getInt("startClock") + 256L*1000L*theMatchInfo.getInt("playClock")) {
                    // Assume the match is wedged/completed after time sufficient for 256+ moves has passed.
                    doneMatches.add(matchKey);
                } else {
                    for (int i = 0; i < m.getPlayers().length; i++) {
                        playerBusy[m.getPlayers()[i]] = true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new IOException(e);
            }
        }
        theState.getRunningMatches().removeAll(doneMatches);

        // Figure out how many players are available. If no players are available,
        // don't bother attempting to schedule a match.
        int readyPlayers = 0;
        for (int i = 0; i < playerBusy.length; i++)
            if (!playerBusy[i])
                readyPlayers++;
        if (readyPlayers < 1) return;
        
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
        int[] thePlayerIndexes = new int[nPlayersForGame];
        String[] thePlayersForMatch = new String[nPlayersForGame];
        for (int i = 0; i < playerBusy.length && nPlayersForGame > 0; i++) {
            if (!playerBusy[i]) {
                nPlayersForGame--;
                thePlayersForMatch[nPlayersForGame] = theActivePlayers[i];
                thePlayerIndexes[nPlayersForGame] = i;
            }
        }

        // Construct a JSON request to the Apollo backend with the information
        // needed to run a match of the selected game w/ the selected players.
        JSONObject theMatchRequest = new JSONObject();
        try {
            theMatchRequest.put("startClock", 45);
            theMatchRequest.put("playClock", 15);
            theMatchRequest.put("gameURL", theGameURL);
            theMatchRequest.put("matchId", "apollo." + theGameKey + "." + System.currentTimeMillis());
            theMatchRequest.put("players", thePlayersForMatch);
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
        KnownMatch k = new KnownMatch(theSpectatorURL, thePlayerIndexes);
        theState.getRunningMatches().add(k.getTimeStamp());
        theState.clearBackendErrors();
    }
}