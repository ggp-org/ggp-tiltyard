package ggp.tiltyard.scheduling;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import net.alloyggp.tournament.api.TAdminAction;
import net.alloyggp.tournament.api.TAdminActions;
import net.alloyggp.tournament.api.TMatchResult;
import net.alloyggp.tournament.api.TNextMatchesResult;
import net.alloyggp.tournament.api.TRanking;
import net.alloyggp.tournament.api.TSeeding;
import net.alloyggp.tournament.api.TTournament;
import net.alloyggp.tournament.api.TTournamentSpecParser;

import org.ggp.base.util.loader.RemoteResourceLoader;
import org.ggp.galaxy.shared.persistence.Persistence;

import com.google.appengine.api.datastore.Text;

import external.JSON.JSONArray;
import external.JSON.JSONException;
import external.JSON.JSONObject;

@PersistenceCapable
public class TournamentData {
	// NOTE: tournamentKey is the identifier that is stored in the
	// "tournamentNameFromHost" field in each match in the tournament.
	// It should be unique across all of the tournaments run on the
	// Tiltyard match hosting system.
    @PrimaryKey @Persistent private String tournamentKey;

    // YAML definition containing all of the immutable configuration
    // settings for the tournament, like start time, tournament type,
    // internal and display names, etc, encoded in YAML.
    @Persistent private Text tournamentConfigYAML;
    
    // When the tournament begins, a fixed seeding is chosen based on
    // a random assignment of the available players. This seeding must
    // be persisted because it will be used for scheduling purposes for
    // the rest of the tournament.
    @Persistent private String persistedSeeding;
    
    // When the tournament begins, the set of players involved in the
    // tournament is chosen and will remain fixed from that point on.
    @Persistent private Set<String> playersInvolved;
    
    // When a tournament match begins, we establish a mapping from the
    // public match identifier to the tournament-internal match id.
    @Persistent private Text serializedPublicToInternalMatchIdMap;
    
    // Has the tournament begun yet?
    @Persistent private Boolean hasBegun;
    
    // Has the tournament finished yet?
    @Persistent private Boolean hasFinished;

    // Administrative actions that have been issues for this tournament.
    @Persistent private List<String> persistedAdminActions;
    
    // Cache of the display data for the tournament, so that it doesn't
    // have to be recomputed on every user request.
    @Persistent private Text cachedDisplayData;
    
    // Data structure with all of the immutable configuration settings
    // for the tournament, decoded from tournamentConfigYAML. This is
    // generated from persisted data but is not, itself, persisted.
    @NotPersistent private TTournament theTournament = null;

    // Data structure with the mapping from public match IDs
    // to the internal match IDs. This is generated from persisted data
    // but is not, itself, persisted.
    @NotPersistent private Map<String, String> publicToInternalMatchIdMap;
    
    // Called to construct a new tournament. This should happen at least a day
    // before the tournament is actually expected to begin, to allow time for
    // the scheduler to wind down existing matches.
    public TournamentData(String key, String configYAML) {
    	tournamentKey = key;
    	tournamentConfigYAML = new Text(configYAML);
    	persistedSeeding = null;
    	hasBegun = Boolean.FALSE;
    	hasFinished = Boolean.FALSE;
    	serializedPublicToInternalMatchIdMap = new Text("");
    	cachedDisplayData = new Text("{}");
    	persistedAdminActions = new ArrayList<String>();
    	inflateAfterLoading();
    	save();
    }
    
    public String getTournamentKey() {
    	return tournamentKey;
    }
    
    public TTournament getTournament() {
    	return theTournament;
    }
    
    public boolean hasBegun() {
    	return hasBegun;
    }
    
    public boolean hasFinished() {
    	return hasFinished;
    }
    
    public String getPersistedSeeding() {
        return persistedSeeding;
    }
    
    public String lookupInternalMatchID(String publicMatchID) {
    	return publicToInternalMatchIdMap.get(publicMatchID);
    }
    
