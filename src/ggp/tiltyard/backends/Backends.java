package ggp.tiltyard.backends;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import org.ggp.shared.persistence.Persistence;

@PersistenceCapable
public class Backends {
    @SuppressWarnings("unused")
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent private Set<String> theHostBackendAddresses;
    @Persistent private Set<String> theFarmBackendAddresses;

    private Backends() {
        thePrimaryKey = "Backends";
        theHostBackendAddresses = new HashSet<String>();
        theFarmBackendAddresses = new HashSet<String>();
    }

    public Set<String> getHostBackendAddresses() {
        return theHostBackendAddresses;
    }
    
    public Set<String> getFarmBackendAddresses() {
        return theFarmBackendAddresses;
    }    
    
    public void save() {
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();        
    }

    /* Static accessor methods */
    public static Backends loadBackends() throws IOException {
        Set<Backends> theStates = Persistence.loadAll(Backends.class);
        if (theStates.size() > 0) {
            return theStates.iterator().next();
        } else {
            return new Backends();
        }
   }
}