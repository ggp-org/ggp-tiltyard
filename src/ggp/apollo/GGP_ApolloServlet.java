package ggp.apollo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.*;

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
        
        resp.getWriter().println("<html><body>");
        resp.getWriter().println("Hello, world! This is Apollo.<ul>");
        
        // Output a sorted list of the mock scheduling round times
        List<String> theDataStrings = new ArrayList<String>();
        for(DataPoint d : DataPoint.loadData()) {
            theDataStrings.add(d.getData());
        }
        Collections.sort(theDataStrings);
        for(String s : theDataStrings) {
            resp.getWriter().println("<li>" + s);        
        }
        
        resp.getWriter().println("</ul></body></html>");
    }
    
    private final String[] theActivePlayers = {
            "ggp-webplayer.appspot.com:80",
            "127.0.0.1:3333",
            "127.0.0.1:3334",
            "127.0.0.1:3335",
            "127.0.0.1:3336"
    };
    
    public void runSchedulingRound() throws IOException {
        //DataPoint.clearDataPoints();
        //new DataPoint("Scheduling round: " + new Date().toString());
        
        boolean[] playerBusy = new boolean[theActivePlayers.length];
        Arrays.fill(playerBusy, false);
        
        // Load in the ongoing matches
        // For each match, look it up from the spectator server
        // If it's finished, remove it from the list.
        // Otherwise, mark the players in it as busy
        
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
        
        String[] thePlayersForMatch = new String[nPlayersForGame];
        for (int i = 0; i < playerBusy.length && nPlayersForGame > 0; i++) {
            if (!playerBusy[i]) {
                nPlayersForGame--;
                thePlayersForMatch[nPlayersForGame] = theActivePlayers[i];                
            }
        }
        
        // Call out to the backend to start the match
        // Persist the matches in the list of running matches
    }
}
