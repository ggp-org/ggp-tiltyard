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
public class ServerState {
    @SuppressWarnings("unused")
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent private Set<String> theRunningMatches;
    @Persistent private String theBackendAddress;
    @Persistent private Integer theSchedulingRound;
    
    private ServerState() {
        thePrimaryKey = "ServerState";
        theRunningMatches = new HashSet<String>();
        theBackendAddress = "";
        theSchedulingRound = 0;
    }
    
    public Set<String> getRunningMatches() {
        return theRunningMatches;
    }
    
    public void setBackendAddress(String theBackendAddress) {
        this.theBackendAddress = theBackendAddress;
    }

    public String getBackendAddress() {
        return theBackendAddress;
    }

    public void incrementSchedulingRound() {
        this.theSchedulingRound++;
    }

    public int getSchedulingRound() {
        return theSchedulingRound;
    }    
    
    public void save() {
        PersistenceManager pm = PMF.get().getPersistenceManager();
        pm.makePersistent(this);
        pm.close();        
    }
    
    /* Static accessor methods */
    public static ServerState loadState() throws IOException {
        PersistenceManager pm = PMF.get().getPersistenceManager();
        try {
            Iterator<?> sqr = ((AbstractQueryResult) pm.newQuery(ServerState.class).execute()).iterator();
            while (sqr.hasNext()) {
                return (ServerState)sqr.next();
            }            
        } catch(JDOObjectNotFoundException e) {
            ;
        } finally {
            pm.close();
        }
        return new ServerState(); 
    }
}