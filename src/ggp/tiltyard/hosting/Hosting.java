package ggp.tiltyard.hosting;

import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskRetryLimit;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

import ggp.tiltyard.backends.BackendPublicKey;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.jdo.Transaction;
import javax.servlet.http.*;

import org.ggp.galaxy.shared.crypto.SignableJSON;
import org.ggp.galaxy.shared.crypto.BaseCryptography.EncodedKeyPair;
import org.ggp.galaxy.shared.game.Game;
import org.ggp.galaxy.shared.game.RemoteGameRepository;
import org.ggp.galaxy.shared.gdl.factory.GdlFactory;
import org.ggp.galaxy.shared.gdl.factory.exceptions.GdlFormatException;
import org.ggp.galaxy.shared.persistence.Persistence;
import org.ggp.galaxy.shared.server.request.RequestBuilder;
import org.ggp.galaxy.shared.statemachine.MachineState;
import org.ggp.galaxy.shared.statemachine.Move;
import org.ggp.galaxy.shared.statemachine.Role;
import org.ggp.galaxy.shared.statemachine.StateMachine;
import org.ggp.galaxy.shared.statemachine.exceptions.MoveDefinitionException;
import org.ggp.galaxy.shared.symbol.factory.exceptions.SymbolFormatException;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.appengine.api.taskqueue.TransientFailureException;

