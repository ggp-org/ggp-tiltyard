package ggp.apollo;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import org.datanucleus.store.query.AbstractQueryResult;

@PersistenceCapable
public class KnownMatch {
    @PrimaryKey @Persistent private long theTimeStamp;
    @Persistent private String theSpectatorURL;
    @Persistent private int[] thePlayers;

    public KnownMatch(String theSpectatorURL, int[] thePlayers) {
        this.theSpectatorURL = theSpectatorURL;
        this.thePlayers = thePlayers;
        this.theTimeStamp = System.currentTimeMillis();
        
        PersistenceManager pm = PMF.get().getPersistenceManager();
        pm.makePersistent(this);
        pm.close();
    }

    public int[] getPlayers() {
        return thePlayers;
    }

    public String getSpectatorURL() {
        return theSpectatorURL;
    }

    public long getTimeStamp() {
        return theTimeStamp;
    }

    /* Static accessor methods */
    public static Set<KnownMatch> loadKnownMatches() throws IOException {
        Set<KnownMatch> theData = new HashSet<KnownMatch>();
        PersistenceManager pm = PMF.get().getPersistenceManager();
        try {
            Iterator<?> sqr = ((AbstractQueryResult) pm.newQuery(KnownMatch.class).execute()).iterator();
            while (sqr.hasNext()) {
                theData.add((KnownMatch)sqr.next());
            }            
        } catch(JDOObjectNotFoundException e) {
            ;
        } finally {
            pm.close();
        }
        return theData;
    }
    
    public static KnownMatch loadKnownMatch(long theTimeStamp) throws IOException {
        KnownMatch theData = null;
        PersistenceManager pm = PMF.get().getPersistenceManager();
        try {
            theData = pm.detachCopy(pm.getObjectById(KnownMatch.class, theTimeStamp));
        } catch(JDOObjectNotFoundException e) {
            ;
        } finally {
            pm.close();
        }
        return theData;
    }
    
    public static void clearDataPoints() throws IOException {
        Set<KnownMatch> theData = loadKnownMatches();

        for (KnownMatch m : theData) {
            PersistenceManager pm = PMF.get().getPersistenceManager();
            try {
                pm.deletePersistent(pm.getObjectById(KnownMatch.class, m.getTimeStamp()));
            } catch(JDOObjectNotFoundException e) {
                ;
            } finally {
                pm.close();
            }
        }
    }
}