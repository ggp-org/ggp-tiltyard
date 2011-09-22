package ggp.apollo;

import ggp.apollo.scheduling.Scheduling;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.servlet.http.*;

import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityStatus;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

@SuppressWarnings("serial")
public class GGP_ApolloServlet extends HttpServlet {    
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (req.getRequestURI().equals("/cron/scheduling_round")) {
            if (isDatastoreWriteable()) {
                Scheduling.runSchedulingRound();            
                resp.setContentType("text/plain");
                resp.getWriter().println("Starting scheduling round.");
            }
            return;
        }

        resp.setHeader("Access-Control-Allow-Origin", "apollo.ggp.org");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setHeader("Access-Control-Allow-Age", "86400");       
        
        if (req.getRequestURI().startsWith("/data/")) {
            respondToRPC(resp, req.getRequestURI().replaceFirst("/data/", ""));
            return;
        }
        
        String reqURI = req.getRequestURI();
        if (reqURI.equals("/games")) reqURI += "/";
        if (reqURI.equals("/stats")) reqURI += "/";
        if (reqURI.equals("/about")) reqURI += "/";
        if (reqURI.equals("/players")) reqURI += "/";
        if (reqURI.equals("/matches")) reqURI += "/";
        if (reqURI.endsWith("/")) {
            reqURI += "index.html";
        }
        
        if (reqURI.startsWith("/players/") && !reqURI.equals("/players/index.html")) {
            String playerName = reqURI.replaceFirst("/players/", "");
            if(Player.loadPlayer(playerName) == null) {
                resp.setStatus(404);
                return;
            }
            reqURI = "/players/playerPage.html";
        }
        if (reqURI.startsWith("/playersDresden/")) {
            reqURI = "/players/playerPageDresden.html";
        }                
        if (reqURI.startsWith("/games/") && !reqURI.equals("/games/index.html")) {
            reqURI = "/games/gamePage.html";
        }
        if (reqURI.startsWith("/matches/") && !reqURI.equals("/matches/index.html")) {            
            reqURI = "/matches/matchPage.html";
        }

        boolean writeAsBinary = false;        
        if (reqURI.endsWith(".html")) {
            resp.setContentType("text/html");
        } else if (reqURI.endsWith(".xml")) {
            resp.setContentType("application/xml");
        } else if (reqURI.endsWith(".xsl")) {
            resp.setContentType("application/xml");
        } else if (reqURI.endsWith(".js")) {
            resp.setContentType("text/javascript");   
        } else if (reqURI.endsWith(".json")) {
            resp.setContentType("text/javascript");
        } else if (reqURI.endsWith(".png")) {
            resp.setContentType("image/png");
            writeAsBinary = true;
        } else if (reqURI.endsWith(".ico")) {
            resp.setContentType("image/png");
            writeAsBinary = true;
        } else {
            resp.setContentType("text/plain");
        }

