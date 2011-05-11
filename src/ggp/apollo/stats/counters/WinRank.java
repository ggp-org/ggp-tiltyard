package ggp.apollo.stats.counters;

import java.util.HashMap;
import java.util.Map;

public class WinRank {
    private Map<String,Integer> theWinRanks;
    
    public WinRank() {
        theWinRanks = new HashMap<String, Integer>();
    }
    
    public void recordWin(String theWinner, String theLoser) {
        if (!theWinRanks.containsKey(theWinner)) {
            theWinRanks.put(theWinner, 0);
        }
        if (!theWinRanks.containsKey(theLoser)) {
            theWinRanks.put(theLoser, 0);
        }
        
        theWinRanks.put(theWinner, theWinRanks.get(theWinner)+1);
        theWinRanks.put(theLoser, theWinRanks.get(theLoser)-1);
    }
    
    public Map<String,Integer> getComputedRanks() {
        return theWinRanks;
    }
}