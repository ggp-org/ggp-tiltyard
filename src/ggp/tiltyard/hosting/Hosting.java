package ggp.tiltyard.hosting;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.*;

import org.ggp.galaxy.shared.game.Game;
import org.ggp.galaxy.shared.game.RemoteGameRepository;
import org.ggp.galaxy.shared.gdl.factory.GdlFactory;
import org.ggp.galaxy.shared.statemachine.MachineState;
import org.ggp.galaxy.shared.statemachine.Move;
import org.ggp.galaxy.shared.statemachine.Role;
import org.ggp.galaxy.shared.statemachine.StateMachine;

public class Hosting {
    public static void doGet(String reqURI, HttpServletResponse resp)
            throws IOException {
    	// TODO(schreib): Factor out the parts of this responsible for
    	// serving static web pages and include them in the top level servlet.
    	
        String[] uriParts = reqURI.split("/");
        if (reqURI.isEmpty() || reqURI.equals("index.html")) {
            writeStaticPage(resp, "MainPage.html");
            return;
        }
        if (reqURI.equals("artemis.css")) {
            writeStaticPage(resp, "artemis.css");
            return;
        }
        if (uriParts.length < 2) {
            resp.setStatus(404);
            return;
        }
        if (uriParts[0].equals("startMatch")) {
            String theGameURL = reqURI.substring("startMatch/".length());

            // For now, only permit games from one repository (for security).
            if (!theGameURL.startsWith("http://games.ggp.org/base/games/")) {
                resp.setStatus(404);
                return;
            }

            // TODO: Fill out all of these with real values.
            String matchId = "party." + (new Date()).getTime();
            Game theGame = RemoteGameRepository.loadSingleGame(theGameURL);            
            List<String> playerNames = new ArrayList<String>();
            List<String> playerURLs = new ArrayList<String>();
            for (int i = 0; i < Role.computeRoles(theGame.getRules()).size(); i++) {
            	playerURLs.add("");
            	playerNames.add(null);
            }
            MatchData m = new MatchData(matchId, playerNames, playerURLs, -1, 0, 0, theGame);

            resp.setHeader("Access-Control-Allow-Origin", "*");
            resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "*");
            resp.setHeader("Access-Control-Allow-Age", "86400");        
            resp.setContentType("text/plain");
            resp.getWriter().println(m.getMatchKey());            
            return;
        }
        if (!uriParts[0].equals("matches")) {
            resp.setStatus(404);
            return;
        }

        String matchName = uriParts[1];
        if (uriParts.length == 2) {
            if (!reqURI.endsWith("/")) { resp.setStatus(404); return; }
            writeStaticPage(resp, "MatchPage.html");
            return;
        }

        String subpageName = uriParts[2];
        if (subpageName.isEmpty() || subpageName.equals("index.html")) {
            if (!reqURI.endsWith("/")) { resp.setStatus(404); return; }
            writeStaticPage(resp, "MatchPage.html");
            return;
        }
        
        subpageName = subpageName.split("#")[0];
        if (subpageName.startsWith("player")) {
            int nIndex = Integer.parseInt(subpageName.substring("player".length()))-1;

            if (uriParts.length == 5) {
                String subSubpageName = uriParts[3];
                if (subSubpageName.equals("play")) {
                    resp.setContentType("text/plain");
                    resp.getWriter().println(uriParts[4].replace("%20", " "));
                    selectMove(matchName, nIndex, uriParts[4].replace("%20", " "));            
                    return;
                }
                resp.setStatus(404);
                return;
            }

            if (!reqURI.endsWith("/")) { resp.setStatus(404); return; }            
            writeStaticPage(resp, "PlayerPage.html");
            return;
        }

        resp.setStatus(404);
        return;
    }

    public static void selectMove(String matchName, int nRoleIndex, String move) throws IOException {
        MatchData theMatch = MatchData.loadMatchData(matchName);
        if (theMatch == null)
            return;

        StateMachine theMachine = theMatch.getMyStateMachine();
        MachineState theState = theMatch.getState(theMachine);
        try {
            if (!theMachine.isTerminal(theState)) {
                Move theMove = theMachine.getMoveFromTerm(GdlFactory.createTerm(move));

                if(theMachine.getLegalMoves(theState, theMachine.getRoles().get(nRoleIndex)).contains(theMove)) {
                    theMatch.setPendingMove(nRoleIndex, move);

                    // This is a loop so that when all players have NOOP moves, we'll still
                    // push through to the next state.
                    while (!theMachine.isTerminal(theState) && theMatch.allPendingMovesSubmitted()) {
                        List<Move> theMoves = new ArrayList<Move>();
                        String[] thePendingMoves = theMatch.getPendingMoves();
                        for (int i = 0; i < thePendingMoves.length; i++)
                            theMoves.add(theMachine.getMoveFromTerm(GdlFactory.createTerm(thePendingMoves[i])));

                        theState = theMachine.getNextState(theState, theMoves);
                        theMatch.setState(theMachine, theState, theMoves);                            
                    }

                    // TODO(schreib): Fix the race condition here.
                    theMatch.publish();
                    theMatch.save();
                } else {
                    throw new IOException("Bad move for role " + nRoleIndex + "! " + theMove + "; Valid moves are: " + theMachine.getLegalMoves(theState, theMachine.getRoles().get(nRoleIndex)));
                }
            }
        } catch (Exception e) {
            throw new IOException(e);
        }     
    }

    public static void writeStaticPage (HttpServletResponse resp, String rootFile) throws IOException {
        FileReader fr = new FileReader("hosting/" + rootFile);
        BufferedReader br = new BufferedReader(fr);
        StringBuffer response = new StringBuffer();

        String line;
        while( (line = br.readLine()) != null ) {
            response.append(line + "\n");
        }

        resp.setContentType("text/html");
        resp.getWriter().println(response.toString());
    }
}