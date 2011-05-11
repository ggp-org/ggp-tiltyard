package ggp.apollo.stats.counters;

public class WeightedAverage {
    private int count = 0;
    private double totalWeight = 0;
    private double totalSum = 0;
    
    public WeightedAverage() {
        ;
    }
    
    public void addValue(double value) {
        addValue(value, 1.0);
    }
    
    public void addValue(double value, double weight) {
        totalWeight += weight;
        totalSum += value*weight;
        count++;
    }
    
    public int getNumValues() {
        return count;
    }
    
    public double getWeightedAverage() {
        if (totalWeight == 0) {
            return -1;
        }
        return totalSum / totalWeight;
    }
    
    // TODO: Right now this returns a string that needs to be interpreted using
    // JSON.parse. This is awkward, since it's already being put into a JSON object
    // that holds statistics. Can we have this list actually be JSON?
    public String toString() {
        return "[" + getWeightedAverage() + ", " + count + "]";
    }
}