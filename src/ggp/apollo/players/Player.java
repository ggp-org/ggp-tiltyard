package ggp.apollo.players;

import ggp.apollo.Persistence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.*;

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
    
    // Optional fields.
    @Persistent private String visibleEmail;
    @Persistent private String visibleWebsite;
    @Persistent private Boolean isPingable;

    public Player(String theName, String theURL, String anOwner) {
        this.theName = theName;
        this.setURL(theURL);
        
        this.setEnabled(false);
        this.setPingable(true);
        this.setGdlVersion("GDLv1");
        this.theOwners = new HashSet<String>();
        this.theOwners.add(anOwner);        
        
        this.setVisibleEmail("");
        this.setVisibleWebsite("");
        
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
    
    public void setVisibleWebsite(String visibleWebsite) {
        this.visibleWebsite = visibleWebsite;
    }

    public String getVisibleWebsite() {
        return visibleWebsite;
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
    
    public void setPingable(boolean isPingable) {
        this.isPingable = isPingable;
    }
    
    public boolean isPingable() {
        return isPingable;
    }
    
    public void setGdlVersion(String gdlVersion) {
        this.gdlVersion = gdlVersion;
    }

    public String getGdlVersion() {
        return gdlVersion;
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
        return pingError;
    }
    
    public JSONObject asJSON(boolean includePrivate) throws IOException {
        try {
            JSONObject theJSON = new JSONObject();
            theJSON.put("name", theName);
            theJSON.put("isEnabled", isEnabled);
            theJSON.put("isPingable", isPingable);
            theJSON.put("gdlVersion", gdlVersion);
            theJSON.put("visibleEmail", visibleEmail);
            theJSON.put("visibleWebsite", visibleWebsite);
            theJSON.put("pingStatus", pingStatus);
            if (includePrivate) {
                // Not sure if we want to expose the userID information,
                // even to the owners themselves.
                //theJSON.put("theOwners", theOwners);
                theJSON.put("theURL", theURL);
                theJSON.put("pingError", pingError);
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
    @SuppressWarnings("unchecked")
    public static List<Player> loadEnabledPlayers() throws IOException {
        PersistenceManager pm = Persistence.getPersistenceManager();
        Query q = pm.newQuery(Player.class);
        q.setFilter("isEnabled == true");
        List<Player> toReturn = new ArrayList<Player> ((List<Player>) q.execute());
        q.closeAll();
        pm.close();
        return toReturn;
    }
    
    public static Set<Player> loadPlayers() throws IOException {
        return Persistence.loadAll(Player.class);
    }
    
    public static Player loadPlayer(String theKey) throws IOException {
        return Persistence.loadSpecific(theKey, Player.class);
    }
}