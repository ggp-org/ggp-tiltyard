package ggp.apollo.stats.counters;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class QuickRank {
    private final double GAMMA = 0.85;
    private Set<String> distinctNodes;
    private Map<String,Map<String,Double>> theWeights;
    private Map<String,Double> theComputedRanks;
    private double theDelta;
    private double theError;
    
    public QuickRank() {
        distinctNodes = new HashSet<String>();
        theWeights = new HashMap<String, Map<String,Double>>();
    }
    
    public void addVote(String toNode, String fromNode) {
        addWeight(toNode, fromNode, 1.0);
    }
    
    public void addWeight(String toNode, String fromNode, double w) {
        if (!theWeights.containsKey(toNode)) {
            theWeights.put(toNode, new HashMap<String,Double>());
        }
        if (!theWeights.get(toNode).containsKey(fromNode)) {
            theWeights.get(toNode).put(fromNode, w);
        } else {
            theWeights.get(toNode).put(fromNode, theWeights.get(toNode).get(fromNode)+w);
        }        
                
        distinctNodes.add(fromNode);
        distinctNodes.add(toNode);        
    }
    
    // Ensure that all of the nodes that have PlayerRank flowing outward
    // have their outward flows normalized, so that they can be scaled by
    // the node's PlayerRank.
    private void normalizeWeights () {
        for (String fromNode : distinctNodes) {
            double outgoingSum = 0;
            for (String toNode : theWeights.keySet()) {
                if (theWeights.get(toNode).containsKey(fromNode)) {
                    outgoingSum += theWeights.get(toNode).get(fromNode);
                }
            }
            if (outgoingSum == 0) continue;
            for (String toNode : theWeights.keySet()) {
                if (theWeights.get(toNode).containsKey(fromNode)) {
                    theWeights.get(toNode).put(fromNode, theWeights.get(toNode).get(fromNode)/outgoingSum);
                }
            }            
        }
    }
    
    public void computeRanks(int nIterations) {
        normalizeWeights();
        
        final double MAX = 10000;
        final double N = distinctNodes.size();
        Map<String,Double> theOldRanks = new HashMap<String,Double>();
        Map<String,Double> theNewRanks = new HashMap<String,Double>();
        for (String toNode : distinctNodes) {
            theOldRanks.put(toNode, MAX/N);
            theNewRanks.put(toNode, MAX/N);
        }
        
        theDelta = -1;
        for (int i = 0; i < nIterations; i++) {
            for (String toNode : theNewRanks.keySet()) {
                double incoming = 0;
                if (theWeights.containsKey(toNode)) {
                    for (String fromNode : theWeights.get(toNode).keySet()) {
                        double w = theWeights.get(toNode).get(fromNode);
                        double fromRank = theOldRanks.get(fromNode);
                        incoming += w*fromRank;
                    }
                }
                //double newRank = (1.0 - GAMMA)*(MAX/N) + GAMMA*(incoming);
                double newRank = (1.0 - GAMMA)*theOldRanks.get(toNode) + GAMMA*(incoming);
                theNewRanks.put(toNode, newRank);
            }
            
            theDelta = computeDifference(theOldRanks, theNewRanks);
            theOldRanks.clear();
            theOldRanks.putAll(theNewRanks);            
        }
                
        theComputedRanks = theNewRanks;
        theError = computeError(theComputedRanks);
    }
    
    public Map<String,Double> getComputedRanks() {
        return theComputedRanks;
    }
    
    public double getComputedDelta() {
        return theDelta;
    }
    
    public double getComputedError() {
        return theError;
    }
    
    private double computeDifference(Map<String,Double> a, Map<String,Double> b) {
        double x = 0;
        for (String k : a.keySet()) {
            double v1 = a.get(k);
            double v2 = b.get(k);
            x += Math.abs(v1 - v2);
        }
        return x;
    }
    
    private double computeError(Map<String,Double> theRanks) {
        double x = 0;
        for (String toPlayer : theWeights.keySet()) {
            double weightedSum = 0;
            for (String fromPlayer : theWeights.get(toPlayer).keySet()) {
                if (theRanks.containsKey(fromPlayer)) {
                    weightedSum += theWeights.get(toPlayer).get(fromPlayer) * theRanks.get(fromPlayer);
                }
            }
            x += Math.abs(theRanks.get(toPlayer) - weightedSum);
        }
        return x;
    }
    
    public int getRankVersion() {
        return 11;
    }
}