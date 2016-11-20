package ggp.tiltyard.scheduling;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

//import com.google.identitytoolkit.GitkitUser;

import external.JSON.JSONException;
import external.JSON.JSONObject;
//import ggp.tiltyard.identity.GitkitIdentity;

public class Tournaments {
    public static void doPost(String theURI, String in, HttpServletRequest req, HttpServletResponse resp) throws IOException {
    	//GitkitUser user = GitkitIdentity.getUser(req);

        if (theURI.equals("submitTournamentAction")) {
        	String theName = null;
        	try {
                JSONObject actionInfo = new JSONObject(in);
                theName = sanitizeHarder(actionInfo.getString("theTournament"));
                if (!theName.equals(actionInfo.getString("theTournament")) || theName.length() >= 50 || theName.length() < 2) {
                    resp.setStatus(404);
                    return;
                }
                
                TournamentData t = TournamentData.loadTournamentData(theName);
                if (t == null) {
                    resp.setStatus(404);
                    return;
                }
                /*
                 * TODO: Add auth check.
                 *
                if (!p.isOwner(user)) {
                    resp.setStatus(404);
                    return;
                }
                 */
                
                if (t.recordAdminAction(actionInfo.getString("theAction"))) {
                	resp.getWriter().println(t.getDisplayData());
                } else {
                	resp.getWriter().println("parse error");
                }
            } catch(JSONException e) {
            	Logger.getAnonymousLogger().severe(in);
                throw new RuntimeException(theName + ": " + e);
            }
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