public class Hosting {
    public static void doTask(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    	String requestedTask = req.getRequestURI().replaceFirst("/hosting/tasks/", "");
       	int nRetryAttempt = Integer.parseInt(req.getHeader("X-AppEngine-TaskRetryCount"));
       	try {
       		if (requestedTask.equals("publish")) {
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
       		} else if (requestedTask.equals("select_move")) {
           		selectMove(req.getParameter("matchKey"), Integer.parseInt(req.getParameter("playerIndex")), Integer.parseInt(req.getParameter("forStep")), req.getParameter("withError"), req.getParameter("theMove").replace("%20", " ").replace("+", " "), req.getParameter("source"));
       		} else if (requestedTask.equals("request")) {
       			MatchData.loadMatchData(req.getParameter("matchKey")).issueRequestForAll(req.getParameter("requestContent"));
       		} else if (requestedTask.equals("request_to")) {
       			MatchData.loadMatchData(req.getParameter("matchKey")).issueRequestTo(Integer.parseInt(req.getParameter("playerIndex")), req.getParameter("requestContent"), false);
       		} else if (requestedTask.equals("request_start")) {
       			MatchData.loadMatchData(req.getParameter("matchKey")).issueStartRequests();           			
       		} else {
       			Logger.getAnonymousLogger().severe("Could not identify task associated with task queue URL: " + requestedTask);
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
           	Logger.getAnonymousLogger().severe("Exception caught during task: " + e.toString() + " ... " + e.getCause());
        }            
		return;
    }
    
    @SuppressWarnings("serial")
	static class MoveSelectException extends Exception {
		public MoveSelectException(String x) {
    		super(x);
    	}
    }

    public static void selectMove(String matchName, int nRoleIndex, int forStep, String withError, String move, String source) throws MoveSelectException {
   		EncodedKeyPair theKeys = StoredCryptoKeys.loadCryptoKeys("Tiltyard");
   		
   		// Attempt the transaction a few times. If the transaction can't go through
   		// after twenty attempts, fail out and let the task queue retry: occasionally
   		// this will get stuck and failing out to the task queue fixes things.
   		int nAttempt = 0;
    	for (; nAttempt < 20; nAttempt++) {
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

	        	if (source.equals("human") && theMatch.isPlayerHuman(nRoleIndex)) {
	        		// Okay -- move by a human, for a human role
	        	} else if (source.equals("robot") && !theMatch.isPlayerHuman(nRoleIndex)) {
	        		// Okay -- move by a robot, for a robot role
	        	} else {
	        		Logger.getAnonymousLogger().severe("Got move from source " + source + " for player " + nRoleIndex + " which " + (theMatch.isPlayerHuman(nRoleIndex) ? "is" : "is not") + " human");
	        		return;
	        	}	        	
	        	
	        	if (forStep != theMatch.getStepCount()) {
	        		Logger.getAnonymousLogger().severe("Got misaligned move for " + nRoleIndex + "; got move for step " + forStep + " but match is actually at step " + theMatch.getStepCount());
	        		return;
	        	}
	        	
	        	boolean shouldPublish = false;
		        StateMachine theMachine = theMatch.getMyStateMachine();
		        MachineState theState = theMatch.getState(theMachine);
	            if (theMachine.isTerminal(theState)) {
	            	Logger.getAnonymousLogger().severe("Got move for match that has already terminated.");
	            	return;
	            }
	            
	            try {
	            	// First, parse the move, check its validity, and include
	            	// it in the match description.
	            	{
			            Move theMove = null;
			            if (withError.isEmpty()) {		            
			            	try {
			            		theMove = theMachine.getMoveFromTerm(GdlFactory.createTerm(move));
							} catch (SymbolFormatException e) {
								// Can't parse the move? Not valid.
								theMove = null;
							}
							if(theMove != null && !theMachine.getLegalMoves(theState, theMachine.getRoles().get(nRoleIndex)).contains(theMove)) {
								// Move isn't in the set of legal moves? Not valid.
								theMove = null;
							}
			            	if (theMove == null) {
			            		// Move isn't valid? Set an error.
			            		withError = "IL " + move;
			            	}
			            }
			            if (theMove == null) {
			            	// When the move isn't valid, choose a random move.
			            	List<Move> theMoves = theMachine.getLegalMoves(theState, theMachine.getRoles().get(nRoleIndex));
		                	Collections.shuffle(theMoves);
		                	theMove = theMoves.get(0);
			            }
		            	
		            	theMatch.setPendingMove(nRoleIndex, theMove.toString());
		            	theMatch.setPendingError(nRoleIndex, withError);
	            	}

	            	// Once the move has been set as pending, check to see if the match can
	            	// transition to the next step. This is done as a loop so that even when
	            	// all of the players have NOOP moves or are playing randomly, we'll still
	            	// push through to the next state.
                    while (!theMachine.isTerminal(theState) && theMatch.allPendingMovesSubmitted()) {
                        List<Move> theMoves = theMatch.advanceState(theMachine);
                        shouldPublish = true;

                        if (theMatch.hasComputerPlayers()) {
	                        String theRequest = null;
	                        if (theMatch.isCompleted()) {
	                        	theRequest = RequestBuilder.getStopRequest(theMatch.getMatchId(), theMoves, theMatch.getScrambler());
	                        } else {
	                        	theRequest = RequestBuilder.getPlayRequest(theMatch.getMatchId(), theMoves, theMatch.getScrambler());
	                        }
	                        addTaskToQueue(withUrl("/hosting/tasks/request").method(Method.GET).param("matchKey", theMatch.getMatchKey()).param("requestContent", theRequest));
                        }
                    }
				} catch (MoveDefinitionException e) {
					throw new RuntimeException(e);
				}
                
                theMatch.deflateForSaving();
                pm.makePersistent(theMatch);
                
                if (shouldPublish) {
                	addTaskToQueue("publication", withUrl("/hosting/tasks/publish").method(Method.GET).param("matchKey", theMatch.getMatchKey()).param("stepCount", "" + theMatch.getStepCount()));
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
    
    public static String startMatch(String gameURL, List<String> playerURLs, List<String> playerNames, int previewClock, int startClock, int playClock) {    	
		if (!gameURL.startsWith("http://games.ggp.org/base/games/")) {
			Logger.getAnonymousLogger().severe("Game URL did not start with valid prefix.");
			return null;
		}
		
		String gameKey = gameURL.replace("http://games.ggp.org/base/games/","").replace("/", "");		
        String matchId = "tiltyard." + gameKey + "." + (new Date()).getTime();
        Game theGame = RemoteGameRepository.loadSingleGame(gameURL);
        if (theGame == null) {
        	Logger.getAnonymousLogger().severe("Game URL " + gameURL + " could not be loaded.");
        	return null;
        }

        int nRoles = Role.computeRoles(theGame.getRules()).size();
        if (nRoles != playerURLs.size() || nRoles != playerNames.size()) {
			Logger.getAnonymousLogger().severe("Game has " + nRoles + " roles but start request has " + playerURLs.size() + " player URLs and " + playerNames.size() + " player names.");
			return null;
        }        
        
        MatchData m = new MatchData(matchId, playerNames, playerURLs, previewClock, startClock, playClock, theGame);
        if (m.hasComputerPlayers()) {
        	addTaskToQueue(withUrl("/hosting/tasks/request_start").method(Method.GET).param("matchKey", m.getMatchKey()));
        }
        return m.getMatchKey();
    }
    
	public static void doPost(String theURI, String in, HttpServletResponse resp) {
		try {
			if (theURI.equals("callback")) {
				JSONObject theResponseJSON = new JSONObject(in);
                if (!SignableJSON.isSignedJSON(theResponseJSON)) {
                    throw new RuntimeException("Got callback response that wasn't signed.");
                }
                if (!theResponseJSON.getString("matchHostPK").equals(BackendPublicKey.theKey)) {
                	throw new RuntimeException("Got callback response that was signed but not by request farm.");
                }
                if (!SignableJSON.verifySignedJSON(theResponseJSON)) {
                	throw new RuntimeException("Got callback response whose signature didn't validate.");
                }
				JSONObject theRequestJSON = new JSONObject(theResponseJSON.getString("originalRequest"));
				if (theRequestJSON.getString("requestContent").startsWith("( PLAY ")) {
					String theMove = "";
					String theError = "";
					if (theResponseJSON.has("response")) {
						theMove = theResponseJSON.getString("response");
					}
					if (theResponseJSON.has("responseType")) {
						String type = theResponseJSON.getString("responseType");
						if (type.equals("TO") || type.equals("CE")) {
							theError = type;
						} 
					}					
					if (!theMove.isEmpty()) {
						try {
							MatchData theMatch = MatchData.loadMatchData(theRequestJSON.getString("matchKey"));
							theMove = theMatch.getScrambler().unscramble(theMove).toString();
						} catch (GdlFormatException ge) {
							;
						} catch (SymbolFormatException e) {
							;
						} catch (RuntimeException re) {
							;
						}
					}
					addTaskToQueue(withUrl("/hosting/tasks/select_move").method(Method.GET).param("matchKey", theRequestJSON.getString("matchKey")).param("playerIndex", "" + theRequestJSON.getInt("playerIndex")).param("forStep", "" + theRequestJSON.getInt("forStep")).param("theMove", theMove).param("withError", theError).param("source", "robot"));
				} else if (theRequestJSON.getString("requestContent").startsWith("( START ")) {				
					MatchData theMatch = MatchData.loadMatchData(theRequestJSON.getString("matchKey"));
	                String theFirstPlayRequest = RequestBuilder.getPlayRequest(theRequestJSON.getString("matchId"), null, theMatch.getScrambler());                        
	                addTaskToQueue(withUrl("/hosting/tasks/request_to").method(Method.GET).param("matchKey", theRequestJSON.getString("matchKey")).param("playerIndex", theRequestJSON.getString("playerIndex")).param("requestContent", theFirstPlayRequest));
				}
				
				resp.getWriter().println("okay");
			} else if(theURI.equals("select_move")) {
				JSONObject theRequest = new JSONObject(in);

				int playerIndex = theRequest.getInt("roleIndex");
				int forStep = theRequest.getInt("forStep");
				String theMove = theRequest.getString("theMove");
				String matchKey = theRequest.getString("matchKey");

				addTaskToQueue(withUrl("/hosting/tasks/select_move").method(Method.GET).param("matchKey", matchKey).param("playerIndex", "" + playerIndex).param("forStep", "" + forStep).param("theMove", theMove).param("withError", "").param("source", "human"));
				
				resp.getWriter().println(theMove);
			}

            resp.setHeader("Access-Control-Allow-Origin", "tiltyard.ggp.org");
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
	
	private static final int TASK_RETRIES = 50;
    public static void addTaskToQueue(TaskOptions task) {
    	addTaskToQueue(null, task);
    }    
    public static void addTaskToQueue(String queueName, TaskOptions task) {
    	task = task.retryOptions(withTaskRetryLimit(TASK_RETRIES).minBackoffSeconds(1).maxBackoffSeconds(60).maxDoublings(4));
    	int nAttempt = 0;    	
    	while (true) {    		
	    	try {
	    		if (queueName == null) {
	    			QueueFactory.getDefaultQueue().add(task);
	    		} else {
	    			QueueFactory.getQueue(queueName).add(task);
	    		}
	    		return;
	    	} catch (TransientFailureException tfe) {
	    		if (nAttempt > 10) {
	    			throw new RuntimeException(tfe);
	    		}	    		
	    	}
	    	try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				;
			}
	    	nAttempt++;
    	}
    }
}