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
    	// When a region is requested, return the set of backends from that region;
    	// if there are no backends registered for that region, fall back to any
    	// available backend for any region.
    	if (forRegion.equals(Player.REGION_EU) && theFarmBackendAddressesEurope.size() > 0) {
    		return theFarmBackendAddressesEurope;
    	} else if (forRegion.equals(Player.REGION_US) && theFarmBackendAddressesUS.size() > 0) {
    		return theFarmBackendAddressesUS;
    	} else {
    		return theFarmBackendAddresses;
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