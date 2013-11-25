package ggp.tiltyard.backends;

import ggp.tiltyard.players.Player;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import org.ggp.galaxy.shared.persistence.Persistence;

@PersistenceCapable
public class Backends {
    @SuppressWarnings("unused")
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent private Set<String> theFarmBackendAddresses;
    @Persistent private Set<String> theFarmBackendAddressesUS;
    @Persistent private Set<String> theFarmBackendAddressesEurope;

    private Backends() {
        thePrimaryKey = "Backends";
        theFarmBackendAddresses = new HashSet<String>();
        theFarmBackendAddressesUS = new HashSet<String>();
        theFarmBackendAddressesEurope = new HashSet<String>();
    }

    public Set<String> getFarmBackendAddresses(String forRegion) {
    	if (forRegion.equals(Player.REGION_EU)) {
    		return theFarmBackendAddressesEurope;
    	} else if (forRegion.equals(Player.REGION_US)) {
    		return theFarmBackendAddressesUS;
    	} else if (forRegion.equals(Player.REGION_ANY)) {
    		return theFarmBackendAddresses;
    	} else {
    		return null;
    	}
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