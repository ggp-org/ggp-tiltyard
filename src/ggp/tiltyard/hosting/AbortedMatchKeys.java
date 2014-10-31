package ggp.tiltyard.hosting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import org.ggp.galaxy.shared.persistence.Persistence;

@PersistenceCapable
public class AbortedMatchKeys {
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent private List<String> abortedMatchKeys;

    private AbortedMatchKeys() {
        thePrimaryKey = "AbortedMatchKeys";
        abortedMatchKeys = new ArrayList<String>();
    }
    
    public boolean isRecentlyAborted(String matchKey) {
    	return abortedMatchKeys.contains(matchKey);
    }
    
    public void addRecentlyAborted(String matchKey) {
    	abortedMatchKeys.add(matchKey);
    	if (abortedMatchKeys.size() > 100) {
    		abortedMatchKeys.remove(0);
    	}
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();
    }
    
    public String toString() {
    	return Arrays.deepToString(abortedMatchKeys.toArray());
    }

    /* Static accessor methods */
    public static AbortedMatchKeys loadAbortedMatchKeys() {
        Set<AbortedMatchKeys> theSingletons = Persistence.loadAll(AbortedMatchKeys.class);
        if (theSingletons.size() > 0) {
            return theSingletons.iterator().next();
        } else {
        	AbortedMatchKeys config = new AbortedMatchKeys();
            PersistenceManager pm = Persistence.getPersistenceManager();
            pm.makePersistent(config);
            pm.close();
            return config;
        }
   }
}