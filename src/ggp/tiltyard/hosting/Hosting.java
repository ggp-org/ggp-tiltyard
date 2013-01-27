package ggp.tiltyard.hosting;

import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskRetryLimit;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

import ggp.tiltyard.players.Player;

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
import org.ggp.galaxy.shared.gdl.scrambler.NoOpGdlScrambler;
import org.ggp.galaxy.shared.persistence.Persistence;
import org.ggp.galaxy.shared.server.request.RequestBuilder;
import org.ggp.galaxy.shared.statemachine.MachineState;
import org.ggp.galaxy.shared.statemachine.Move;
import org.ggp.galaxy.shared.statemachine.Role;
import org.ggp.galaxy.shared.statemachine.StateMachine;
import org.ggp.galaxy.shared.statemachine.exceptions.GoalDefinitionException;
import org.ggp.galaxy.shared.statemachine.exceptions.MoveDefinitionException;
import org.ggp.galaxy.shared.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.galaxy.shared.symbol.factory.exceptions.SymbolFormatException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
               		selectMove(req.getParameter("matchKey"), Integer.parseInt(req.getParameter("playerIndex")), Integer.parseInt(req.getParameter("forStep")), req.getParameter("theMove").replace("%20", " ").replace("+", " "));
           		} else if (uriParts[1].equals("publish")) {
           			MatchData m = MatchData.loadMatchData(req.getParameter("matchKey"));
           			int stepCountToPublish = Integer.parseInt(req.getParameter("stepCount")); 
           			while (stepCountToPublish > m.getStepCount()) {
           				// Only publish the match when the step count actually matches the
           				// step count that we intend to publish. When we're supposed to publish
           				// a step count and the loaded match isn't yet at that step count, reload
           				// it until it reaches the desired step count -- it's possible that the
           				// newer version is still being persisted and we've got an old version.
           				try { Thread.sleep(500); } catch (InterruptedException ie) {}
           				m = MatchData.loadMatchData(req.getParameter("matchKey"));
           			}
           			// If the match is already past the point where we're supposed to publish,
           			// just drop it, since there's going to be another task queued up to publish
           			// it at that point.
           			if (m.getStepCount() > stepCountToPublish) {
           				Logger.getAnonymousLogger().severe("Was supposed to publish step " + stepCountToPublish + " for match, but match is already at step " + m.getStepCount());
           			}
               		m.publish();
           		} else if (uriParts[1].equals("request")) {
           			MatchData.loadMatchData(req.getParameter("matchKey")).issueRequestForAll(req.getParameter("requestContent"));
           		} else if (uriParts[1].equals("request_to")) {
           			MatchData.loadMatchData(req.getParameter("matchKey")).issueRequestTo(Integer.parseInt(req.getParameter("playerIndex")), req.getParameter("requestContent"), false);
           		} else if (uriParts[1].equals("request_start")) {
           			MatchData.loadMatchData(req.getParameter("matchKey")).issueStartRequests();           			
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
               	Logger.getAnonymousLogger().severe("Exception caught during task: " + e.toString());
            }            
    		return;
        } else if (!uriParts[0].equals("matches")) {
            resp.setStatus(404);
            return;
        }

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
            if (!reqURI.endsWith("/")) { resp.setStatus(404); return; }            
            writeStaticPage(resp, "PlayerPage.html");
            return;
        }

        resp.setStatus(404);
        return;
    }
    
    @SuppressWarnings("serial")
	static class MoveSelectException extends Exception {
		public MoveSelectException(String x) {
    		super(x);
    	}
    }

    public static void selectMove(String matchName, int nRoleIndex, int forStep, String move) throws MoveSelectException {
   		EncodedKeyPair theKeys = null;
   		try {
   			theKeys = StoredCryptoKeys.loadCryptoKeys("Artemis");
   		} catch (IOException ie) {
   			throw new RuntimeException(ie);
   		}
   		
   		// Attempt the transaction five times. If the transaction can't go through
   		// after five attempts, fail out and let the task queue retry: occasionally
   		// this will get stuck and failing out to the task queue fixes things.
   		int nAttempt = 0;
    	for (; nAttempt < 5; nAttempt++) {
	    	PersistenceManager pm = Persistence.getPersistenceManager();
	    	Transaction tx = pm.currentTransaction();

	    	try {
		    	// Start the transaction
	    	    tx.begin();
	
	    	    MatchData oldMatch = pm.getObjectById(MatchData.class, matchName);
	        	if (oldMatch == null) {
	        		throw new RuntimeException("Could not find match!");
	        	}
	    	    pm.makeTransactional(oldMatch);
	        	MatchData theMatch = pm.detachCopy(oldMatch);
	        	theMatch.inflateAfterLoading(theKeys);
	        	
	        	if (forStep != theMatch.getStepCount()) {
	        		Logger.getAnonymousLogger().severe("Got misaligned move for " + nRoleIndex + "; got move for step " + forStep + " but match is actually at step " + theMatch.getStepCount());
	        		return;
	        	}
	        	
	        	boolean shouldPublish = false;
		        StateMachine theMachine = theMatch.getMyStateMachine();
		        MachineState theState = theMatch.getState(theMachine);
	            if (!theMachine.isTerminal(theState)) {
	            	try {
	            		Move theMove = theMachine.getMoveFromTerm(GdlFactory.createTerm(move));
		                
						if(!theMachine.getLegalMoves(theState, theMachine.getRoles().get(nRoleIndex)).contains(theMove)) {
							Logger.getAnonymousLogger().severe("Bad move for role " + nRoleIndex + "! " + theMove + "; Valid moves are: " + theMachine.getLegalMoves(theState, theMachine.getRoles().get(nRoleIndex)));
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
	                        shouldPublish = true;
	
	                        if (theMatch.hasComputerPlayers()) {
		                        String theRequest = null;
		                        if (theMatch.isCompleted()) {
		                        	theRequest = RequestBuilder.getStopRequest(theMatch.getMatchId(), theMoves, new NoOpGdlScrambler());
		                        } else {
		                        	theRequest = RequestBuilder.getPlayRequest(theMatch.getMatchId(), theMoves, new NoOpGdlScrambler());
		                        }
		                        QueueFactory.getDefaultQueue().add(withUrl("/hosting/tasks/request").method(Method.GET).param("matchKey", theMatch.getMatchKey()).param("requestContent", theRequest).retryOptions(withTaskRetryLimit(TASK_RETRIES)));
	                        }
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
	                
                    if (shouldPublish) {
                    	QueueFactory.getQueue("publication").add(withUrl("/hosting/tasks/publish").method(Method.GET).param("matchKey", theMatch.getMatchKey()).param("stepCount", "" + theMatch.getStepCount()).retryOptions(withTaskRetryLimit(TASK_RETRIES)));
                    }	                
	            }
	
	    	    // Commit the transaction, flushing the object to the datastore
	    	    tx.commit();
	    	    pm.close();
	    	    return;
	    	} catch (javax.jdo.JDOException e) {
	    		;
	    	} finally {
	    	    if (tx.isActive()) {
	    	        // Error occurred so rollback the transaction and try again
	    	        tx.rollback();
	    	    }
	    	}
    	}
    	throw new MoveSelectException("Could not select a move in " + nAttempt + " attempts.");
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

	public static void doPost(String theURI, String in, HttpServletResponse resp) {
		try {		
			if (theURI.equals("callback")) {
				JSONObject theResponseJSON = new JSONObject(in);
				JSONObject theRequestJSON = new JSONObject(theResponseJSON.getString("originalRequest"));
				if (theRequestJSON.getString("requestContent").startsWith("( PLAY ")) {
					String theMove = "unspecified";
					if (theResponseJSON.has("response")) {
						theMove = theResponseJSON.getString("response");
					}
					QueueFactory.getDefaultQueue().add(withUrl("/hosting/tasks/select_move").method(Method.GET).param("matchKey", theRequestJSON.getString("matchKey")).param("playerIndex", "" + theRequestJSON.getInt("playerIndex")).param("forStep", "" + theRequestJSON.getInt("forStep")).param("theMove", theMove).retryOptions(withTaskRetryLimit(TASK_RETRIES)));
				} else if (theRequestJSON.getString("requestContent").startsWith("( START ")) {				
	                String theFirstPlayRequest = RequestBuilder.getPlayRequest(theRequestJSON.getString("matchId"), null, new NoOpGdlScrambler());                        
	                QueueFactory.getDefaultQueue().add(withUrl("/hosting/tasks/request_to").method(Method.GET).param("matchKey", theRequestJSON.getString("matchKey")).param("playerIndex", theRequestJSON.getString("playerIndex")).param("requestContent", theFirstPlayRequest).retryOptions(withTaskRetryLimit(TASK_RETRIES)));
				}
				
				resp.getWriter().println("okay");
			} else if (theURI.equals("start_match")) {
				JSONObject theRequest = new JSONObject(in);
				
				String gameURL = theRequest.getString("gameURL");
				if (!gameURL.startsWith("http://games.ggp.org/base/games/")) {
					Logger.getAnonymousLogger().severe("Game URL did not start with valid prefix.");
					return;
				}
				
	            String matchId = "tiltyard." + (new Date()).getTime();
	            Game theGame = RemoteGameRepository.loadSingleGame(gameURL);

	            // This code parses the "playerCodes" that can be sent to the
	            // match hosting system to indicate which players to use. There
	            // are four types of valid match codes:
	            //
	            // empty string         = a human player
	            // "random"             = a random player
	            // "tiltyard://foo"     = player named "foo" on Tiltyard
	            // any URL              = remote player at that URL
	            //
	            // Start requests that don't include a playerCodes field are
	            // assumed to consist entirely of human players.
	            int nRoles = Role.computeRoles(theGame.getRules()).size();
	            JSONArray thePlayerCodes = null;
	            if (theRequest.has("playerCodes")) {
	            	thePlayerCodes = theRequest.getJSONArray("playerCodes");
		            if (nRoles != thePlayerCodes.length()) {
						Logger.getAnonymousLogger().severe("Game has " + nRoles + " roles but start request has " + thePlayerCodes.length() + " player codes.");
						return;
		            }
	            } else {
	            	thePlayerCodes = new JSONArray();
	            	for (int i = 0; i < nRoles; i++) {
	            		thePlayerCodes.put("");
	            	}
	            }
	            List<String> playerURLs = new ArrayList<String>();
	            List<String> playerNames = new ArrayList<String>();
	            for (int i = 0; i < nRoles; i++) {
	            	String code = thePlayerCodes.getString(i);
	            	if (code.isEmpty()) {
	            		playerNames.add("");
	            		playerURLs.add(null);
	            	} else if (code.toLowerCase().equals("random")) {
	            		playerNames.add("Random");
	            		playerURLs.add(null);
	            	} else if (code.startsWith("tiltyard://")) {
	            		code = code.substring("tiltyard://".length());
	            		Player p = Player.loadPlayer(code);
	            		if (p == null) {
	            			Logger.getAnonymousLogger().severe("Player " + code + " not found.");
	            			return;
	            		} else {
		            		playerNames.add(code);
		            		playerURLs.add(p.getURL());
	            		}
	            	} else {
	            		playerNames.add("");
	            		playerURLs.add(code);
	            	}
	            }
	            
	            int analysisClock = theRequest.getInt("analysisClock");
	            int startClock = theRequest.getInt("startClock");
	            int playClock = theRequest.getInt("playClock");
	            
	            MatchData m = new MatchData(matchId, playerNames, playerURLs, analysisClock, startClock, playClock, theGame);
	            if (m.hasComputerPlayers()) {
	            	QueueFactory.getDefaultQueue().add(withUrl("/hosting/tasks/request_start").method(Method.GET).param("matchKey", m.getMatchKey()).retryOptions(withTaskRetryLimit(TASK_RETRIES)));
	            }
	            
	            resp.getWriter().println(m.getMatchKey());
			} else if(theURI.equals("select_move")) {
				JSONObject theRequest = new JSONObject(in);

				int playerIndex = theRequest.getInt("roleIndex");
				int forStep = theRequest.getInt("forStep");
				String theMove = theRequest.getString("theMove");
				String matchKey = theRequest.getString("matchKey");

				QueueFactory.getDefaultQueue().add(withUrl("/hosting/tasks/select_move").method(Method.GET).param("matchKey", matchKey).param("playerIndex", "" + playerIndex).param("forStep", "" + forStep).param("theMove", theMove).retryOptions(withTaskRetryLimit(TASK_RETRIES)));
				
				resp.getWriter().println(theMove);
			}
			
            resp.setHeader("Access-Control-Allow-Origin", "*");
            resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            resp.setHeader("Access-Control-Allow-Headers", "*");
            resp.setHeader("Access-Control-Allow-Age", "86400");        
            resp.setContentType("text/plain");
            resp.setStatus(200);			
		} catch (JSONException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}