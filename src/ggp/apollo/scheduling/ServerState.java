package ggp.apollo.scheduling;

import ggp.apollo.Persistence;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

@PersistenceCapable
public class ServerState {
    @SuppressWarnings("unused")
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent private Set<String> theRunningMatches;

    private ServerState() {
        thePrimaryKey = "ServerState";
        theRunningMatches = new HashSet<String>();
    }

    public Set<String> getRunningMatches() {
        return theRunningMatches;
    }

    public void save() {
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();        
    }

    /* Static accessor methods */
    public static ServerState loadState() throws IOException {
        Set<ServerState> theStates = Persistence.loadAll(ServerState.class);
        if (theStates.size() > 0) {
            return theStates.iterator().next();
        } else {
            return new ServerState();
        }
   }
}