package ggp.tiltyard.hosting;

import java.io.IOException;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import com.google.appengine.api.datastore.Text;

import org.ggp.shared.crypto.BaseCryptography.EncodedKeyPair;
import org.ggp.shared.persistence.Persistence;
import org.json.JSONException;

@PersistenceCapable
public class StoredCryptoKeys {
    @SuppressWarnings("unused")
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent private Text theCryptoKeys;

    private StoredCryptoKeys(String theKeyName, String theKeyPair) {
    	thePrimaryKey = theKeyName;
        theCryptoKeys = new Text(theKeyPair);
    }

    private void save() {
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();        
    }
    
    /* Static accessor methods */
    public static EncodedKeyPair loadCryptoKeys(String keyName) throws IOException {
    	StoredCryptoKeys theKeys = Persistence.loadSpecific(keyName, StoredCryptoKeys.class);
    	if (theKeys == null) {
    		return null;
    	}
    	try {
    		return new EncodedKeyPair(theKeys.theCryptoKeys.getValue());
    	} catch (JSONException e) {
    		return null;
    	}
    }
    
    public static void storeCryptoKeys(String theKeyName, String theKeyPair) {
    	StoredCryptoKeys theKeys = new StoredCryptoKeys(theKeyName, theKeyPair);
    	theKeys.save();
    }
}