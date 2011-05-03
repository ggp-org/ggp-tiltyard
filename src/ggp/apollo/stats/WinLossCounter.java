package ggp.apollo.stats;

public class WinLossCounter {
    private int myWins = 0;
    private int myTies = 0;
    private int myLosses = 0;
    
    public WinLossCounter() {
        ;
    }
    
    public void addEntry(int myScore, int theirScore) {
        if (myScore > theirScore) {
            myWins++;
        } else if (theirScore > myScore) {
            myLosses++;
        } else {
            myTies++;
        }
    }
    
    public String toString() {
        return "" + myWins + "-" + myTies + "-" + myLosses;
    }
}