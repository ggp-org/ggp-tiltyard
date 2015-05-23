package ggp.tiltyard.hosting;

import external.JSON.JSONArray;
import external.JSON.JSONException;
import external.JSON.JSONObject;
import ggp.tiltyard.backends.BackendRegistration;
import ggp.tiltyard.backends.Backends;
import ggp.tiltyard.players.Player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import com.google.appengine.api.datastore.Text;

import org.ggp.base.server.request.RequestBuilder;
import org.ggp.base.util.crypto.BaseCryptography.EncodedKeyPair;
import org.ggp.base.util.crypto.BaseHashing;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.factory.GdlFactory;
import org.ggp.base.util.gdl.scrambler.GdlScrambler;
import org.ggp.base.util.match.Match;
import org.ggp.base.util.match.MatchPublisher;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
import org.ggp.base.util.symbol.factory.exceptions.SymbolFormatException;
import org.ggp.galaxy.shared.persistence.Persistence;

@PersistenceCapable
public class MatchData {	
    // NOTE: matchKey is the unique identifier under which this match has been
    // published to the http://matches.ggp.org/ spectator server. The complete
    // match can thus be found at http://matches.ggp.org/matches/X/ where X is
    // the matchKey.
    @PrimaryKey @Persistent private String matchKey;

	@Persistent private String[] playerURLs;
	@Persistent private String[] playerRegions;
	@Persistent private boolean[] playsRandomly;
    @Persistent private String[] pendingMoves;
    @Persistent private String[] pendingErrors;
    @Persistent private Text theGameJSON;
    @Persistent private Text theMatchJSON;
    @Persistent private String theAuthToken;
    
    private Match theMatch;
    
