package ggp.tiltyard.players;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.*;

import org.ggp.base.server.request.RequestBuilder;
import org.ggp.base.util.loader.RemoteResourceLoader;
import org.ggp.base.util.presence.InfoResponse;
import org.ggp.galaxy.shared.persistence.Persistence;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.api.images.ServingUrlOptions;
import com.google.appengine.api.users.User;
import com.google.identitytoolkit.GitkitUser;

import external.JSON.JSONException;
import external.JSON.JSONObject;

@PersistenceCapable
public class Player {
    @PrimaryKey @Persistent private String theName;

    private static final int STRIKES_BEFORE_DISABLING = 2;
    private static final int PING_STRIKES_BEFORE_DISABLING = 60;
    
    // Ownership information.
    @Persistent private Set<String> theOwners; // legacy IDs
    @Persistent private Set<String> theOwnerLocalIds;
    @Persistent private Set<String> theOwnerEmails;
    @Persistent private Set<User> theOwnerUsers; // legacy data structures
    
    // Standard properties.
    @Persistent private boolean isEnabled;
    @Persistent private String gdlVersion;    
    @Persistent private String theURL;
    @Persistent private Integer nStrikes;
    @Persistent private Integer nPingStrikes;
    @Persistent private String infoStatus;
    @Persistent private String infoError;
    @Persistent private Text infoFull;

    // Optional fields.
    @Persistent private String visibleEmail;
    @Persistent private String visibleWebsite;
    @Persistent private Boolean isPingable;
    @Persistent private String exponentURL;
    @Persistent private String exponentVizURL;
    @Persistent private String theCountryCode;
    
    // Optional image-related fields.
    @Persistent private String imageBlobKey;
    @Persistent private String imageLargeURL;
    @Persistent private String imageThumbURL;

