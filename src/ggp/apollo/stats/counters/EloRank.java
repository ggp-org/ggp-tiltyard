package ggp.apollo.stats.counters;

import java.util.HashMap;
import java.util.Map;

public class EloRank {
    private Map<String,Integer> theMatchCount;
    private Map<String,Double> theTrueStrength;
    
    public EloRank() {
        theTrueStrength = new HashMap<String, Double>();
        theMatchCount = new HashMap<String, Integer>();
    }
    
    public void addNextMatch(String aPlayer, String bPlayer, int aScore, int bScore) {
        if (!theTrueStrength.containsKey(aPlayer)) {
            theTrueStrength.put(aPlayer, 0.0);
            theMatchCount.put(aPlayer, 0);
        }
        if (!theTrueStrength.containsKey(bPlayer)) {
            theTrueStrength.put(bPlayer, 0.0);
            theMatchCount.put(bPlayer, 0);
        }
        
        double EA = getExpectedScore(aPlayer, bPlayer);
        double EB = 1.0 - EA;
        
        theTrueStrength.put(aPlayer, theTrueStrength.get(aPlayer) + getConstant(aPlayer) * (aScore/100.0 - EA));
        theTrueStrength.put(bPlayer, theTrueStrength.get(bPlayer) + getConstant(bPlayer) * (bScore/100.0 - EB));        
        theMatchCount.put(aPlayer, theMatchCount.get(aPlayer) + 1);
        theMatchCount.put(bPlayer, theMatchCount.get(bPlayer) + 1);
    }
    
    public Map<String,Double> getComputedRanks() {
        return theTrueStrength;
    }
    
    /* Private functions */
    private double getExpectedScore(String thePlayer, String opposingPlayer) {
        double RA = theTrueStrength.get(thePlayer);
        double RB = theTrueStrength.get(opposingPlayer);
        double QA = Math.pow(10.0, RA / 400.0);
        double QB = Math.pow(10.0, RB / 400.0);
        return QA / (QA + QB);
    }
    
    private double getConstant(String thePlayer) {
        int nCount = theMatchCount.get(thePlayer);
        double theRank = theTrueStrength.get(thePlayer);
        if (nCount < 10) return 64;
        if (nCount < 50) return 32;
        if (nCount < 250) return 16;
        if (theRank < 2400) return 12;
        return 8;
    }
}