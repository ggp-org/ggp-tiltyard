package ggp.tiltyard.hosting;

import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskRetryLimit;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.jdo.Transaction;
import javax.servlet.http.*;

import org.ggp.galaxy.shared.crypto.BaseCryptography.EncodedKeyPair;
import org.ggp.galaxy.shared.game.Game;
import org.ggp.galaxy.shared.game.RemoteGameRepository;
import org.ggp.galaxy.shared.gdl.factory.GdlFactory;
import org.ggp.galaxy.shared.persistence.Persistence;
import org.ggp.galaxy.shared.statemachine.MachineState;
import org.ggp.galaxy.shared.statemachine.Move;
import org.ggp.galaxy.shared.statemachine.Role;
import org.ggp.galaxy.shared.statemachine.StateMachine;
import org.ggp.galaxy.shared.statemachine.exceptions.GoalDefinitionException;
import org.ggp.galaxy.shared.statemachine.exceptions.MoveDefinitionException;
import org.ggp.galaxy.shared.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.galaxy.shared.symbol.factory.exceptions.SymbolFormatException;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions.Method;

public class Hosting {
	private static final int TASK_RETRIES = 10;
	
    public static void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {    	
    	// TODO(schreib): Factor out the parts of this responsible for
    	// serving static web pages and include them in the top level servlet.
    	String reqURI = req.getRequestURI().replaceFirst("/hosting/", "");
    	
        String[] uriParts = reqURI.split("[/?]");
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
        if (uriParts[0].equals("tasks")) {
           	int nRetryAttempt = Integer.parseInt(req.getHeader("X-AppEngine-TaskRetryCount"));
           	try {
           		if (uriParts[1].equals("select_move")) {
               		selectMove(req.getParameter("matchKey"), Integer.parseInt(req.getParameter("playerIndex")), req.getParameter("theMove").replace("%20", " "));
           		} else if (uriParts[1].equals("publish")) {
               		MatchData.loadMatchData(req.getParameter("matchKey")).publish();
           		} else {
           			Logger.getAnonymousLogger().severe("Could not identify task associated with task queue URL: " + uriParts[1]);
           			resp.setStatus(404);
           			return;
           		}
           		resp.setStatus(200);
           	} catch (Exception e) {
           		resp.setStatus(503);
           		// For the first few exceptions, silently issue errors to task queue to trigger retries.
          		// After a few retries, start surfacing the exceptions, since they're clearly not transient.
               	// This reduces the amount of noise in the error logs caused by transient PuSH hub errors.
               	if (nRetryAttempt > TASK_RETRIES - 3) {
               		throw new RuntimeException(e);
               	}
               	Logger.getAnonymousLogger().warning("Exception caught during task: " + e.toString());
               	// Wait a little time, in case that helps.
               	try {
               		Thread.sleep(1000);
               	} catch (InterruptedException ie) {
               		;
               	}
            }            
    		return;
        } else if (uriParts[0].equals("startMatch")) {
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
        } else if (!uriParts[0].equals("matches")) {
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
                    QueueFactory.getDefaultQueue().add(withUrl("/hosting/tasks/select_move").method(Method.GET).param("matchKey", matchName).param("playerIndex", "" + nIndex).param("theMove", uriParts[4]).retryOptions(withTaskRetryLimit(TASK_RETRIES)));            
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

    public static void selectMove(String matchName, int nRoleIndex, String move) {
   		EncodedKeyPair theKeys = null;
   		try {
   			theKeys = StoredCryptoKeys.loadCryptoKeys("Artemis");
   		} catch (IOException ie) {
   			throw new RuntimeException(ie);
   		}
   		
    	while (true) {
	    	PersistenceManager pm = Persistence.getPersistenceManager();
	    	Transaction tx = pm.currentTransaction();

	    	// Start the transaction
    	    tx.begin();

        	MatchData theMatch = pm.getObjectById(MatchData.class, matchName);
        	if (theMatch == null) {
        		throw new RuntimeException("Could not find match!");
        	}
        	theMatch.inflateAfterLoading(theKeys);
	
	        StateMachine theMachine = theMatch.getMyStateMachine();
	        MachineState theState = theMatch.getState(theMachine);
            if (!theMachine.isTerminal(theState)) {
            	try {
            		Move theMove = theMachine.getMoveFromTerm(GdlFactory.createTerm(move));
	                
					if(!theMachine.getLegalMoves(theState, theMachine.getRoles().get(nRoleIndex)).contains(theMove)) {
						Logger.getAnonymousLogger().warning("Bad move for role " + nRoleIndex + "! " + theMove + "; Valid moves are: " + theMachine.getLegalMoves(theState, theMachine.getRoles().get(nRoleIndex)));
						return;
					}
                
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
				} catch (MoveDefinitionException e) {
					Logger.getAnonymousLogger().severe("GGP Prover move issue: " + e.toString());
				} catch (TransitionDefinitionException e) {
					Logger.getAnonymousLogger().severe("GGP Prover transition issue: " + e.toString());
				} catch (SymbolFormatException e) {
					Logger.getAnonymousLogger().severe("GGP Prover symbol issue: " + e.toString());
				} catch (GoalDefinitionException e) {
					Logger.getAnonymousLogger().severe("GGP Prover goal issue: " + e.toString());
				}
                
                theMatch.deflateForSaving();
                pm.makePersistent(theMatch);
                QueueFactory.getDefaultQueue().add(withUrl("/hosting/tasks/publish").method(Method.GET).param("matchKey", theMatch.getMatchKey()).retryOptions(withTaskRetryLimit(TASK_RETRIES)));
            }
	        
    	    // Commit the transaction, flushing the object to the datastore
    	    tx.commit();
    	    if (tx.isActive()) {
    	    	Logger.getAnonymousLogger().info("Transaction failed, rolling back...");
    	        // Error occurred so rollback the transaction and try again
    	        tx.rollback();
	    	    pm.close();	    	    
    	    } else {
    	    	Logger.getAnonymousLogger().info("Transaction succeeded!");
    	    	// Otherwise we're done!
	    	    pm.close();
    	    	return;
    	    }
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