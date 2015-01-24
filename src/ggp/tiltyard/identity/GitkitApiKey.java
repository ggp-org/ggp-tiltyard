package ggp.tiltyard.identity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import org.ggp.galaxy.shared.persistence.Persistence;

import com.google.appengine.api.datastore.Blob;

@PersistenceCapable
public class GitkitApiKey {
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent private Blob theKey;

    private GitkitApiKey() {
        thePrimaryKey = "GitkitApiKey";
    }
    
    public static void persistKey(byte[] theKey) {
    	GitkitApiKey apiKey = new GitkitApiKey();
    	apiKey.theKey = new Blob(theKey);
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(apiKey);
        pm.close();
    }
    
    public static InputStream getApiKey() {
        Set<GitkitApiKey> theConfigs = Persistence.loadAll(GitkitApiKey.class);
        if (theConfigs.size() > 0) {
            GitkitApiKey theApiKey = theConfigs.iterator().next();
            return new ByteArrayInputStream(theApiKey.theKey.getBytes());
        } else {
        	throw new RuntimeException("Could not load API key!");
        }
    }
}