    public Player(String theName, String theURL, String emailAddress, String localID) {
        this.theName = theName;
        this.setURL(theURL);

        // Store the ownership information.
        this.theOwners = new HashSet<String>();
        this.theOwnerEmails = new HashSet<String>();
        this.theOwnerUsers = new HashSet<User>();
        this.theOwnerLocalIds = new HashSet<String>();
        this.theOwnerLocalIds.add(localID);
        if (emailAddress != null) {
        	this.theOwnerEmails.add(emailAddress);
        }        
        
        this.setEnabled(false);
        this.setPingable(true);
        this.setGdlVersion("GDLv1");
        
        this.setVisibleEmail("");
        this.setVisibleWebsite("");
        this.setImageBlobKey("");
        this.setExponentURL("");
        this.setExponentVizURL("");
        this.setCountryCode("");
        
        this.nStrikes = 0;
        this.nPingStrikes = 0;
        
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
        this.nPingStrikes = 0;
        if (isEnabled == false) {
            this.infoStatus = null;
            this.infoError = null;
            this.infoFull = null;
        } else if (isEnabled == true) {
            this.infoStatus = "waiting";
            this.infoError = null;
            this.infoFull = null;
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
    
    public static final String REGION_US = "US";
    public static final String REGION_EU = "EU";
    public static final String REGION_ANY = "ANY";
    public String getRegion() {
    	if (theCountryCode == null || theCountryCode.isEmpty()) {
    		return REGION_US;
    	} else {
    		String continent = Geography.countryCodesToContinents.get(theCountryCode);
    		if (continent.equals("AF")) return REGION_EU;
    		if (continent.equals("NA")) return REGION_US;
    		if (continent.equals("OC")) return REGION_US;
    		if (continent.equals("AN")) return REGION_US;
    		if (continent.equals("AS")) return REGION_US;
    		if (continent.equals("EU")) return REGION_EU;
    		if (continent.equals("SA")) return REGION_US;
    		return REGION_US;
    	}
    }
    
    public String getCountryCode() {
    	return theCountryCode;
    }
    
    public void setCountryCode(String newCountryCode) {
    	theCountryCode = newCountryCode;
    }

    public void addLocalId(String localId) {
    	if (theOwnerLocalIds == null) {
    		theOwnerLocalIds = new HashSet<String>();
    	}
    	theOwnerLocalIds.add(localId);
    }
    
    public boolean isOwner(User user) {
        return user != null && theOwners.contains(user.getUserId());
    }
    
    public boolean isOwner(GitkitUser user) {
    	return user != null && (theOwnerEmails.contains(user.getEmail()) || theOwnerLocalIds.contains(user.getLocalId()));
    }
    
    public void addStrike() {
        if (nStrikes == null) {
            nStrikes = 0;
        }
        if (nStrikes >= STRIKES_BEFORE_DISABLING) {
            setEnabled(false);
        } else {
            nStrikes++;
        }
    }
    
    public void addPingStrike() {
    	if (nPingStrikes == null) {
    		nPingStrikes = 0;
    	}
    	if (nPingStrikes >= PING_STRIKES_BEFORE_DISABLING) {
    		setEnabled(false);
    	} else {
    		nPingStrikes++;
    	}
    }
    
    public void resetStrikes() {
        if (nStrikes == null) {
            nStrikes = 0;
        }
        nStrikes = 0;
        resetPingStrikes();
    }
    
    public void resetPingStrikes() {
        if (nPingStrikes == null) {
        	nPingStrikes = 0;
        }
        nPingStrikes = 0;    	
    }
    
    public void setInfo(String theInfoResponse, String theError) {
    	InfoResponse info = InfoResponse.create(theInfoResponse);
    	
    	String status = "error";
    	if (info.getStatus() != null) {
    		status = info.getStatus();
    	}
    	if (status.length() > 100) {
    		// Ping status should be either "busy" or "available", so we should feel free
    		// to trim any status that's longer than 20 characters.
    		status = status.substring(0, 20);
    	}
    	
        infoStatus = status;        
        infoError = theError;
        infoFull = new Text(theInfoResponse);
    }
    
    public String getInfoStatus() {
        return infoStatus;
    }
    
    public String getInfoError() {
        return infoError;
    }
    
    public void doPing() {
        String theInfoError = "";
        String theInfoResponse = "";                    
        try {
            String theProperURL = getURL();
            if (!theProperURL.startsWith("http://")) {
                theProperURL = "http://" + theProperURL;
            }
            theInfoResponse = RemoteResourceLoader.postRawWithTimeout(theProperURL, RequestBuilder.getInfoRequest(), 2500);
            resetPingStrikes();
        } catch (IOException e) {
        	theInfoError = e.toString();
        	addPingStrike();
        }
        setInfo(theInfoResponse, theInfoError);
        save();
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
            theJSON.put("infoStatus", infoStatus);
            if (imageBlobKey != null && !imageBlobKey.isEmpty()) {
            	theJSON.put("imageURL", imageLargeURL);
            	theJSON.put("thumbURL", imageThumbURL);
            }
            if (includePrivate) {
                // Not sure if we want to expose the userID information,
                // even to the owners themselves.
                //theJSON.put("theOwners", theOwners);                
                theJSON.put("infoError", infoError);
                theJSON.put("infoFull", infoFull == null ? null : infoFull.getValue());
                theJSON.put("countryCode", theCountryCode);
                theJSON.put("theURL", theURL);
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
    public static List<Player> loadEnabledPlayers() {
        PersistenceManager pm = Persistence.getPersistenceManager();
        Query q = pm.newQuery(Player.class);
        q.setFilter("isEnabled == true");
        List<Player> toReturn = new ArrayList<Player> ((List<Player>) q.execute());
        q.closeAll();
        pm.close();
        return toReturn;
    }
    
    public static Set<Player> loadPlayers() {
        return Persistence.loadAll(Player.class);
    }
    
    public static Player loadPlayer(String theKey) {
        return Persistence.loadSpecific(theKey, Player.class);
    }
}