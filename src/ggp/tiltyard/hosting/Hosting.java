package ggp.tiltyard.hosting;

import static com.google.appengine.api.taskqueue.RetryOptions.Builder.withTaskRetryLimit;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;
import external.JSON.JSONArray;
import external.JSON.JSONException;
import external.JSON.JSONObject;
import ggp.tiltyard.backends.BackendPublicKey;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.jdo.Transaction;
import javax.servlet.http.*;

import org.ggp.base.server.request.RequestBuilder;
import org.ggp.base.util.crypto.BaseCryptography.EncodedKeyPair;
import org.ggp.base.util.crypto.SignableJSON;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.game.RemoteGameRepository;
import org.ggp.base.util.gdl.factory.GdlFactory;
import org.ggp.base.util.gdl.factory.exceptions.GdlFormatException;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.symbol.factory.exceptions.SymbolFormatException;
import org.ggp.galaxy.shared.persistence.Persistence;

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
       		} else if (requestedTask.equals("select_moves")) {				
           		selectMoves(req.getParameter("matchKey"), Integer.parseInt(req.getParameter("forStep")), new JSONObject(req.getParameter("moveBatchJSON").replace("%20", " ").replace("+", " ")));
       		} else if (requestedTask.equals("request")) {
       			if (AbortedMatchKeys.loadAbortedMatchKeys().isRecentlyAborted(req.getParameter("matchKey"))) return;
       			MatchData.loadMatchData(req.getParameter("matchKey")).issueRequestForAll(req.getParameter("requestContent"));
       		} else if (requestedTask.equals("request_start")) {
       			if (AbortedMatchKeys.loadAbortedMatchKeys().isRecentlyAborted(req.getParameter("matchKey"))) return;
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
           	Logger.getAnonymousLogger().severe("During task, caught " + convertExceptionToString(e));
        }            
		return;
    }
    
    public static String convertExceptionToString(Exception e) {
       	StringWriter traceWriter = new StringWriter();
       	e.printStackTrace(new PrintWriter(traceWriter));
    	return "exception [" + e.toString() + "] with cause [" + e.getCause() + "] with trace: " + traceWriter.toString(); 
    }

    public static final int SELECT_MOVES_ATTEMPTS = 20;
    public static void selectMoves(String matchKey, int forStep, JSONObject moveBatchJSON) {
   		EncodedKeyPair theKeys = StoredCryptoKeys.loadCryptoKeys("Tiltyard");
   		
   		// Attempt the transaction a few times. If the transaction can't go through
   		// after twenty attempts, fail out and let the task queue retry: occasionally
   		// this will get stuck and failing out to the task queue fixes things.
   		int nAttempt = 0;
   		Exception lastException = null;
    	for (; nAttempt < SELECT_MOVES_ATTEMPTS; nAttempt++) {
	    	PersistenceManager pm = Persistence.getPersistenceManager();
	    	Transaction tx = pm.currentTransaction();

	    	try {
		    	// Start the transaction
	    	    tx.begin();

	    	    MatchData oldMatch = pm.getObjectById(MatchData.class, matchKey);
	        	if (oldMatch == null) {
	        		throw new RuntimeException("Could not find match!");
	        	}
	    	    pm.makeTransactional(oldMatch);
	        	MatchData theMatch = pm.detachCopy(oldMatch);
	        	theMatch.inflateAfterLoading(theKeys);

	        	if (forStep != theMatch.getStepCount()) {
	        		Logger.getAnonymousLogger().severe("Got misaligned move batch; got move for step " + forStep + " but match is actually at step " + theMatch.getStepCount());
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
	            	JSONArray moveArray = moveBatchJSON.getJSONArray("responses");
	            	// First, go through all of the incoming move requests, parse them,
	            	// check their validity, and include them in the match description.
	            	for (int i = 0; i < moveArray.length(); i++) {
	            		JSONObject moveResponse = moveArray.getJSONObject(i);
	            		JSONObject moveRequest = moveResponse.getJSONObject("originalRequest");
	            		
	            		int nRoleIndex = moveRequest.getInt("playerIndex");	            		
	            		String source = moveRequest.getString("source");
	            		String matchId = moveRequest.getString("matchId");	            		
	            		
	            		if (!matchId.equals(theMatch.getMatchId())) {
	            			Logger.getAnonymousLogger().severe("Got inconsistency between match id in batch for element " + i + ": " + matchId + " vs " + theMatch.getMatchId());
	            			continue;	            			
	            		}
	            		if (!matchKey.equals(moveRequest.getString("matchKey"))) {
	            			Logger.getAnonymousLogger().severe("Got inconsistency between match keys in batch for element " + i + ": " + matchKey + " vs " + moveRequest.getString("matchKey"));
	            			continue;
	            		}
	            		if (forStep != moveRequest.getInt("forStep")) {
	            			Logger.getAnonymousLogger().severe("Got inconsistency between for-steps in batch for element " + i + ": " + forStep + " vs " + moveRequest.getInt("forStep"));
	            			continue;	            			
	            		}
	            		
    		        	if (source.equals("human") && theMatch.isPlayerHuman(nRoleIndex)) {
    		        		// Okay -- move by a human, for a human role
    		        	} else if (source.equals("robot") && !theMatch.isPlayerHuman(nRoleIndex)) {
    		        		// Okay -- move by a robot, for a robot role
    		        	} else {
    		        		Logger.getAnonymousLogger().severe("Got move from source " + source + " for player " + nRoleIndex + " which " + (theMatch.isPlayerHuman(nRoleIndex) ? "is" : "is not") + " human");
    		        		continue;
    		        	}
    		        	
    		        	Move theMove = null;
    		        	String theError = "";
    		        	
    		        	// Extract any error-related information from the request response
    					if (moveResponse.has("responseType")) {
    						String type = moveResponse.getString("responseType");
    						if (type.equals("TO") || type.equals("CE")) {
    							theError = type;
    						}
    					}
	            		
			            if (theError.isEmpty()) {
			            	String move = moveResponse.getString("response");  // .replace("%20", " ").replace("+", " ")
			            	try {
			            		if (source.equals("robot")) {
			            			move = theMatch.getScrambler().unscramble(move).toString();
			            		}
			            	} catch (GdlFormatException e) {
			            		;
			            	} catch (SymbolFormatException e) {
			            		;
			            	}
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
			            		theError = "IL " + move;
			            	}
			            }
			            if (theMove == null) {
			            	// When the move isn't valid, choose a random move.
			            	List<Move> theMoves = theMachine.getLegalMoves(theState, theMachine.getRoles().get(nRoleIndex));
		                	Collections.shuffle(theMoves);
		                	theMove = theMoves.get(0);
			            }
		            	
		            	theMatch.setPendingMove(nRoleIndex, theMove.toString());
		            	theMatch.setPendingError(nRoleIndex, theError);
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
				} catch (JSONException e) {
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
	    	} catch (javax.jdo.JDOObjectNotFoundException e) {
	    		lastException = e;
    			if (AbortedMatchKeys.loadAbortedMatchKeys().isRecentlyAborted(matchKey)) {
    				return;
    			} else {
    				Logger.getAnonymousLogger().severe("Could not load match " + matchKey + " but was not recently aborted");
    				break;
    			}	    		
	    	} catch (javax.jdo.JDOException e) {
	    		lastException = e;
	    	} finally {
	    	    if (tx.isActive()) {
	    	        // Error occurred so rollback the transaction and try again
	    	        tx.rollback();
	    	    }
	    	}
    	}
    	Logger.getAnonymousLogger().severe("Could not select a move in " + nAttempt + " attempts. Last exception was " + convertExceptionToString(lastException));
    	throw new RuntimeException(lastException);
    }
    
    public static void writeStaticPage (HttpServletResponse resp, String rootFile) throws IOException {
        FileReader fr = new FileReader("hosting/" + rootFile);
        BufferedReader br = new BufferedReader(fr);
        StringBuffer response = new StringBuffer();

        String line;
        while( (line = br.readLine()) != null ) {
            response.append(line + "\n");
        }
        br.close();

        resp.setContentType("text/html");
        resp.getWriter().println(response.toString());        
    }
    
    public static String startMatch(String gameURL, List<String> playerURLs, List<String> playerNames, List<String> playerRegions, int previewClock, int startClock, int playClock) {    	
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
        
        MatchData m = new MatchData(matchId, playerNames, playerURLs, playerRegions, previewClock, startClock, playClock, theGame);
        if (m.hasComputerPlayers()) {
        	addTaskToQueue(withUrl("/hosting/tasks/request_start").method(Method.GET).param("matchKey", m.getMatchKey()));
        }
        return m.getMatchKey();
    }
    
	public static void doPost(String theURI, String in, HttpServletResponse resp) {
		try {
			if (theURI.equals("callback")) {
				JSONObject theBatchResponseJSON = new JSONObject(in);
                if (!SignableJSON.isSignedJSON(theBatchResponseJSON)) {
                    throw new RuntimeException("Got callback response that wasn't signed.");
                }
                if (!theBatchResponseJSON.getString("matchHostPK").equals(BackendPublicKey.theKey)) {
                	throw new RuntimeException("Got callback response that was signed but not by request farm.");
                }
                if (!SignableJSON.verifySignedJSON(theBatchResponseJSON)) {
                	throw new RuntimeException("Got callback response whose signature didn't validate: " + theBatchResponseJSON);
                }
                if (!theBatchResponseJSON.has("responses")) {
                	throw new RuntimeException("Got callback response that did not contain batch responses.");
                }

                JSONArray responseArrayJSON = theBatchResponseJSON.getJSONArray("responses");
                if (responseArrayJSON.length() < 1) {
                	throw new RuntimeException("Got callback response that contains zero batch responses.");
                }
				JSONObject aRequestJSON = new JSONObject(responseArrayJSON.getJSONObject(0).getString("originalRequest"));
				if (!aRequestJSON.has("matchId")) {
					throw new RuntimeException("Could not get match ID from callback: " + aRequestJSON.toString());
				}
				if (!aRequestJSON.has("matchKey")) {
					throw new RuntimeException("Could not get match key from callback: " + aRequestJSON.toString());
				}
				String matchId = aRequestJSON.getString("matchId");
				String matchKey = aRequestJSON.getString("matchKey");
				int forStep = aRequestJSON.getInt("forStep");
				if (!AbortedMatchKeys.loadAbortedMatchKeys().isRecentlyAborted(matchKey)) {
					if (aRequestJSON.getString("requestContent").startsWith("( PLAY ")) {
						addTaskToQueue(withUrl("/hosting/tasks/select_moves").method(Method.GET).param("matchKey", matchKey).param("forStep", "" + forStep).param("moveBatchJSON", theBatchResponseJSON.toString()));
					} else if (aRequestJSON.getString("requestContent").startsWith("( START ")) {
						MatchData theMatch = MatchData.loadMatchData(matchKey);
						if (theMatch == null) {
							Logger.getAnonymousLogger().severe("Could not find match referenced by callback: " + aRequestJSON.toString());
						} else {
							String theFirstPlayRequest = RequestBuilder.getPlayRequest(matchId, null, theMatch.getScrambler());
							addTaskToQueue(withUrl("/hosting/tasks/request").method(Method.GET).param("matchKey", matchKey).param("requestContent", theFirstPlayRequest));
						}
					} else if (aRequestJSON.getString("requestContent").startsWith("( STOP ") ||
							   aRequestJSON.getString("requestContent").startsWith("( ABORT ")) {
						;
					} else {
						throw new RuntimeException("Got callback for unexpected request type: " + aRequestJSON.getString("requestContent"));
					}
				}
				
				resp.getWriter().println("okay");
			} else if(theURI.equals("select_move")) {
				JSONObject theUserRequest = new JSONObject(in);

				int roleIndex = theUserRequest.getInt("roleIndex");                                                            
				int forStep = theUserRequest.getInt("forStep");
				String theMove = theUserRequest.getString("theMove");
				String matchKey = theUserRequest.getString("matchKey");
				
				// Forge a batch response from the request farm, based on the incoming
				// request from a user. This can be passed to "select_moves" and decoded
				// in the same way as ordinary batch responses from the request farm.
				JSONObject theOriginalRequest = new JSONObject();
				theOriginalRequest.put("matchKey", matchKey);
				theOriginalRequest.put("roleIndex", roleIndex);
				theOriginalRequest.put("forStep", forStep);
				theOriginalRequest.put("source", "human");
				JSONObject theResponse = new JSONObject();
				theResponse.put("response", theMove);
				theResponse.put("responseType", "OK");
				theResponse.put("originalRequest", theOriginalRequest);
				JSONArray theResponses = new JSONArray();
				theResponses.put(theResponse);
				JSONObject theBatchResponse = new JSONObject();
				theBatchResponse.put("responses", theResponses);
				
				if (AbortedMatchKeys.loadAbortedMatchKeys().isRecentlyAborted(matchKey)) {
					return;
				}

				addTaskToQueue(withUrl("/hosting/tasks/select_moves").method(Method.GET).param("matchKey", matchKey).param("forStep", "" + forStep).param("moveBatchJSON", theBatchResponse.toString()));
				
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