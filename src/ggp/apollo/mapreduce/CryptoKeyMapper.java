package ggp.apollo.mapreduce;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Text;
import com.google.appengine.repackaged.org.json.JSONObject;
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
        
        if (theMatch.has("apolloSigned") && theMatch.getBoolean("apolloSigned")) {            
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