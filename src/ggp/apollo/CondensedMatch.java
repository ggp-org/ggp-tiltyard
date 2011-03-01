package ggp.apollo;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import com.google.appengine.api.datastore.Text;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

@PersistenceCapable
public class CondensedMatch {
    @PrimaryKey @Persistent private String theSpectatorURL;
    @Persistent private Text theCondensedMatchJSON;
    @Persistent private List<String> thePlayers;

    public CondensedMatch(String theSpectatorURL, List<String> thePlayers) {
        this.theSpectatorURL = theSpectatorURL;
        this.thePlayers = thePlayers;
        this.theCondensedMatchJSON = new Text("");
        
        save();
    }
    
    public boolean isReady() {
        return (this.theCondensedMatchJSON.getValue().length() > 0);
    }

    public void condenseFullJSON(JSONObject theJSON) throws JSONException {
        try {
            theJSON.put("moveCount", theJSON.getJSONArray("moves").length());
        } catch (Exception e) {
            theJSON.put("moveCount", 0);
        }
        theJSON.remove("states");      // Strip out all of the large fields
        theJSON.remove("moves");       // that we won't need most of the time.        
        theJSON.remove("stateTimes");  // This is why we can store it here.        
        this.theCondensedMatchJSON = new Text(theJSON.toString());
    }
    
    public JSONObject getCondensedJSON() {
        try {
            return new JSONObject(theCondensedMatchJSON.getValue());
        } catch (JSONException e) {
            return null;
        }
    }

    public List<String> getPlayers() {
        return thePlayers;
    }

    public String getSpectatorURL() {
        return theSpectatorURL;
    }
    
    public void save() {
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();        
    }    

    /* Static accessor methods */
    public static Set<CondensedMatch> loadCondensedMatches() throws IOException {
        return Persistence.loadAll(CondensedMatch.class);
    }
    
    public static CondensedMatch loadCondensedMatch(String theSpectatorURL) throws IOException {
        return Persistence.loadSpecific(theSpectatorURL, CondensedMatch.class);
    }
}