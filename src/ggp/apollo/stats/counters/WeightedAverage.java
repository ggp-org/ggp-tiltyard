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
    
    public String toString() {
        return "" + getWeightedAverage();
    }
}