package ggp.apollo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import com.google.appengine.api.datastore.Text;
import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

@PersistenceCapable
public class Game {
    @PrimaryKey @Persistent private String gameMetaURL;
    @Persistent private Text jsonMetadata;

    @Persistent private List<String> recentMatchURLs;
    private static final int kRecentMatchURLsToRecord = 40;

    public Game(String gameMetaURL) {
        this.gameMetaURL = gameMetaURL;
        this.recentMatchURLs = new ArrayList<String>();
        
        try {
            JSONObject theMetadata = RemoteResourceLoader.loadJSON(gameMetaURL);
            jsonMetadata = new Text(theMetadata.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        save();
    }

    public String getMetaURL() {
        return gameMetaURL;
    }
    
    public JSONObject getMetadata() {
        try {
            return new JSONObject(jsonMetadata.getValue());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getRecentMatchURLs() {
        return recentMatchURLs;
    }    

    public void addRecentMatchURL(String theURL) {
        recentMatchURLs.add(theURL);
        if (recentMatchURLs.size() > kRecentMatchURLsToRecord) {
            recentMatchURLs.remove(0);
        }
    }

    public JSONObject asJSON(boolean includeMatches) throws IOException {
        try {
            JSONObject theJSON = new JSONObject();
            theJSON.put("gameMetaURL", gameMetaURL);
            theJSON.put("metadata", new JSONObject(jsonMetadata.getValue()));
            if (includeMatches) {
                JSONArray theMatches = new JSONArray();
                for (String recentMatchURL : recentMatchURLs) {
                    CondensedMatch c = CondensedMatch.loadCondensedMatch(recentMatchURL);
                    if (!c.isReady()) continue;
                    theMatches.put(c.getCondensedJSON());
                }
                theJSON.put("recentMatches", theMatches);
            }
            return theJSON;
        } catch (JSONException e) {
            return null;
        }
    }

    // refreshFromRepository shouldn't be necessary, but can be used to update
    // the game metadata cached from the repository server in the event that the
    // metadata for a particular game-version changes.
    public void refreshFromRepository() {
        try {
            JSONObject theMetadata = RemoteResourceLoader.loadJSON(gameMetaURL);
            jsonMetadata = new Text(theMetadata.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        save();
    }

    public void save() {
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();        
    }    

    /* Static accessor methods */
    public static Set<Game> loadGames() throws IOException {
        return Persistence.loadAll(Game.class);
    }

    public static Game loadGame(String theMetaURL) throws IOException {
        return Persistence.loadSpecific(theMetaURL, Game.class);
    }
}