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
    @Persistent Set<RunningMatch> theRunningMatches;
    
    static class RunningMatch {
        public String theURL;
        public int[] thePlayers;
    }
    
    private ServerState() {
        theRunningMatches = new HashSet<RunningMatch>();
    }
    
    public Set<RunningMatch> getRunningMatches() {
        return theRunningMatches;
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