package ggp.tiltyard.players;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.blobstore.BlobKey;
import com.google.appengine.api.blobstore.BlobstoreService;
import com.google.appengine.api.blobstore.BlobstoreServiceFactory;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;

import org.json.JSONException;
import org.json.JSONObject;

public class Registration {
    public static void doGet(String theRPC, HttpServletResponse resp) throws IOException {
        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();

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
                    theResponse.put("nickname", user.getNickname());
                    theResponse.put("authDomain", user.getAuthDomain());
                    theResponse.put("federatedIdentity", user.getFederatedIdentity());
                    theResponse.put("emailAddress", user.getEmail());
                    theResponse.put("userId", user.getUserId());
                    theResponse.put("logoutURL", userService.createLogoutURL("http://tiltyard.ggp.org/REPLACEME"));
                    theResponse.put("loggedIn", true);
                    theResponse.put("isAdmin", userService.isUserAdmin());
                } else {
                    Map<String, String> openIdProviders = new HashMap<String, String>();
                    openIdProviders = new HashMap<String, String>();
                    openIdProviders.put("google", "https://www.google.com/accounts/o8/id");
                    openIdProviders.put("yahoo", "yahoo.com");
                    openIdProviders.put("myspace", "myspace.com");
                    openIdProviders.put("aol", "aol.com");
                    openIdProviders.put("myopenid", "myopenid.com");

                    JSONObject theProviders = new JSONObject();
                    for (String providerName : openIdProviders.keySet()) {
                        String providerUrl = openIdProviders.get(providerName);
                        theProviders.put(providerName, userService.createLoginURL("http://tiltyard.ggp.org/REPLACEME", null, providerUrl, new HashSet<String>()));
                    }
                    theResponse.put("providers", theProviders);
                    theResponse.put("preferredOrder", new String[] {"google", "yahoo", "aol", "myspace", "myopenid"} );
                    theResponse.put("loggedIn", false);
                }
                resp.getWriter().println(theResponse.toString());
            } else {
                resp.setStatus(404);
            }
        } catch(JSONException e) {
            throw new IOException(e);
        }        
    }
    
    public static void doPost(String theURI, String in, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();

        try {
            if (theURI.equals("/data/updatePlayer")) {
                JSONObject playerInfo = new JSONObject(in);
                String theName = sanitizeHarder(playerInfo.getString("name"));
                if (!theName.equals(playerInfo.getString("name"))) {
                    resp.setStatus(404);
                    return;
                }
                if (theName.toLowerCase().equals("random") && !theName.equals("Random")) {
                	resp.setStatus(404);
                	return;
                }
                
                Player p = Player.loadPlayer(theName);
                if (p == null) {
                    p = new Player(theName, sanitize(playerInfo.getString("theURL")), user);
                } else if (!p.isOwner(user)) {
                    resp.setStatus(404);
                    return;
                }
                
                // TODO: Remove this once all of the players have up-to-date ownership info.
                p.addOwner(user);

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
                p.save();

                resp.getWriter().println(p.asJSON(true));
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
                p.addOwner(user);
                
            	p.setImageBlobKey(theBlob.getKeyString());
            	p.save();
            	resp.sendRedirect("/players/" + playerName);
            } else {
                resp.setStatus(404);
            }
        } catch(JSONException e) {
            throw new IOException(e);
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