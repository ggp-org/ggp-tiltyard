package ggp.tiltyard.players;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.identitytoolkit.GitkitUser;

import external.JSON.JSONException;
import external.JSON.JSONObject;
import ggp.tiltyard.identity.GitkitIdentity;

public class Registration {
    public static void doGet(String theRPC, HttpServletRequest req, HttpServletResponse resp) throws IOException {
    	GitkitUser user = GitkitIdentity.getUser(req);

        try {
            if (theRPC.equals("players/")) {
                JSONObject theResponse = new JSONObject();
                for (Player p : Player.loadPlayers()) {
                    theResponse.put(p.getName(), p.asJSON(p.isOwner(user)));
                }
                resp.getWriter().println(theResponse.toString());
            } else if (theRPC.startsWith("players/")) {
                String thePlayer = theRPC.replaceFirst("players/", "");
                Player p = Player.loadPlayer(thePlayer);
                if (p == null) {
                    resp.setStatus(404);
                    return;
                }
            	JSONObject thePlayerJSON = p.asJSON(p.isOwner(user));
            	if (p.isOwner(user)) {
            		BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
            		thePlayerJSON.put("imageUploadURL", blobstoreService.createUploadUrl("/data/uploadPlayerImage/" + thePlayer));
            	}
            	resp.getWriter().println(thePlayerJSON);
            } else if (theRPC.equals("login")) {
                JSONObject theResponse = new JSONObject();
                if (user != null) {
                    theResponse.put("nickname", user.getName());
                    theResponse.put("currentProver", user.getCurrentProvider());
                    theResponse.put("emailAddress", user.getEmail());
                    theResponse.put("userId", user.getLocalId());
                    theResponse.put("loggedIn", true);
                    theResponse.put("isAdmin", "sam.schreiber@gmail.com".equals(user.getEmail()));
                    /* TODO: Sign auth tokens?
                    theResponse.put("signedBy", "tiltyard.ggp.org");
                    theResponse.put("signedOn", System.currentTimeMillis());
                    EncodedKeyPair authKey = StoredCryptoKeys.loadCryptoKeys("UserAuthKey");
                    SignableJSON.signJSON(theResponse, authKey.thePublicKey, authKey.thePrivateKey);
                    theResponse.put("signature", theResponse.getString("matchHostSignature"));
                    theResponse.remove("matchHostSignature");
                    theResponse.remove("matchHostPK");
                    */
                }
                resp.getWriter().println(theResponse.toString());
            } else {
                resp.setStatus(404);
            }
        } catch(JSONException e) {
            throw new IOException(e);
        }        
    }
    
    private static void addDefaultAsNeeded(JSONObject playerInfo, String fieldName, String defaultValue) throws JSONException {
    	if (!playerInfo.has(fieldName)) {
    		playerInfo.put(fieldName, defaultValue);
    	}
    }
    
