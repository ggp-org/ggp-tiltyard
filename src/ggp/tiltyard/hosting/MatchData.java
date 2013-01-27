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

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import com.google.appengine.api.datastore.Text;

import org.ggp.galaxy.shared.crypto.BaseCryptography.EncodedKeyPair;
import org.ggp.galaxy.shared.game.Game;
import org.ggp.galaxy.shared.gdl.scrambler.NoOpGdlScrambler;
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
import org.ggp.galaxy.shared.statemachine.implementation.prover.ProverStateMachine;
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
        theMatch = new Match(matchId, startClock, playClock, theGame);
        theMatch.setCryptographicKeys(StoredCryptoKeys.loadCryptoKeys("Artemis"));
        this.playerURLs = playerURLs.toArray(new String[]{});
    	// TODO(schreib): Add support for matches where some players are
    	// authenticated but others aren't. For now, only vouch for players
    	// when all players are authenticated.
        if (!playerNames.contains(null)) {
        	theMatch.setPlayerNamesFromHost(playerNames);   	
        }
        List<Boolean> isPlayerHuman = new ArrayList<Boolean>();
        for (int i = 0; i < playerURLs.size(); i++) {
        	isPlayerHuman.add(playerURLs.get(i) == null);
        }
        theMatch.setWhichPlayersAreHuman(isPlayerHuman);
        // Players named Random will play randomly; all others will not.
        playsRandomly = new boolean[playerNames.size()];
        for (int i = 0; i < playerNames.size(); i++) {
        	playsRandomly[i] = playerNames.get(i) != null && playerNames.get(i).toLowerCase().equals("random");
        }

        // NOTE: This code assumes that the first state for the match will always have
        // a non-forced move for at least one player. If this is not the case, it will
        // need to be updated, since we shouldn't actually begin the match in the state
        // we get from getInitialState().
        StateMachine theMachine = getMyStateMachine();
        MachineState theState = theMachine.getInitialState();
        try {
        	this.setState(theMachine, theState, null);
        } catch (Exception e) {
        	// TODO(schreib): Eventually stop casting this to an IOException and properly
        	// handle ggp-related exceptions in the match setup.
        	throw new IOException(e);
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

    public String[] getPendingMoves() {
        return pendingMoves;
    }
    
    public boolean isCompleted() {
    	return theMatch.isCompleted();
    }
    
    public int getStepCount() {
    	return theMatch.getMoveHistory().size();
    }
    
    public boolean hasComputerPlayers() {
    	for (int i = 0; i < playerURLs.length; i++) {
    		if (playerURLs[i] != null)
    			return true;
    	}
    	return false;
    }

    public void setState(StateMachine theMachine, MachineState state, List<Move> moves) throws MoveDefinitionException, GoalDefinitionException {
        theMatch.appendState(state.getContents());
        theMatch.appendNoErrors();
        if (moves != null) {
            theMatch.appendMoves2(moves);            
        }
        if (theMachine.isTerminal(state)) {
        	theMatch.markCompleted(theMachine.getGoals(state));
        }

        // Clear the current pending moves
        pendingMoves = new String[theMachine.getRoles().size()];

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
    		issueRequestTo(i, RequestBuilder.getStartRequest(theMatch.getMatchId(), theRoles.get(i), theMatch.getGame().getRules(), theMatch.getStartClock(), theMatch.getPlayClock(), new NoOpGdlScrambler()), true);
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
    public String publish() throws IOException {
        return MatchPublisher.publishToSpectatorServer(SPECTATOR_SERVER, theMatch);
    }

    void deflateForSaving() {
        theMatchJSON = new Text(theMatch.toJSON());
        theAuthToken = theMatch.getSpectatorAuthToken();
    }

    void inflateAfterLoading() {
        try {
            theMatch = new Match(theMatchJSON.getValue(), Game.loadFromJSON(theGameJSON.getValue()), theAuthToken);
            theMatch.setCryptographicKeys(StoredCryptoKeys.loadCryptoKeys("Artemis"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    void inflateAfterLoading(EncodedKeyPair theKeys) {
        try {
            theMatch = new Match(theMatchJSON.getValue(), Game.loadFromJSON(theGameJSON.getValue()), theAuthToken);
            theMatch.setCryptographicKeys(theKeys);
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