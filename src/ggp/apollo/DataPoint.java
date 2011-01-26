package ggp.apollo;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.annotations.*;

import org.datanucleus.store.query.AbstractQueryResult;

@PersistenceCapable
public class DataPoint {
    @PrimaryKey @Persistent private String theDataPoint;

    public DataPoint(String theData) {
        this.theDataPoint = theData;        
        PersistenceManager pm = PMF.get().getPersistenceManager();
        pm.makePersistent(this);
        pm.close();
    }
    
    public String getData() {
        return theDataPoint;
    }

    /* Static accessor methods */
    public static Set<DataPoint> loadData() throws IOException {
        Set<DataPoint> theData = new HashSet<DataPoint>();
        PersistenceManager pm = PMF.get().getPersistenceManager();
        try {
            Iterator<?> sqr = ((AbstractQueryResult) pm.newQuery(DataPoint.class).execute()).iterator();
            while (sqr.hasNext()) {
                theData.add((DataPoint)sqr.next());
            }            
        } catch(JDOObjectNotFoundException e) {
            ;
        } finally {
            pm.close();
        }
        return theData;
    }
    
    public static DataPoint loadDataPoints(String theKey) throws IOException {
        DataPoint theData = null;
        PersistenceManager pm = PMF.get().getPersistenceManager();
        try {
            theData = pm.detachCopy(pm.getObjectById(DataPoint.class, theKey));
        } catch(JDOObjectNotFoundException e) {
            ;
        } finally {
            pm.close();
        }
        return theData;
    }
    
    public static void clearDataPoints() throws IOException {
        Set<DataPoint> theData = loadData();

        for (DataPoint m : theData) {
            PersistenceManager pm = PMF.get().getPersistenceManager();
            try {
                pm.deletePersistent(pm.getObjectById(DataPoint.class, m.getData()));
            } catch(JDOObjectNotFoundException e) {
                ;
            } finally {
                pm.close();
            }
        }
    }
}