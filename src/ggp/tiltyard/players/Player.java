package ggp.tiltyard.players;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.*;

import org.ggp.galaxy.shared.persistence.Persistence;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.ServingUrlOptions;
import com.google.appengine.api.users.User;

@PersistenceCapable
public class Player {
    @PrimaryKey @Persistent private String theName;

    // Ownership information.
    @Persistent private Set<String> theOwners;
    @Persistent private Set<String> theOwnerEmails;
    @Persistent private Set<User> theOwnerUsers;
    
    // Standard properties.
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
    @Persistent private String exponentURL;
    @Persistent private String exponentVizURL;
    
    // Optional image-related fields.
    @Persistent private String imageBlobKey;
    @Persistent private String imageLargeURL;
    @Persistent private String imageThumbURL;

    public Player(String theName, String theURL, User anOwner) {
        this.theName = theName;
        this.setURL(theURL);

        // Store the ownership information.
        this.theOwners = new HashSet<String>();
        this.theOwnerEmails = new HashSet<String>();
        this.theOwnerUsers = new HashSet<User>();
        this.theOwners.add(anOwner.getUserId());
        this.theOwnerEmails.add(anOwner.getEmail());
        this.theOwnerUsers.add(anOwner);
        
        this.setEnabled(false);
        this.setPingable(true);
        this.setGdlVersion("GDLv1");
        
        this.setVisibleEmail("");
        this.setVisibleWebsite("");
        this.setImageBlobKey("");
        this.setExponentURL("");
        this.setExponentVizURL("");
        
        this.nStrikes = 0;
        
        save();
    }
    
    public String getName() {
        return theName;
    }
    
    public void setVisibleEmail(String visibleEmail) {
        this.visibleEmail = visibleEmail;
    }

    public void setVisibleWebsite(String visibleWebsite) {
        this.visibleWebsite = visibleWebsite;
    }
    
    public void setImageBlobKey(String imageBlobKey) {
    	this.imageBlobKey = imageBlobKey;
    	if (imageBlobKey != null && !imageBlobKey.isEmpty()) {
    		this.imageLargeURL = ImagesServiceFactory.getImagesService().getServingUrl(ServingUrlOptions.Builder.withBlobKey(new BlobKey(imageBlobKey)).imageSize(150));
    		this.imageThumbURL = ImagesServiceFactory.getImagesService().getServingUrl(ServingUrlOptions.Builder.withBlobKey(new BlobKey(imageBlobKey)).imageSize(25));
    	}
    }
    
    public String getImageBlobKey() {
    	return imageBlobKey;
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
        if (isPingable == null) return true;
        return isPingable;
    }
    
    public void setGdlVersion(String gdlVersion) {
        this.gdlVersion = gdlVersion;
    }

    public String getGdlVersion() {
        return gdlVersion;
    }
    
    public void setExponentURL(String exponentURL) {
    	this.exponentURL = exponentURL;
    }

    public void setExponentVizURL(String exponentVizURL) {
    	this.exponentVizURL = exponentVizURL;
    }    
    
    public void addOwner(User anOwner) {
    	if (theOwnerEmails == null) {
    		theOwnerEmails = new HashSet<String>();
    	}
    	if (theOwnerUsers == null) {
    		theOwnerUsers = new HashSet<User>();
    	}
    	theOwners.add(anOwner.getUserId());
        theOwnerEmails.add(anOwner.getEmail());
        theOwnerUsers.add(anOwner);
    }
    
    public boolean isOwner(User user) {
        return user != null && theOwners.contains(user.getUserId());
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
    	if (theNewStatus.length() > 100) {
    		// Ping status should be either "busy" or "available", so we should feel free
    		// to trim any status that's longer than 100 characters.
    		theNewStatus = theNewStatus.substring(0, 100);
    	}
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
            theJSON.put("exponentURL", exponentURL);
            theJSON.put("exponentVizURL", exponentVizURL);
            theJSON.put("pingStatus", pingStatus);
            if (imageBlobKey != null && !imageBlobKey.isEmpty()) {
            	theJSON.put("imageURL", imageLargeURL);
            	theJSON.put("thumbURL", imageThumbURL);
            }
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