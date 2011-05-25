package ggp.apollo.mapreduce;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.repackaged.org.json.*;
import com.google.appengine.tools.mapreduce.AppEngineMapper;

import org.apache.hadoop.io.NullWritable;

import java.util.logging.Logger;

public class CryptoKeyMapper extends AppEngineMapper<Key, Entity, NullWritable, NullWritable> {
  private static final Logger log = Logger.getLogger(CryptoKeyMapper.class.getName());

  public CryptoKeyMapper() {
      ;
  }

  @Override
  public void taskSetup(Context context) {
    log.warning("Doing per-task setup");
  }

  @Override
  public void taskCleanup(Context context) {
    log.warning("Doing per-task cleanup");
  }

  @Override
  public void setup(Context context) {
    log.warning("Doing per-worker setup");
  }

  @Override
  public void cleanup(Context context) {
    log.warning("Doing per-worker cleanup");    
  }

  // Quick observation map over the datastore, to collect numbers
  // on how often various match descriptor fields are being used.
  @Override
  public void map(Key key, Entity value, Context context) {
    log.warning("Mapping key: " + key);
    
    try {
        String theJSON = ((Text)value.getProperty("theCondensedMatchJSON")).getValue();
        JSONObject theMatch = new JSONObject(theJSON);

        boolean isAllRobots = true;
        JSONArray thePlayers = theMatch.getJSONArray("apolloPlayers");
        for (int i = 0; i < thePlayers.length(); i++) {
            if (!thePlayers.getString(i).startsWith("Webplayer-")) {
                isAllRobots = false;
            }
        }
        if (isAllRobots) {
            if (theMatch.getLong("startTime") < System.currentTimeMillis() - 1209600000L) {
                DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
                datastore.delete(key);
                
                context.getCounter("MatchType", "oldRobotMatch").increment(1);
            } else {
                context.getCounter("MatchType", "robotMatch").increment(1);
            }
        } else {
            context.getCounter("MatchType", "realMatch").increment(1);
        }
        
        if (theMatch.has("errors")) {
            context.getCounter("RecordedErrors", "hasRecord").increment(1);
        } else {
            context.getCounter("RecordedErrors", "lacksRecord").increment(1);
        }
        
        if (theMatch.has("matchHostPK") || theMatch.has("matchHostSignature")) {
            context.getCounter("Cryptography", "Signed Long").increment(1);
        } else if (theMatch.has("apolloSigned") && theMatch.getBoolean("apolloSigned")) {            
            context.getCounter("Cryptography", "Signed").increment(1);
        } else {
            context.getCounter("Cryptography", "Unsigned").increment(1);
        }
        
        context.getCounter("Overall", "Readable").increment(1);
    } catch (Exception e) {
        context.getCounter("Overall", "Unreadable").increment(1);
    }
  }
}