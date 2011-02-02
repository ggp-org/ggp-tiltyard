package ggp.apollo;

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

import javax.servlet.http.*;

import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

@SuppressWarnings("serial")
public class GGP_ApolloServlet extends HttpServlet {
    private final String backendAddress = "http://0.0.0.0:9124/";
    
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (req.getRequestURI().equals("/cron/scheduling_round")) {
            runSchedulingRound();
            resp.setContentType("text/plain");
            resp.getWriter().println("Starting scheduling round.");            
            return;
        }
        
        resp.getWriter().println("<html><body>");
        resp.getWriter().println("Hello, world! This is Apollo.<ul>");

        // Output a sorted list of the recorded matches.
        ServerState theState = ServerState.loadState();
        List<String> theDataStrings = new ArrayList<String>();
        for(KnownMatch m : KnownMatch.loadKnownMatches()) {
            String isOngoing = theState.getRunningMatches().contains(m.getTimeStamp()) ? " <b>(Ongoing!)</b>" : "";
            theDataStrings.add("Match " + m.getTimeStamp() + " [" + m.getPlayers().length + "]: <a href='" + m.getSpectatorURL() + "viz.html'>Spectator View</a>" + isOngoing);
        }
        Collections.sort(theDataStrings);
        for(String s : theDataStrings) {
            resp.getWriter().println("<li>" + s);        
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

    public void runSchedulingRound() throws IOException {
        boolean[] playerBusy = new boolean[theActivePlayers.length];
        Arrays.fill(playerBusy, false);

        ServerState theState = ServerState.loadState();

        Set<String> doneMatches = new HashSet<String>();
        for (String matchKey : theState.getRunningMatches()) {
            KnownMatch m = KnownMatch.loadKnownMatch(matchKey);
            try {
                JSONObject theMatchInfo = RemoteResourceLoader.loadJSON(m.getSpectatorURL());
                if(theMatchInfo.getBoolean("isCompleted")) {
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
        
        int readyPlayers = 0;
        for (int i = 0; i < playerBusy.length; i++)
            if (!playerBusy[i])
                readyPlayers++;

        int nPlayersForGame = 0;
        String theGameURL = null;
        if (readyPlayers >= 3) {
            theGameURL = "http://ggp-repository.appspot.com/games/3pConnectFour/";
            nPlayersForGame = 3;
        } else if (readyPlayers >= 2){
            theGameURL = "http://ggp-repository.appspot.com/games/connectFour/";
            nPlayersForGame = 2;
        } else if (readyPlayers >= 1) {
            theGameURL = "http://ggp-repository.appspot.com/games/peg/";
            nPlayersForGame = 1;
        }

        if (theGameURL == null)
            return;

        int[] thePlayerIndexes = new int[nPlayersForGame];
        String[] thePlayersForMatch = new String[nPlayersForGame];
        for (int i = 0; i < playerBusy.length && nPlayersForGame > 0; i++) {
            if (!playerBusy[i]) {
                nPlayersForGame--;
                thePlayersForMatch[nPlayersForGame] = theActivePlayers[i];
                thePlayerIndexes[nPlayersForGame] = i;
            }
        }
                
        JSONObject theMatchRequest = new JSONObject();
        try {
            theMatchRequest.put("startClock", 45);
            theMatchRequest.put("playClock", 15);
            theMatchRequest.put("gameURL", theGameURL);
            theMatchRequest.put("matchId", "apollo." + System.currentTimeMillis());
            theMatchRequest.put("players", thePlayersForMatch);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        String theSpectatorURL = null;
        try {
            URL url = new URL(backendAddress + URLEncoder.encode(theMatchRequest.toString(), "UTF-8"));
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            theSpectatorURL = reader.readLine();
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        
        KnownMatch k = new KnownMatch(theSpectatorURL, thePlayerIndexes);
        theState.getRunningMatches().add(k.getTimeStamp());
        theState.save();
    }
}