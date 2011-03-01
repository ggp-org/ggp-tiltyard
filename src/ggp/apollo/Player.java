package ggp.apollo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

@PersistenceCapable
public class Player {
    @PrimaryKey @Persistent private String theName;    
    @Persistent private Set<String> theOwners;    
    @Persistent private boolean isEnabled;
    @Persistent private String gdlVersion;    
    @Persistent private String theURL;
    
    @Persistent private List<String> recentMatchURLs;
    private static final int kRecentMatchURLsToRecord = 40;
    
    // Optional fields.
    @Persistent private String visibleEmail;

    public Player(String theName, String theURL, String anOwner) {
        this.theName = theName;
        this.setURL(theURL);
        
        this.setEnabled(false);
        this.setGdlVersion("GDLv1");
        this.theOwners = new HashSet<String>();
        this.theOwners.add(anOwner);
        
        this.setVisibleEmail("");
        this.recentMatchURLs = new ArrayList<String>();
        
        save();
    }
    
    public String getName() {
        return theName;
    }
    
    public void setVisibleEmail(String visibleEmail) {
        this.visibleEmail = visibleEmail;
    }

    public String getVisibleEmail() {
        return visibleEmail;
    }

    public void setURL(String theURL) {
        this.theURL = theURL;
    }

    public String getURL() {
        return theURL;
    }

    public void setEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    public boolean isEnabled() {
        return isEnabled;
    }
    
    public void setGdlVersion(String gdlVersion) {
        this.gdlVersion = gdlVersion;
    }

    public String getGdlVersion() {
        return gdlVersion;
    }
    
    public List<String> getRecentMatchURLs() {
        return recentMatchURLs;
    }    
    
    public void addRecentMatchURL(String theURL) {
        recentMatchURLs.add(theURL);
        if (recentMatchURLs.size() > kRecentMatchURLsToRecord) {
            recentMatchURLs.remove(0);
        }
    }
    
    public void save() {
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();        
    }    

    /* Static accessor methods */
    public static Set<Player> loadPlayers() throws IOException {
        return Persistence.loadAll(Player.class);
    }
    
    public static Player loadPlayer(String theKey) throws IOException {
        return Persistence.loadSpecific(theKey, Player.class);
    }
}