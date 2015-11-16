package ggp.tiltyard.scheduling;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import org.ggp.galaxy.shared.persistence.Persistence;

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
    @Persistent private String tournamentConfigYAML;    
    
    // When the tournament begins, a fixed seeding is chosen based on
    // a random assignment of the available players. This seeding must
    // be persisted because it will be used for scheduling purposes for
    // the rest of the tournament.
    @Persistent private String persistedSeeding;
    
    // When a tournament match begins, we establish a mapping from the
    // public match identifier to the tournament-internal match id.
    @Persistent private String serializedPublicToInternalMatchIdMap;
    
    // Has the tournament begun yet?
    @Persistent private Boolean hasBegun;
    
    // Has the tournament finished yet?
    @Persistent private Boolean hasFinished;
    
    // Data structure with all of the immutable configuration settings
    // for the tournament, decoded from tournamentConfigYAML. This is
    // generated from persisted data but is not, itself, persisted.
    @NotPersistent private Tournament theTournament = null;

    // Data structure with the mapping from public match IDs
    // to the internal match IDs. This is generated from persisted data
    // but is not, itself, persisted.
    @NotPersistent private Map<String, String> publicToInternalMatchIdMap;
    
    // Called to construct a new tournament. This should happen at least a day
    // before the tournament is actually expected to begin, to allow time for
    // the scheduler to wind down existing matches.
    public TournamentData(String key, String configYAML) {
    	tournamentKey = key;
    	tournamentConfigYAML = configYAML;
    	persistedSeeding = null;
    	hasBegun = Boolean.FALSE;
    	hasFinished = Boolean.FALSE;
    	serializedPublicToInternalMatchIdMap = "";
    	inflateAfterLoading();
    	save();
    }
    
    public String getTournamentKey() {
    	return tournamentKey;
    }
    
    public Tournament getTournament() {
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
    
    public Map<String,String> getPublicToInternalMatchIdMap() {
    	return publicToInternalMatchIdMap;
    }
    
    // This should be called once the time to begin the tournament has arrived,
    // to establish a seeding based on a random arrangement of the players who
    // are available and opted-in at the beginning of the tournament.
    public void beginTournament(String persistedSeeding) {
    	hasBegun = Boolean.TRUE;
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
    
    // This should be called when the tournament has no additional matches left
    // to schedule, and no longer needs to be considered during scheduling.
    public void finishTournament() {
    	hasFinished = Boolean.TRUE;
    	save();
    }
    
    void deflateForSaving() {
    	serializedPublicToInternalMatchIdMap = serializeStringHashMap(publicToInternalMatchIdMap);
    }

    void inflateAfterLoading() {
    	theTournament = TournamentSpecParser.parseYamlString(tournamentConfigYAML);
    	publicToInternalMatchIdMap = deserializeStringHashMap(serializedPublicToInternalMatchIdMap);
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