    public static void doPost(String theURI, String in, HttpServletRequest req, HttpServletResponse resp) throws IOException {
    	GitkitUser user = GitkitIdentity.getUser(req);

        if (theURI.equals("/data/updatePlayer")) {
        	String theName = null;
        	try {
                JSONObject playerInfo = new JSONObject(in);
                theName = sanitizeHarder(playerInfo.getString("name"));
                if (!theName.equals(playerInfo.getString("name")) || theName.length() >= 50 || theName.length() < 2) {
                    resp.setStatus(404);
                    return;
                }
                if (theName.toLowerCase().equals("random") && !theName.equals("Random")) {
                	resp.setStatus(404);
                	return;
                }
                
                // Add default values in case certain optional fields have
                // been omitted from the JSON object.
                addDefaultAsNeeded(playerInfo, "visibleEmail", "");
                addDefaultAsNeeded(playerInfo, "visibleWebsite", "");
                addDefaultAsNeeded(playerInfo, "exponentURL", "");
                addDefaultAsNeeded(playerInfo, "exponentVizURL", "");
                addDefaultAsNeeded(playerInfo, "countryCode", "");
                
                Player p = Player.loadPlayer(theName);
                if (p == null) {
                    p = new Player(theName, sanitize(playerInfo.getString("theURL")), user.getEmail(), user.getLocalId());
                } else if (!p.isOwner(user)) {
                    resp.setStatus(404);
                    return;
                }

                // Clear the country code field unless the country code is part of the ISO 3166 list of country codes.
                if (playerInfo.has("countryCode") && !playerInfo.getString("countryCode").isEmpty() && !Geography.countryCodesToContinents.containsKey(playerInfo.getString("countryCode"))) {
                	Logger.getAnonymousLogger().severe("Could not find country code " + playerInfo.getString("countryCode") + "; ignoring...");
                	playerInfo.put("countryCode", "");
                }

                // TODO: Remove this once all of the players have up-to-date ownership info.
                p.addLocalId(user.getLocalId());

                String gdlVersion = playerInfo.getString("gdlVersion");                
                if (!gdlVersion.equals("GDLv1") && !gdlVersion.equals("GDLv2")) {
                    gdlVersion = "GDLv1";
                }
                p.setGdlVersion(gdlVersion);
                
                p.setEnabled(playerInfo.getBoolean("isEnabled"));
                p.setPingable(playerInfo.getBoolean("isPingable"));
                p.setURL(sanitize(playerInfo.getString("theURL")));
                p.setVisibleEmail(sanitize(playerInfo.getString("visibleEmail")));
                p.setVisibleWebsite(sanitize(playerInfo.getString("visibleWebsite")));
                p.setExponentURL(sanitize(playerInfo.getString("exponentURL")));
                p.setExponentVizURL(sanitize(playerInfo.getString("exponentVizURL")));
                p.setCountryCode(playerInfo.getString("countryCode"));
                
                p.doPing();
                
                p.save();

                resp.getWriter().println(p.asJSON(true));
            } catch(JSONException e) {
            	Logger.getAnonymousLogger().severe(in);
                throw new RuntimeException(theName + ": " + e);
            }
        } else if (theURI.startsWith("/data/uploadPlayerImage/")) {
        	String playerName = theURI.replaceFirst("/data/uploadPlayerImage/", "");
            Player p = Player.loadPlayer(playerName);
            
            if (p == null) {
                resp.setStatus(404);
                return;
            } else if (!p.isOwner(user)) {
                resp.setStatus(404);
                return;
            }
            
        	BlobstoreService blobstoreService = BlobstoreServiceFactory.getBlobstoreService();
        	Map<String, List<BlobKey>> blobs = blobstoreService.getUploads(req);
        	
        	BlobKey theBlob = null;
        	Set<BlobKey> blobsToDelete = new HashSet<BlobKey>();
        	for (List<BlobKey> list : blobs.values()) {
        		for (BlobKey b : list) {
        			if (p != null && (theBlob == null || theBlob.equals(b))) {
        				theBlob = b;
        			} else {
        				blobsToDelete.add(b);
        			}
        		}
        	}
        	if (p != null) {
        		String oldBlobKey = p.getImageBlobKey();
        		if (oldBlobKey != null && !oldBlobKey.isEmpty() && !oldBlobKey.equals(theBlob.getKeyString())) {
        			blobsToDelete.add(new BlobKey(p.getImageBlobKey()));
        		}
        	}
        	int i = 0;
        	BlobKey[] blobsToDeleteArr = new BlobKey[blobsToDelete.size()];
        	for (BlobKey blobToDelete : blobsToDelete) {
        		blobsToDeleteArr[i++] = blobToDelete;
        	}
			blobstoreService.delete(blobsToDeleteArr);
        	
            // TODO: Remove this once all of the players have up-to-date ownership info.
			p.addLocalId(user.getLocalId());
            
        	p.setImageBlobKey(theBlob.getKeyString());
        	p.save();
        	resp.sendRedirect("/players/" + playerName);
        } else {
            resp.setStatus(404);
        }
    }
    
    public static String sanitize(String x) {
        // TODO: Force the string to be ASCII?
        return x.replaceAll("<", "&lt;")
                .replaceAll(">", "&rt;")
                .replaceAll("\"", "&quot;")
                .trim();
    }
    
    public static String sanitizeHarder(String x) {
        StringBuilder theString = new StringBuilder();
        
        for (int i = 0; i < x.length(); i++) {
            char c = x.charAt(i);
            if (Character.isLetterOrDigit(c) ||
                c == '-' || c == '_') {
                theString.append(c);
            }
        }
        
        return sanitize(theString.toString());
    }    
}