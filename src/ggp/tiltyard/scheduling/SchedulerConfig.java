package ggp.tiltyard.scheduling;

import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import org.ggp.galaxy.shared.persistence.Persistence;

@PersistenceCapable
public class SchedulerConfig {
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent private boolean isDrained;
    @Persistent private boolean isDrainedExceptTournament;

    private SchedulerConfig() {
        thePrimaryKey = "SchedulerConfig";
        isDrained = false;
        isDrainedExceptTournament = false;
    }
    
    public boolean isDrained() {
    	return isDrained;
    }
    
    public boolean isDrainedExceptTournament() {
    	return isDrainedExceptTournament;
    }

    /* Static accessor methods */
    public static SchedulerConfig loadConfig() {
        Set<SchedulerConfig> theConfigs = Persistence.loadAll(SchedulerConfig.class);
        if (theConfigs.size() > 0) {
            return theConfigs.iterator().next();
        } else {
            SchedulerConfig config = new SchedulerConfig();
            PersistenceManager pm = Persistence.getPersistenceManager();
            pm.makePersistent(config);
            pm.close();
            return config;
        }
   }
}