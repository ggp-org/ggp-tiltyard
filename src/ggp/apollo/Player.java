package ggp.apollo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

@PersistenceCapable
public class Player {
    @PrimaryKey @Persistent private String theName;    
    @Persistent private Set<String> theOwners;    
    @Persistent private boolean isEnabled;
    @Persistent private String gdlVersion;    
    @Persistent private String theURL;
    @Persistent private Integer nStrikes;
    @Persistent private String pingStatus;
    @Persistent private String pingError;
    
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
        
        this.nStrikes = 0;
        
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
        this.nStrikes = 0;
        if (isEnabled == false) {
            this.pingStatus = null;
            this.pingError = null;
        } else if (isEnabled == true) {
            this.pingStatus = "waiting";
            this.pingError = null;
        }
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
    
    public boolean isOwner(String userId) {
        return theOwners.contains(userId);
    }
    
    public void addStrike() {
        if (nStrikes == null) {
            nStrikes = 0;
        }
        if (nStrikes > 2) {
            setEnabled(false);
        } else {
            nStrikes++;
        }
    }
    
    public void resetStrikes() {
        if (nStrikes == null) {
            nStrikes = 0;
        }        
        nStrikes = 0;
    }
    
    public void setPingStatus(String theNewStatus, String theError) {
        pingStatus = theNewStatus;
        pingError = theError;
    }
    
    public String getPingStatus() {
        return pingStatus;
    }
    
    public String getPingError() {
        return pingStatus;
    }
    
    public JSONObject asJSON(boolean includePrivate, boolean includeMatches) throws IOException {
        try {
            JSONObject theJSON = new JSONObject();
            theJSON.put("name", theName);
            theJSON.put("isEnabled", isEnabled);
            theJSON.put("gdlVersion", gdlVersion);
            theJSON.put("visibleEmail", visibleEmail);
            theJSON.put("pingStatus", pingStatus);
            if (includePrivate) {
                // Not sure if we want to expose the userID information,
                // even to the owners themselves.
                //theJSON.put("theOwners", theOwners);
                theJSON.put("theURL", theURL);
                theJSON.put("pingError", pingError);
            }
            if (includeMatches) {
                JSONArray theMatches = new JSONArray();
                for (String recentMatchURL : recentMatchURLs) {
                    CondensedMatch c = CondensedMatch.loadCondensedMatch(recentMatchURL);
                    if (!c.isReady()) continue;
                    theMatches.put(c.getCondensedJSON());
                }
                theJSON.put("recentMatches", theMatches);
            }
            return theJSON;
        } catch (JSONException e) {
            return null;
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