    public boolean hasInternalMatchID(String internalMatchID) {
    	return publicToInternalMatchIdMap.values().contains(internalMatchID);
    }
    
    public Set<String> getPlayersInvolved() {
    	return playersInvolved;
    }
    
    public List<TAdminAction> getAdminActions() {
    	List<TAdminAction> adminActions = new ArrayList<TAdminAction>();
    	if (persistedAdminActions != null) {
    		for (String persistedAdminAction : persistedAdminActions) {
    			TAdminAction adminAction = TAdminActions.fromPersistedString(persistedAdminAction);
    			adminActions.add(adminAction);
    		}
    	}
    	return adminActions;
    }
    
    public void updateDisplayDataCache() {
    	try {
	    	JSONObject displayData = new JSONObject();
	    	
	    	TSeeding theSeeding = getSeeding();
	    	Set<TMatchResult> theMatchResults = getMatchResultsSoFar();
	    	
	    	displayData.put("id", getTournamentKey());
	    	displayData.put("name", theTournament.getDisplayName());
	    	displayData.put("standings", theTournament.getCurrentStandings(theSeeding, theMatchResults, getAdminActions()).toString().replace("\n", "\n <br> "));
	    	displayData.put("hasBegun", hasBegun);
	    	displayData.put("hasFinished", hasFinished);
	    	displayData.put("matchIdMapDebugString", publicToInternalMatchIdMap.toString());
	    	{
	    		JSONArray standingsHistory = new JSONArray();
	    		for (TRanking aRanking : theTournament.getStandingsHistory(theSeeding, theMatchResults, getAdminActions())) {
	    			standingsHistory.put(aRanking.toString());
	    		}
	    		displayData.put("standingsHistory", standingsHistory);
	    	}
	    	{
	    		JSONArray thePlayers = new JSONArray();
	    		for (String player : getPlayersInvolved()) {
	    			thePlayers.put(player);
	    		}
	    		displayData.put("players", thePlayers);
	    	}
	    	{
	    		JSONArray adminActions = new JSONArray();
	    		for (TAdminAction aAction : getAdminActions()) {
	    			adminActions.put(aAction.toString());
	    		}
	    		displayData.put("adminActions", adminActions);
	    	}
	    	cachedDisplayData = new Text(displayData.toString());
    	} catch (JSONException je) {
    		Logger.getAnonymousLogger().log(Level.SEVERE, "Could not serialize tournament display data: " + je, je);
    	}
    }
    
    public String getDisplayData() {
    	if (cachedDisplayData == null) {
    		updateDisplayDataCache();
    	}
    	if (cachedDisplayData == null) {
    		return "{}";
    	} else {
    		return cachedDisplayData.getValue();
    	}
    }
    
    // This should be called once the time to begin the tournament has arrived,
    // to establish a seeding based on a random arrangement of the players who
    // are available and opted-in at the beginning of the tournament.
    public void beginTournament(Set<String> playersInvolved, String persistedSeeding) {
    	hasBegun = Boolean.TRUE;
    	this.playersInvolved = playersInvolved;
    	this.persistedSeeding = persistedSeeding;
    	save();
    }
    
    // This should be called when a new match begins, to record the association
    // between the match's public identifier and its internal identifier that's
    // used for tournament scheduling.
    public void recordMatch(String publicID, String internalID) {
    	publicToInternalMatchIdMap.put(publicID, internalID);
    	save();
    }
    
    public void recordAdminAction(String persistableAdminAction) {
    	persistedAdminActions.add(persistableAdminAction);
    	save();
    	updateDisplayDataCache();
    }
    
    // This should be called when the tournament has no additional matches left
    // to schedule, and no longer needs to be considered during scheduling.
    public void finishTournament() {
    	hasFinished = Boolean.TRUE;
    	save();
    }
    
    // Get the next matches to run, based on the seeding and the match results
    // for the tournament so far.
    public TNextMatchesResult getNextMatches() {
    	return getTournament().getMatchesToRun(getSeeding(), getMatchResultsSoFar(), getAdminActions());
    }
    
