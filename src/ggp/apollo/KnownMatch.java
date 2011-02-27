package ggp.apollo;

import java.io.IOException;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

@PersistenceCapable
public class KnownMatch {
    @PrimaryKey @Persistent private String theTimeStamp;
    @Persistent private String theSpectatorURL;
    @Persistent private int[] thePlayers;

    public KnownMatch(String theSpectatorURL, int[] thePlayers) {
        this.theSpectatorURL = theSpectatorURL;
        this.thePlayers = thePlayers;
        this.theTimeStamp = "" + System.currentTimeMillis();
        
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();
    }

    public int[] getPlayers() {
        return thePlayers;
    }

    public String getSpectatorURL() {
        return theSpectatorURL;
    }

    public String getTimeStamp() {
        return theTimeStamp;
    }

    /* Static accessor methods */
    public static Set<KnownMatch> loadKnownMatches() throws IOException {
        return Persistence.loadAll(KnownMatch.class);
    }
    
    public static KnownMatch loadKnownMatch(String theTimeStamp) throws IOException {
        return Persistence.loadSpecific(theTimeStamp, KnownMatch.class);
    }
}