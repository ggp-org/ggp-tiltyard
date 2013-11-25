package ggp.tiltyard.backends;


import ggp.tiltyard.players.Player;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.ggp.galaxy.shared.crypto.SignableJSON;
import org.json.JSONException;
import org.json.JSONObject;

public class BackendRegistration {
    public static long getTimeBlock() {
        return System.currentTimeMillis() / 3600000;
    }

    public static boolean verifyBackendPing(String thePingResponse) {
        try {
            JSONObject thePingJSON = new JSONObject(thePingResponse);
            long currentTimeBlock = getTimeBlock();
            if (!SignableJSON.isSignedJSON(thePingJSON)) throw new Exception("Backend registration not signed.");
            if (!SignableJSON.verifySignedJSON(thePingJSON)) throw new Exception("Backend registration signature not valid.");
            if (!thePingJSON.getString("matchHostPK").equals(BackendPublicKey.theKey)) throw new Exception("Backend registration not signed with Tiltyard key.");
            if (thePingJSON.getLong("lastTimeBlock") != currentTimeBlock && thePingJSON.getLong("nextTimeBlock") != currentTimeBlock) throw new Exception("Backend registration time block not valid.");
            return true;
        } catch (Exception e) {
            return false;
        }        
    }

    public static void doPost(String theURI, String in, String remoteAddr, HttpServletResponse resp) throws IOException {
        if (theURI.equals("register/farm")) {
            if (!verifyBackendPing(in)) {
                resp.setStatus(404);
                return;
            }
            try {
	            JSONObject thePingJSON = new JSONObject(in);
	            Backends theBackends = Backends.loadBackends();
	            theBackends.getFarmBackendAddresses(Player.REGION_ANY).add(remoteAddr);
	            if (thePingJSON.has("zone") && thePingJSON.getString("zone").contains("us-central")) theBackends.getFarmBackendAddresses(Player.REGION_US).add(remoteAddr);
	            if (thePingJSON.has("zone") && thePingJSON.getString("zone").contains("europe-west")) theBackends.getFarmBackendAddresses(Player.REGION_EU).add(remoteAddr);
	            theBackends.save();
            } catch (JSONException e) {
                resp.setStatus(404);
                return;            	
            }
        } else {
            resp.setStatus(404);
        }
    }    
}