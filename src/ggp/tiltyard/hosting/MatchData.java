package ggp.tiltyard.hosting;

import ggp.tiltyard.backends.BackendRegistration;
import ggp.tiltyard.backends.Backends;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import com.google.appengine.api.datastore.Text;

import org.ggp.galaxy.shared.crypto.BaseCryptography.EncodedKeyPair;
import org.ggp.galaxy.shared.game.Game;
import org.ggp.galaxy.shared.gdl.factory.GdlFactory;
import org.ggp.galaxy.shared.gdl.scrambler.GdlScrambler;
import org.ggp.galaxy.shared.match.Match;
import org.ggp.galaxy.shared.match.MatchPublisher;
import org.ggp.galaxy.shared.persistence.Persistence;
import org.ggp.galaxy.shared.server.request.RequestBuilder;
import org.ggp.galaxy.shared.statemachine.MachineState;
import org.ggp.galaxy.shared.statemachine.Move;
import org.ggp.galaxy.shared.statemachine.Role;
import org.ggp.galaxy.shared.statemachine.StateMachine;
import org.ggp.galaxy.shared.statemachine.exceptions.GoalDefinitionException;
import org.ggp.galaxy.shared.statemachine.exceptions.MoveDefinitionException;
import org.ggp.galaxy.shared.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.galaxy.shared.statemachine.implementation.prover.ProverStateMachine;
import org.ggp.galaxy.shared.symbol.factory.exceptions.SymbolFormatException;
import org.json.JSONException;
import org.json.JSONObject;

@PersistenceCapable
public class MatchData {	
    // NOTE: matchKey is the unique identifier under which this match has been
    // published to the http://matches.ggp.org/ spectator server. The complete
    // match can thus be found at http://matches.ggp.org/matches/X/ where X is
    // the matchKey.
    @PrimaryKey @Persistent private String matchKey;

	@Persistent private String[] playerURLs;
	@Persistent private boolean[] playsRandomly;
    @Persistent private String[] pendingMoves;
    @Persistent private String[] pendingErrors;
    @Persistent private Text theGameJSON;
    @Persistent private Text theMatchJSON;
    @Persistent private String theAuthToken;
    
    private Match theMatch;
    
    public MatchData(String matchId, List<String> playerNames, List<String> playerURLs, int analysisClock, int startClock, int playClock, Game theGame) throws IOException {        
        try {
            JSONObject theSerializedGame = new JSONObject(theGame.serializeToJSON());            
            theSerializedGame.remove("theStylesheet");
            theSerializedGame.remove("theDescription");
            theGameJSON = new Text(theSerializedGame.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // TODO(schreib): Add support for the analysis clock here.
        theMatch = new Match(matchId, analysisClock, startClock, playClock, theGame);
        theMatch.setCryptographicKeys(StoredCryptoKeys.loadCryptoKeys("Tiltyard"));        
        this.playerURLs = playerURLs.toArray(new String[]{});        
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
    
    public boolean isWedged() {
    	// Assume the match is wedged/completed after time sufficient for 256+ moves has passed.
    	// Later on, this can be refined to look at the average step count in the game being played.
    	return getElapsedTime() > 1000L*theMatch.getStartClock() + 256L*1000L*theMatch.getPlayClock();
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
    
    public void issueRequestTo(int nRole, String requestContent, boolean isStart) throws IOException {
    	try {
	    	JSONObject theRequestJSON = new JSONObject();
	    	theRequestJSON.put("requestContent", requestContent);
	    	theRequestJSON.put("timeoutClock", isStart ? theMatch.getStartClock()*1000 : theMatch.getPlayClock()*1000);
	    	theRequestJSON.put("callbackURL", "http://tiltyard.ggp.org/hosting/callback");
	    	theRequestJSON.put("matchId", theMatch.getMatchId());	    	
	    	theRequestJSON.put("matchKey", matchKey);
	    	if (playerURLs[nRole] == null) return;

            String playerAddress = playerURLs[nRole];
            if (playerAddress.startsWith("http://")) {
                playerAddress = playerAddress.replace("http://", "");
            }
            if (playerAddress.endsWith("/")) {
                playerAddress = playerAddress.substring(0, playerAddress.length()-1);
            }
            String[] splitAddress = playerAddress.split(":");
    		theRequestJSON.put("targetHost", splitAddress[0]);
    		theRequestJSON.put("targetPort", Integer.parseInt(splitAddress[1]));
    		theRequestJSON.put("forPlayerName", "PLAYER"); //theMatch.getPlayerNamesFromHost().get(nRole));
    		
    		theRequestJSON.put("playerIndex", nRole);
    		theRequestJSON.put("forStep", getStepCount());
	    	
	    	issueRequest(theRequestJSON);
    	} catch (JSONException je) {
    		throw new RuntimeException(je);
    	}
    }
    
    public void issueStartRequests() throws IOException {
    	List<Role> theRoles = Role.computeRoles(theMatch.getGame().getRules());
    	for (int i = 0; i < playerURLs.length; i++) {
    		if (playerURLs[i] == null) continue;
    		issueRequestTo(i, RequestBuilder.getStartRequest(theMatch.getMatchId(), theRoles.get(i), theMatch.getGame().getRules(), theMatch.getStartClock(), theMatch.getPlayClock(), theMatch.getGdlScrambler()), true);
    	}
    }
    
    public void issueRequestForAll(String requestContent) throws IOException {
    	for (int i = 0; i < playerURLs.length; i++) {
    		if (playerURLs[i] == null) continue;
    		issueRequestTo(i, requestContent, false);
    	}
    }

    private static void issueRequest(JSONObject requestJSON) throws IOException {
        // Find a backend server to run the request. As part of this process,
        // ping all of the registered backend servers to verify that they're
        // still available, and deregister those that aren't. Lastly choose
        // randomly from the remaining ones.
        Backends theBackends = Backends.loadBackends();
        List<String> validBackends = new ArrayList<String>();
        for (String theBackendAddress : theBackends.getFarmBackendAddresses()) {
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
            //Counter.increment("Tiltyard.Scheduling.Backend.Errors");
            theBackends.getFarmBackendAddresses().clear();
            theBackends.save();
            return;
        }
        // TODO(schreib): Eventually this might be a good place for load balancing
        // logic, to ensure the requests-per-backend load is distributed roughly evenly
        // rather than clobbering one unlucky backend. This may also be a good place
        // for rate-limiting logic to avoid overloading backends: we can always just
        // not issue new requests if all of the backends are overloaded.
        String theBackendAddress = validBackends.get(new Random().nextInt(validBackends.size()));
        theBackends.getFarmBackendAddresses().retainAll(validBackends);
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
	            }
	        } catch (Exception e) {
	        	if (nIssueRequestAttempt > 9) {
	        		throw new RuntimeException(e);
	        	}
	        }
	        nIssueRequestAttempt++;
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
        return m;
    }    
}