package ggp.apollo.scheduling.backends;

import ggp.apollo.Persistence;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import java.util.Properties;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

@PersistenceCapable
public class Backends {
    @SuppressWarnings("unused")
    @PrimaryKey @Persistent private String thePrimaryKey;
    @Persistent private Set<String> theBackendAddresses;
    @Persistent private int theBackendErrors;

    private Backends() {
        thePrimaryKey = "Backends";
        theBackendAddresses = new HashSet<String>();
        theBackendErrors = 0;
    }

    public Set<String> getBackendAddresses() {
        return theBackendAddresses;
    }
    
    public void addBackendError() {
        theBackendErrors++;
        if (theBackendErrors % 10 == 0) {
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);
            try {
                Message msg = new MimeMessage(session);
                msg.setFrom(new InternetAddress("sam.schreiber@gmail.com", "Sam Schreiber"));
                msg.addRecipient(Message.RecipientType.TO, new InternetAddress("sam.schreiber@gmail.com", "Sam Schreiber"));
                msg.setSubject("GGP.org Alert: Tiltyard Backends Missing.");
                msg.setText("All backend servers for Tiltyard have been missing for " + theBackendErrors + " cycles.");
                Transport.send(msg);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void save() {
        PersistenceManager pm = Persistence.getPersistenceManager();
        pm.makePersistent(this);
        pm.close();        
    }

    /* Static accessor methods */
    public static Backends loadBackends() throws IOException {
        Set<Backends> theStates = Persistence.loadAll(Backends.class);
        if (theStates.size() > 0) {
            return theStates.iterator().next();
        } else {
            return new Backends();
        }
   }
}