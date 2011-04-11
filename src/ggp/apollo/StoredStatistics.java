package ggp.apollo;

import java.io.IOException;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import com.google.appengine.api.datastore.Text;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

@PersistenceCapable
public class StoredStatistics {
    @SuppressWarnings("unused")
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent private Text overall;
    @Persistent private Text perGame;
    @Persistent private Text perPlayer;    
    
    public StoredStatistics() {
        thePrimaryKey = "StoredStatistics";
        
        overall = new Text(new JSONObject().toString());
        perGame = new Text(new JSONObject().toString());
        perPlayer = new Text(new JSONObject().toString());
    }
    
    public JSONObject getOverallStats() {
        try {
            return new JSONObject(overall.getValue());
        } catch (JSONException e) {
            return null;
        }
    }
    
    public void setOverallStats(JSONObject overallStats) {
        overall = new Text(overallStats.toString());
    }
    
    public JSONObject getGameStats(String gameKey) {
        try {
            return new JSONObject(perGame.getValue()).getJSONObject(gameKey);
        } catch (JSONException e) {
            return null;
        }
    }
    
    public void setGameStats(String gameKey, JSONObject gameStats) {
        try {
            JSONObject perGameObj = new JSONObject(perGame.getValue());
            perGameObj.put(gameKey, gameStats);
            perGame = new Text(perGameObj.toString());
        } catch (JSONException e) {
            ;
        }
    }
    
    public JSONObject getPlayerStats(String playerKey) {
        try {
            return new JSONObject(perPlayer.getValue()).getJSONObject(playerKey);
        } catch (JSONException e) {
            return null;
        }
    }
    
    public void setPlayerStats(String playerKey, JSONObject playerStats) {
        try {
            JSONObject perPlayerObj = new JSONObject(perPlayer.getValue());
            perPlayerObj.put(playerKey, playerStats);
            perPlayer = new Text(perPlayerObj.toString());
        } catch (JSONException e) {
            ;
        }
    }

    public void save() {
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();        
    }
    
    /* Static accessor methods */
    public static StoredStatistics loadStatistics() throws IOException {
        Set<StoredStatistics> theStats = Persistence.loadAll(StoredStatistics.class);
        if (theStats.size() > 0) {
            return theStats.iterator().next();
        } else {
            return new StoredStatistics();
        }
   }
}