        try {
            if (writeAsBinary) {
                writeStaticBinaryPage(resp, reqURI.substring(1));
            } else {
                // Temporary limits on caching, for during development.
                resp.setHeader("Cache-Control", "no-cache");
                resp.setHeader("Pragma", "no-cache");
                writeStaticTextPage(resp, reqURI.substring(1));
            }
        } catch(IOException e) {
            resp.setStatus(404);
        }
    }
    
    public boolean isDatastoreWriteable() {
        CapabilitiesService service = CapabilitiesServiceFactory.getCapabilitiesService();
        CapabilityStatus status = service.getStatus(Capability.DATASTORE_WRITE).getStatus();
        return (status != CapabilityStatus.DISABLED);
    }

    public void writeStaticTextPage(HttpServletResponse resp, String theURI) throws IOException {
        FileReader fr = new FileReader(theURI);
        BufferedReader br = new BufferedReader(fr);
        StringBuffer response = new StringBuffer();
        
        String line;
        while( (line = br.readLine()) != null ) {
            response.append(line + "\n");
        }

        resp.getWriter().println(response.toString());
    }
    
    public void writeStaticBinaryPage(HttpServletResponse resp, String theURI) throws IOException {
        InputStream in = new FileInputStream(theURI);
        byte[] buf = new byte[1024];
        while (in.read(buf) > 0) {
            resp.getOutputStream().write(buf);
        }
        in.close();        
    }
    
    public void respondToRPC(HttpServletResponse resp, String theRPC) throws IOException {
        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();
        String userId = (user != null) ? user.getUserId() : "";

        try {
            if (theRPC.equals("players/")) {
                JSONObject theResponse = new JSONObject();
                for (Player p : Player.loadPlayers()) {
                    theResponse.put(p.getName(), p.asJSON(p.isOwner(userId)));
                }
                resp.getWriter().println(theResponse.toString());
            } else if (theRPC.startsWith("players/")) {
                String thePlayer = theRPC.replaceFirst("players/", "");
                Player p = Player.loadPlayer(thePlayer);
                if (p == null) {
                    resp.setStatus(404);
                    return;
                }
                resp.getWriter().println(p.asJSON(p.isOwner(userId)));
            } else if (theRPC.equals("matches/")) {
                resp.setStatus(404);
            } else if (theRPC.equals("serverState")) {
                ServerState serverState = ServerState.loadState();
                JSONObject theResponse = new JSONObject();
                theResponse.put("schedulingRound", serverState.getSchedulingRound());
                theResponse.put("backendErrors", serverState.getBackendErrors());
                resp.getWriter().println(theResponse.toString());
            } else if (theRPC.equals("login")) {
                JSONObject theResponse = new JSONObject();
                if (user != null) {                    
                    theResponse.put("nickname", user.getNickname());
                    theResponse.put("authDomain", user.getAuthDomain());
                    theResponse.put("federatedIdentity", user.getFederatedIdentity());
                    theResponse.put("emailAddress", user.getEmail());
                    theResponse.put("userId", user.getUserId());
                    theResponse.put("logoutURL", userService.createLogoutURL("http://apollo.ggp.org/REPLACEME"));
                    theResponse.put("loggedIn", true);
                    theResponse.put("isAdmin", userService.isUserAdmin());
                } else {
                    Map<String, String> openIdProviders = new HashMap<String, String>();
                    openIdProviders = new HashMap<String, String>();
                    openIdProviders.put("google", "google.com/accounts/o8/id");
                    openIdProviders.put("yahoo", "yahoo.com");
                    openIdProviders.put("myspace", "myspace.com");
                    openIdProviders.put("aol", "aol.com");
                    openIdProviders.put("myopenid", "myopenid.com");

                    JSONObject theProviders = new JSONObject();
                    for (String providerName : openIdProviders.keySet()) {
                        String providerUrl = openIdProviders.get(providerName);
                        theProviders.put(providerName, userService.createLoginURL("http://apollo.ggp.org/REPLACEME", null, providerUrl, new HashSet<String>()));
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
    
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!isDatastoreWriteable()) return;
        
        resp.setHeader("Access-Control-Allow-Origin", "apollo.ggp.org");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setHeader("Access-Control-Allow-Age", "86400");

        String theURI = req.getRequestURI();
        
        BufferedReader br = new BufferedReader(new InputStreamReader(req.getInputStream()));
        int contentLength = Integer.parseInt(req.getHeader("Content-Length").trim());
        StringBuilder theInput = new StringBuilder();
        for (int i = 0; i < contentLength; i++) {
            theInput.append((char)br.read());
        }
        String in = theInput.toString().trim();
        
        UserService userService = UserServiceFactory.getUserService();
        User user = userService.getCurrentUser();
        String userId = (user != null) ? user.getUserId() : "";

        try {
            if (theURI.equals("/data/updatePlayer") && userId.length() > 0) {
                JSONObject playerInfo = new JSONObject(in);
                String theName = sanitizeHarder(playerInfo.getString("name"));
                if (!theName.equals(playerInfo.getString("name"))) {
                    resp.setStatus(404);
                    return;                    
                }
                
                Player p = Player.loadPlayer(theName);
                if (p == null) {
                    p = new Player(theName, sanitize(playerInfo.getString("theURL")), userId);
                } else if (!p.isOwner(userId)) {
                    resp.setStatus(404);
                    return;
                }

                String gdlVersion = playerInfo.getString("gdlVersion");                
                if (!gdlVersion.equals("GDLv1") && !gdlVersion.equals("GDLv2")) {
                    gdlVersion = "GDLv1";
                }
                
                p.setEnabled(playerInfo.getBoolean("isEnabled"));
                p.setGdlVersion(gdlVersion);
                p.setURL(sanitize(playerInfo.getString("theURL")));
                p.setVisibleEmail(sanitize(playerInfo.getString("visibleEmail")));
                p.save();

                resp.getWriter().println(p.asJSON(true));
            } else {
                resp.setStatus(404);
            }
        } catch(JSONException e) {
            throw new IOException(e);
        }        
    }

    public void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {  
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "*");
        resp.setHeader("Access-Control-Allow-Age", "86400");    
    }

    public static String translateRepositoryCodename(String theURL) {
        return theURL.replaceFirst("base/", "http://games.ggp.org/games/");
    }
}