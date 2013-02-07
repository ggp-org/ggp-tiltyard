package ggp.tiltyard.scheduling;

import java.io.IOException;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import org.ggp.galaxy.shared.persistence.Persistence;

@PersistenceCapable
public class SchedulerConfig {
    @SuppressWarnings("unused")
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent public boolean isDrained;

    private SchedulerConfig() {
        thePrimaryKey = "SchedulerConfig";
        isDrained = false;
    }

    public void save() {
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();        
    }

    /* Static accessor methods */
    public static SchedulerConfig loadConfig() throws IOException {
        Set<SchedulerConfig> theConfigs = Persistence.loadAll(SchedulerConfig.class);
        if (theConfigs.size() > 0) {
            return theConfigs.iterator().next();
        } else {
            SchedulerConfig config = new SchedulerConfig();
            config.save();
            return config;
        }
   }
}