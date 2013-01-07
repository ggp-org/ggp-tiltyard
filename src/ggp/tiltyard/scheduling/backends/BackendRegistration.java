package ggp.tiltyard.scheduling.backends;

import ggp.tiltyard.TiltyardPublicKey;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.ggp.shared.crypto.SignableJSON;
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
            if (!thePingJSON.getString("matchHostPK").equals(TiltyardPublicKey.theKey)) throw new Exception("Backend registration not signed with Tiltyard key.");
            if (thePingJSON.getLong("lastTimeBlock") != currentTimeBlock && thePingJSON.getLong("nextTimeBlock") != currentTimeBlock) throw new Exception("Backend registration time block not valid.");
            return true;
        } catch (Exception e) {
            return false;
        }        
    }

    public static void doPost(String theURI, String in, String remoteAddr, HttpServletResponse resp) throws IOException {
        if (theURI.equals("/backends/register")) {
            if (!verifyBackendPing(in)) {
                resp.setStatus(404);
                return;
            }
            Backends theBackends = Backends.loadBackends();
            theBackends.getBackendAddresses().add(remoteAddr);
            theBackends.save();
        } else {
            resp.setStatus(404);
        }
    }    
}