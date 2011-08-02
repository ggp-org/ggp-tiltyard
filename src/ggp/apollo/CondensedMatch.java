package ggp.apollo;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

import com.google.appengine.api.datastore.Text;

@PersistenceCapable
public class CondensedMatch {
    @PrimaryKey @Persistent private String theSpectatorURL;
    @Persistent private Text theCondensedMatchJSON;
    @Persistent private List<String> thePlayers;
    @Persistent private Date createdOn;

    public CondensedMatch(String theSpectatorURL, List<String> thePlayers) {
        this.theSpectatorURL = theSpectatorURL;
        this.thePlayers = thePlayers;
        this.theCondensedMatchJSON = new Text("");
        this.createdOn = new Date();
        
        save();
    }

    public boolean isReady() {
        return (this.theCondensedMatchJSON.getValue().length() > 0);
    }

    public void condenseFullJSON(JSONObject theJSON) throws JSONException {
        theJSON.put("apolloSpectatorURL", theSpectatorURL);
        theJSON.put("apolloPlayers", thePlayers);
        try {
            theJSON.put("moveCount", theJSON.getJSONArray("moves").length());
        } catch (Exception e) {
            theJSON.put("moveCount", 0);
        }
        theJSON.remove("matchHostPK");
        theJSON.remove("matchHostSignature");
        theJSON.put("apolloSigned", true);        
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
    
    // Can sometimes be null.
    public Date getCreationDate() {        
        return createdOn;
    }
    
    public void save() {
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();        
    }    

    /* Static accessor methods */
    public static CondensedMatch loadCondensedMatch(String theSpectatorURL) throws IOException {
        return Persistence.loadSpecific(theSpectatorURL, CondensedMatch.class);
    }
}