package ggp.tiltyard.scheduling;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import org.ggp.galaxy.shared.persistence.Persistence;

@PersistenceCapable
public class ServerState {
    @SuppressWarnings("unused")
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent private Set<String> theRunningMatches;
    @Persistent public boolean isDrained;

    private ServerState() {
        thePrimaryKey = "ServerState";
        theRunningMatches = new HashSet<String>();
        isDrained = false;
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