    public MatchData(String matchId, List<String> playerNames, List<String> playerURLs, List<String> playerRegions, int previewClock, int startClock, int playClock, Game theGame) {        
        try {
            JSONObject theSerializedGame = new JSONObject(theGame.serializeToJSON());            
            theSerializedGame.remove("theStylesheet");
            theSerializedGame.remove("theDescription");
            theGameJSON = new Text(theSerializedGame.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // TODO(schreib): Add support for the previewClock clock here.
        theMatch = new Match(matchId, previewClock, startClock, playClock, theGame);
        theMatch.setCryptographicKeys(StoredCryptoKeys.loadCryptoKeys("Tiltyard"));        
        this.playerURLs = playerURLs.toArray(new String[]{});
        this.playerRegions = playerRegions.toArray(new String[]{});
        theMatch.setPlayerNamesFromHost(playerNames);
        // Players named Random will play randomly; all others will not.
        playsRandomly = new boolean[playerNames.size()];
        for (int i = 0; i < playerNames.size(); i++) {
        	playsRandomly[i] = playerNames.get(i) != null && playerNames.get(i).toLowerCase().equals("random");
        }
        List<Boolean> isPlayerHuman = new ArrayList<Boolean>();
        for (int i = 0; i < playerURLs.size(); i++) {
        	isPlayerHuman.add(isPlayerHuman(i));
        }
        theMatch.setWhichPlayersAreHuman(isPlayerHuman);
        theMatch.enableScrambling();

        StateMachine theMachine = getMyStateMachine();
        MachineState theState = theMachine.getInitialState();
        try {
        	setState(theMachine, theState, null);
        } catch (MoveDefinitionException e) {
        	throw new RuntimeException(e);
        } catch (GoalDefinitionException e) {
        	throw new RuntimeException(e);
        }
        while (!theMachine.isTerminal(theState) && allPendingMovesSubmitted()) {
            advanceState(theMachine);
        }        

        matchKey = publish();
        save();
    }
    
    public String getMatchKey() {
        return matchKey;
    }
    
    public String getMatchId() {
    	return theMatch.getMatchId();
    }

    public boolean isCompleted() {
    	return theMatch.isCompleted();
    }
    
    public int getStepCount() {
    	return theMatch.getMoveHistory().size();
    }
    
    public boolean isPlayerHuman(int nPlayer) {
    	return playerURLs[nPlayer] == null && !playsRandomly[nPlayer];
    }
    
    public List<String> getPlayerNames() {
    	return theMatch.getPlayerNamesFromHost();
    }
    
    private long getElapsedTime() {
    	return System.currentTimeMillis() - theMatch.getStartTime().getTime();
    }    
    
    public long getTimeSinceLastChange() {
    	List<Date> theTimeHistory = theMatch.getStateTimeHistory();
    	if (theTimeHistory != null && theTimeHistory.size() > 0) {
    		Date lastChange = theTimeHistory.get(theTimeHistory.size()-1);
    		return System.currentTimeMillis() - lastChange.getTime();
    	} else {
    		return getElapsedTime();
    	}
    }
    
    public boolean isWedged() {
    	// Assume the match is wedged after time sufficient for the start phase and ten moves
    	// has passed without any updates to the match state, and the match isn't completed.
    	// Only matches with computer players can be considered wedged, since computer players are
    	// the only players that are negatively impacted by long-running matches.
    	return hasComputerPlayers() && !isCompleted() && getTimeSinceLastChange() > 1000L*(theMatch.getStartClock() + 10*theMatch.getPlayClock());
    }
    
    public GdlScrambler getScrambler() {
    	return theMatch.getGdlScrambler();
    }
    
    public JSONObject getMatchInfo() {
    	try {
    		return new JSONObject(theMatch.toJSON());
    	} catch (JSONException je) {
    		throw new RuntimeException(je);
    	}
    }
    
    public long getExpectedTimeToCompletion() {
  	    // Naively assume that each match takes 30 moves. Later on, this can be
  	    // refined to look at the average step count in the game being played.
        long predictedLength = 1000L*theMatch.getStartClock() + 30L*1000L*theMatch.getPlayClock();
        return Math.max(0L, predictedLength - getElapsedTime());
    }
    
    public boolean hasComputerPlayers() {
    	for (int i = 0; i < playerURLs.length; i++)
    		if (playerURLs[i] != null)
    			return true;
    	return false;
    }
    
    public List<Move> advanceState(StateMachine theMachine) {
    	try {
	        List<Move> theMoves = new ArrayList<Move>();
	        for (int i = 0; i < pendingMoves.length; i++)
	            theMoves.add(theMachine.getMoveFromTerm(GdlFactory.createTerm(pendingMoves[i])));
	
	        MachineState state = getState(theMachine);
	        state = theMachine.getNextState(state, theMoves);
	        setState(theMachine, state, theMoves);
	        return theMoves;
    	} catch (MoveDefinitionException e) {
    		throw new RuntimeException(e);
    	} catch (SymbolFormatException e) {
    		throw new RuntimeException(e);
		} catch (TransitionDefinitionException e) {
			throw new RuntimeException(e);
		} catch (GoalDefinitionException e) {
			throw new RuntimeException(e);
		}
    }
    
    private void setState(StateMachine theMachine, MachineState state, List<Move> moves) throws MoveDefinitionException, GoalDefinitionException {
        theMatch.appendState(state.getContents());
        if (moves != null) {
            theMatch.appendMoves2(moves);
        }
        if (theMachine.isTerminal(state)) {
        	theMatch.markCompleted(theMachine.getGoals(state));
        }
        if (pendingErrors != null) {
	        List<String> theErrors = new ArrayList<String>();
	        for (int i = 0; i < pendingErrors.length; i++) {
	        	theErrors.add(pendingErrors[i] == null ? "" : pendingErrors[i]);
	        }
	        theMatch.appendErrors(theErrors);
        } else {
        	// TODO(schreib): Support errors in START response.
        	theMatch.appendNoErrors();
        }

        // Clear the current pending moves and errors
        pendingMoves = new String[theMachine.getRoles().size()];
        pendingErrors = new String[theMachine.getRoles().size()];

        // If the match isn't completed, we should fill in all of the moves
        // that are automatically forced (because the player has no other move).
        // Moves are never forced for computer-controlled players.
        if (!theMatch.isCompleted()) {
            for (int i = 0; i < pendingMoves.length; i++) {
            	if (playerURLs[i] != null)
            		continue;

            	List<Move> theMoves = theMachine.getLegalMoves(state, theMachine.getRoles().get(i));
                if (theMoves.size() == 1) {
                    pendingMoves[i] = theMachine.getLegalMoves(state, theMachine.getRoles().get(i)).get(0).toString();
                } else if (playsRandomly[i]) {
                	Collections.shuffle(theMoves);
                	pendingMoves[i] = theMoves.get(0).toString();
                }
            }
        }
    }
    
    /**
     * Once this function returns, the match will be published and then deleted,
     * so it's important to ensure that the abort requests have been sent before
     * returning from the function.
     */
    public void abort() {
    	while(true) {
	    	try {
	    		issueAbortRequests();
	    		break;
	    	} catch (IOException ie) {
	    		;
	    	}
    	}
    	AbortedMatchKeys.loadAbortedMatchKeys().addRecentlyAborted(matchKey);
    	theMatch.markAborted();
    }
    
    private void issueRequests(String requestContent, boolean isStart) throws IOException {
    	List<Role> theRoles = Role.computeRoles(theMatch.getGame().getRules());
    	String requestRegion = Player.REGION_ANY;
    	
    	try {
    		JSONArray theRequests = new JSONArray();
    		for (int nRole = 0; nRole< playerURLs.length; nRole++) {
    			if (playerURLs[nRole] == null) continue;
    			JSONObject theRequestJSON = new JSONObject();

    	    	theRequestJSON.put("requestContent", (isStart ? RequestBuilder.getStartRequest(theMatch.getMatchId(), theRoles.get(nRole), theMatch.getGame().getRules(), theMatch.getStartClock(), theMatch.getPlayClock(), theMatch.getGdlScrambler()) : requestContent));
    	    	theRequestJSON.put("timeoutClock", 5000 + (isStart ? theMatch.getStartClock()*1000 : theMatch.getPlayClock()*1000));    	    	
    	    	theRequestJSON.put("matchId", theMatch.getMatchId());	    	
    	    	theRequestJSON.put("matchKey", matchKey);
    	    	
                String playerAddress = playerURLs[nRole];
                if (playerAddress.startsWith("http://")) {
                    playerAddress = playerAddress.replace("http://", "");
                }
                if (playerAddress.endsWith("/")) {
                    playerAddress = playerAddress.substring(0, playerAddress.length()-1);
                }
                String[] splitAddress = playerAddress.split(":");
        		theRequestJSON.put("targetHost", splitAddress[0]);
        		try {
        			theRequestJSON.put("targetPort", Integer.parseInt(splitAddress[1]));
        		} catch (ArrayIndexOutOfBoundsException e) {
        			theRequestJSON.put("targetPort", 9147);
        		} catch (NumberFormatException e) {
        			theRequestJSON.put("targetPort", 9147);
        		}
        		theRequestJSON.put("forPlayerName", "PLAYER"); //theMatch.getPlayerNamesFromHost().get(nRole));
        		
        		theRequestJSON.put("playerIndex", nRole);
        		theRequestJSON.put("source", "robot");
        		theRequestJSON.put("forStep", getStepCount());
        		theRequestJSON.put("fastReturn", true);
        		
        		// Add some extra headers to the outgoing request that can be
        		// used for debugging player networking issues.
        		JSONObject extraHeaders = new JSONObject();
        		extraHeaders.put("GGP-Match-ID", theMatch.getMatchId());
        		extraHeaders.put("GGP-Match-Step", getStepCount());
        		extraHeaders.put("GGP-Match-Host", "Tiltyard");
        		StringBuilder sb = new StringBuilder();
        		for (String playerName : theMatch.getPlayerNamesFromHost()) {
        			 sb.append(BaseHashing.computeSHA1Hash(playerName) + ", ");
        		}
        		sb.setLength(sb.length()-2);
        		extraHeaders.put("GGP-Match-Players", sb.toString());
        		theRequestJSON.put("extraHeaders", extraHeaders);
        		
        		theRequests.put(theRequestJSON);
        		requestRegion = playerRegions[nRole];
    		}
    		if (theRequests.length() == 0) {
    			return;
    		}
    		JSONObject theBatchRequest = new JSONObject();
    		theBatchRequest.put("requests", theRequests);
    		theBatchRequest.put("callbackURL", "http://tiltyard.ggp.org/hosting/callback");
    		
    		// TODO: Come up with a better approach for choosing request region.
    		issueRequestToFarm(theBatchRequest, requestRegion);
    	} catch (JSONException je) {
    		throw new RuntimeException(je);
    	}
    }
    
    public void issueStartRequests() throws IOException {
    	issueRequests(null, true);
    }
    
    private void issueAbortRequests() throws IOException {
    	issueRequests(RequestBuilder.getAbortRequest(theMatch.getMatchId()), false);
    }    
    
    public void issueRequestForAll(String requestContent) throws IOException {
    	issueRequests(requestContent, false);
    }

    private static void issueRequestToFarm(JSONObject requestJSON, String forRegion) throws IOException {
        // Find a backend server to run the request. As part of this process,
        // ping all of the registered backend servers to verify that they're
        // still available, and deregister those that aren't. Lastly choose
        // randomly from the remaining ones.
        Backends theBackends = Backends.loadBackends();
        List<String> validBackends = new ArrayList<String>();
        for (String theBackendAddress : theBackends.getFarmBackendAddresses(forRegion)) {
            try {
                URL url = new URL("http://" + theBackendAddress + ":9125/" + URLEncoder.encode("ping", "UTF-8"));
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                if(!BackendRegistration.verifyBackendPing(reader.readLine())) {
                    continue;
                }
                reader.close();
            } catch (Exception e) {
                continue;
            }
            validBackends.add(theBackendAddress);
        }
        if (validBackends.size() == 0) {            
            theBackends.getFarmBackendAddresses(forRegion).clear();
            theBackends.save();
            return;
        }
        // TODO(schreib): Eventually this might be a good place for load balancing
        // logic, to ensure the requests-per-backend load is distributed roughly evenly
        // rather than clobbering one unlucky backend. This may also be a good place
        // for rate-limiting logic to avoid overloading backends: we can always just
        // not issue new requests if all of the backends are overloaded.
        String theBackendAddress = validBackends.get(new Random().nextInt(validBackends.size()));
        theBackends.getFarmBackendAddresses(forRegion).retainAll(validBackends);
        theBackends.save();
        
        // Send the match request to the request farm backend. Repeat until we can confirm
        // that the request has been received successfully, or until it has failed enough
        // times that it's unlikely it will succeed in the future.
        int nIssueRequestAttempt = 0;
        //String requestPayload = URLEncoder.encode(requestJSON.toString(), "UTF-8");
        String requestPayload = requestJSON.toString();
        while (true) {        	
	        try {	        	
	            URL url = new URL("http://" + theBackendAddress + ":9125/");
	            
	            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	            connection.setDoOutput(true);
	            connection.setRequestMethod("POST");

	    		PrintWriter pw = new PrintWriter(connection.getOutputStream());
	    		/*
	    		pw.println("POST / HTTP/1.0");
	    		pw.println("Content-Length: " + requestPayload.length());
	    		pw.println();
	    		*/
	    		pw.print(requestPayload);	    		
	    		pw.flush();
	    		pw.close();

	            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {	            	
	                break;
	            } else {
	                // Server returned HTTP error code.	            	
	            	Logger.getAnonymousLogger().severe("Got error response from request farm: " + connection.getResponseCode() + " " + connection.getResponseMessage());
	            }
	        } catch (Exception e) {
	        	if (nIssueRequestAttempt > 59) {
	        		Logger.getAnonymousLogger().severe("Gave up after " + nIssueRequestAttempt + " attempts to contact request farm.");
	        		throw new RuntimeException(e);
	        	}
	        }
	        nIssueRequestAttempt++;
	        try {
	        	// Sleep for a few milliseconds to give the request farm a chance to come back.
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
        //Counter.increment("Tiltyard.Scheduling.Round.Success");
    }
    
    public MachineState getState(StateMachine theMachine) {
        if (theMatch.getMostRecentState() == null)
            return null;
        return theMachine.getMachineStateFromSentenceList(theMatch.getMostRecentState());
    }

    public void setPendingMove(int nRoleIndex, String move) {
        this.pendingMoves[nRoleIndex] = move;
    }
    
    public void setPendingError(int nRoleIndex, String error) {
        this.pendingErrors[nRoleIndex] = error;
    }    

    public boolean allPendingMovesSubmitted() {
        for (int i = 0; i < pendingMoves.length; i++) {
            if (pendingMoves[i] == null) return false;
        }
        return true;
    }

    public StateMachine getMyStateMachine() {
        StateMachine theMachine = new ProverStateMachine();
        theMachine.initialize(Game.loadFromJSON(theGameJSON.getValue()).getRules());
        return theMachine;
    }

    public static final String SPECTATOR_SERVER = "http://matches.ggp.org/";
    public String publish() {
    	int nAttempt = 0;
    	while (true) {
	    	try {
	    		return MatchPublisher.publishToSpectatorServer(SPECTATOR_SERVER, theMatch);
	    	} catch (IOException ie) {
	    		if (nAttempt > 10) {
	    			throw new RuntimeException(ie);
	    		} else if (nAttempt > 7) {	    		
	    			Logger.getAnonymousLogger().severe("Caught exception while publishing: " + ie.toString() + " ... " + ie.getCause());
	    		}
	    	}
    	    nAttempt++;
	    	try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				;
			}	    	
    	}
    }

    void deflateForSaving() {
        theMatchJSON = new Text(theMatch.toJSON());
        theAuthToken = theMatch.getSpectatorAuthToken();
    }

    void inflateAfterLoading() {
        try {
            theMatch = new Match(theMatchJSON.getValue(), Game.loadFromJSON(theGameJSON.getValue()), theAuthToken);
            theMatch.setCryptographicKeys(StoredCryptoKeys.loadCryptoKeys("Tiltyard"));
            theMatch.enableScrambling();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    void inflateAfterLoading(EncodedKeyPair theKeys) {
        try {
            theMatch = new Match(theMatchJSON.getValue(), Game.loadFromJSON(theGameJSON.getValue()), theAuthToken);
            theMatch.setCryptographicKeys(theKeys);
            theMatch.enableScrambling();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void save() {
        deflateForSaving();
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();
    }
    
    public void delete() {
    	Persistence.clearSpecific(getMatchKey(), MatchData.class);
    }

    /* Static accessor methods */
    public static Set<MatchData> loadMatches() throws IOException {
        Set<MatchData> s = Persistence.loadAll(MatchData.class);
        for (MatchData m : s) {
            if (m != null) m.inflateAfterLoading();
        }
        return s;
    }

    public static MatchData loadMatchData(String matchKey) throws IOException {
        MatchData m = Persistence.loadSpecific(matchKey, MatchData.class);
        if (m != null) m.inflateAfterLoading();
        if (m == null) Logger.getAnonymousLogger().severe("Could not load match: " + matchKey);
        return m;
    }    
}