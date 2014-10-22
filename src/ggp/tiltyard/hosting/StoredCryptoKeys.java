package ggp.tiltyard.hosting;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import com.google.appengine.api.datastore.Text;

import external.JSON.JSONException;

import org.ggp.base.util.crypto.BaseCryptography.EncodedKeyPair;
import org.ggp.galaxy.shared.persistence.Persistence;

@PersistenceCapable
public class StoredCryptoKeys {
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
    public static EncodedKeyPair loadCryptoKeys(String keyName) {
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