    // Get the seeding based on the persisted data.
    private TSeeding getSeeding() {
		return TSeeding.fromPersistedString(getPersistedSeeding());
    }

    // Get the match results for the tournament by querying the database server
    // for all matches on Tiltyard with the right tournament name. 
    private Set<TMatchResult> getMatchResultsSoFar() {
    	try {
	    	JSONObject theTournamentMatchesJSON = RemoteResourceLoader.loadJSON("http://database.ggp.org/query/filterTournament,recent1000,90bd08a7df7b8113a45f1e537c1853c3974006b2," + getTournamentKey());		
	    	Set<TMatchResult> matchResults = new HashSet<TMatchResult>();
	    	JSONArray theMatches = theTournamentMatchesJSON.getJSONArray("queryMatches");
	    	for (int i = 0; i < theMatches.length(); i++) {
	    		JSONObject aMatchJSON = theMatches.getJSONObject(i);
	    		String internalMatchID = lookupInternalMatchID(aMatchJSON.getString("matchURL").replace("http://matches.ggp.org/matches/", "").replace("/", ""));
	    		if (aMatchJSON.getBoolean("isAborted")) {
	    			matchResults.add(TMatchResult.getAbortedMatchResult(internalMatchID));
	    		} else if (aMatchJSON.getBoolean("isCompleted")) {
	    			List<Integer> theGoals = new ArrayList<Integer>();
	    			for (int j = 0; j < aMatchJSON.getJSONArray("goalValues").length(); j++) {
	    				theGoals.add(aMatchJSON.getJSONArray("goalValues").getInt(j));
	    			}
	    			matchResults.add(TMatchResult.getSuccessfulMatchResult(internalMatchID, theGoals));
	    		}
	    	}
	    	return matchResults;
    	} catch (Exception e) {
    		Logger.getAnonymousLogger().log(Level.SEVERE, "Could not query match results for tournament " + getTournamentKey() + ": " + e, e);
    		throw new RuntimeException(e);
    	}
    }
    
    void deflateForSaving() {
    	serializedPublicToInternalMatchIdMap = new Text(serializeStringHashMap(publicToInternalMatchIdMap));
    }

    void inflateAfterLoading() {
    	theTournament = TTournamentSpecParser.parseYamlString(tournamentConfigYAML.getValue());
    	publicToInternalMatchIdMap = deserializeStringHashMap(serializedPublicToInternalMatchIdMap.getValue());
    }
    
        
    public void save() {
    	deflateForSaving();
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();
    }
    
    public void delete() {
    	Persistence.clearSpecific(getTournamentKey(), TournamentData.class);
    }
    
    /* Static accessor methods */
    public static Set<TournamentData> loadTournaments() throws IOException {
        Set<TournamentData> s = Persistence.loadAll(TournamentData.class);
        for (TournamentData t : s) {
        	t.inflateAfterLoading();
        }
        return s;
    }

    public static TournamentData loadTournamentData(String tournamentKey) throws IOException {
    	TournamentData t = Persistence.loadSpecific(tournamentKey, TournamentData.class);
    	if (t != null) t.inflateAfterLoading();
        if (t == null) Logger.getAnonymousLogger().severe("Could not load tournament: " + tournamentKey);
        return t;
    }
    
    /* Static serialization routines */
    private String serializeStringHashMap(Map<String,String> map) {
    	StringBuilder b = new StringBuilder();
    	for (String key : map.keySet()) {
    		b.append(key + " " + map.get(key) + " ");
    	}
    	return b.toString().trim();
    }
    
    private Map<String,String> deserializeStringHashMap(String serialized) {
    	Map<String,String> map = new HashMap<String,String>();
    	String[] data = serialized.trim().split(" ");
    	for (int i = 0; i+1 < data.length; i+=2) {
    		map.put(data[i], data[i+1]);
    	}
    	return map;
    }
}