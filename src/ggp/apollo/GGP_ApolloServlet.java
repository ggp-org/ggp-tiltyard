package ggp.apollo;

import ggp.apollo.players.Registration;
import ggp.apollo.scheduling.Scheduling;
import ggp.apollo.scheduling.backends.BackendRegistration;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.http.*;

import com.google.appengine.api.capabilities.CapabilitiesService;
import com.google.appengine.api.capabilities.CapabilitiesServiceFactory;
import com.google.appengine.api.capabilities.Capability;
import com.google.appengine.api.capabilities.CapabilityStatus;

@SuppressWarnings("serial")
public class GGP_ApolloServlet extends HttpServlet {
    public static boolean isDatastoreWriteable() {
        CapabilitiesService service = CapabilitiesServiceFactory.getCapabilitiesService();
        CapabilityStatus status = service.getStatus(Capability.DATASTORE_WRITE).getStatus();
        return (status != CapabilityStatus.DISABLED);
    }

    public static void setAccessControlHeader(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
        resp.setHeader("Access-Control-Allow-Age", "86400");
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        setAccessControlHeader(resp);

        if (req.getRequestURI().equals("/cron/scheduling_round")) {
            if (isDatastoreWriteable()) {
                Scheduling.runSchedulingRound();            
                resp.setContentType("text/plain");
                resp.getWriter().println("Starting scheduling round.");
            }
            resp.setStatus(200);
            return;
        }

        if (req.getRequestURI().startsWith("/data/")) {
            Registration.doGet(req.getRequestURI().replaceFirst("/data/", ""), resp);
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
            reqURI = "/players/playerPage.html";
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

    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!isDatastoreWriteable()) return;
        setAccessControlHeader(resp);
        resp.setHeader("Access-Control-Allow-Origin", "tiltyard.ggp.org");

        String theURI = req.getRequestURI();
        BufferedReader br = new BufferedReader(new InputStreamReader(req.getInputStream()));
        int contentLength = Integer.parseInt(req.getHeader("Content-Length").trim());
        StringBuilder theInput = new StringBuilder();
        for (int i = 0; i < contentLength; i++) {
            theInput.append((char)br.read());
        }
        String in = theInput.toString().trim();        

        if (theURI.startsWith("/backends/")) {
            BackendRegistration.doPost(theURI, in, req.getRemoteAddr(), resp);
        } else {
            Registration.doPost(theURI, in, resp);
        }
    }

    public void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {  
        setAccessControlHeader(resp);
    }

    /* --- */

    public static void writeStaticTextPage(HttpServletResponse resp, String theURI) throws IOException {
        FileReader fr = new FileReader(theURI);
        BufferedReader br = new BufferedReader(fr);
        StringBuffer response = new StringBuffer();

        String line;
        while( (line = br.readLine()) != null ) {
            response.append(line + "\n");
        }

        resp.getWriter().println(response.toString());
    }

    public static void writeStaticBinaryPage(HttpServletResponse resp, String theURI) throws IOException {
        InputStream in = new FileInputStream(theURI);
        byte[] buf = new byte[1024];
        while (in.read(buf) > 0) {
            resp.getOutputStream().write(buf);
        }
        in.close();        
    }    
}