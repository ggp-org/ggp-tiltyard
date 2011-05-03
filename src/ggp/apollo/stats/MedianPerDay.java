package ggp.apollo.stats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MedianPerDay {
    private Map<Long,Integer> eventsPerDay;
    
    public MedianPerDay() {
        eventsPerDay = new HashMap<Long,Integer>();
    }
    
    public static <T extends Comparable<? super T>> T getRoughMedian(Collection<T> theObjects) {
        List<T> theList = new ArrayList<T>(theObjects); 
        Collections.sort(theList);
        return theList.get(theList.size()/2);
    }    
    
    public void addToDay(int nValue, long theTime) {
        Long theDayStart = theTime / 86400000L;
        if (!eventsPerDay.containsKey(theDayStart)) {
            eventsPerDay.put(theDayStart, 0);
        }
        eventsPerDay.put(theDayStart, eventsPerDay.get(theDayStart)+nValue);
    }
    
    public double getMedianPerDay() {
        return getRoughMedian(eventsPerDay.values());
    }
    
    public String toString() {
        return "" + getMedianPerDay();
    }
}