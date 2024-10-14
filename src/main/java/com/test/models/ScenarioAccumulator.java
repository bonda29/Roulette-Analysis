package com.test.models;

import lombok.Data;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Data
public class ScenarioAccumulator {
    private static final int MAX_SAMPLES = 1000;

    private int count = 0;
    private double sumProfit = 0.0;
    private double sumProfitSq = 0.0;
    private int outOfMoneyCount = 0;
    private int targetReachedCount = 0;
    private double sumRounds = 0.0;

    private Percentile medianEstimator = new Percentile().withEstimationType(Percentile.EstimationType.R_7);
    private List<Double> profitSamples = new ArrayList<>();

    public void add(SimulationResult result) {
        double profit = result.getProfit();
        sumProfit += profit;
        sumProfitSq += profit * profit;
        count++;

        sumRounds += result.getRoundsPlayed();

        if (result.isOutOfMoney()) outOfMoneyCount++;
        if (result.isTargetReached()) targetReachedCount++;

        // Collect samples for approximate median calculation
        if (profitSamples.size() < MAX_SAMPLES) {
            profitSamples.add(profit);
        } else {
            // Replace existing samples randomly to keep the sample representative
            int index = new Random().nextInt(MAX_SAMPLES);
            profitSamples.set(index, profit);
        }
    }

    public ScenarioAccumulator combine(ScenarioAccumulator other) {
        this.sumProfit += other.sumProfit;
        this.sumProfitSq += other.sumProfitSq;
        this.count += other.count;

        this.sumRounds += other.sumRounds;

        this.outOfMoneyCount += other.outOfMoneyCount;
        this.targetReachedCount += other.targetReachedCount;

        this.profitSamples.addAll(other.profitSamples);
        if (this.profitSamples.size() > MAX_SAMPLES) {

            // Reduce sample size back to MAX_SAMPLES
            Collections.shuffle(this.profitSamples);
            this.profitSamples = this.profitSamples.subList(0, MAX_SAMPLES);
        }

        return this;
    }

    public AggregatedMetrics toAggregatedMetrics(long scenarioId) {
        int n = count;
        double averageProfit = sumProfit / n;
        double varianceProfit = (sumProfitSq - (sumProfit * sumProfit) / n) / (n - 1);
        double profitStdDev = Math.sqrt(varianceProfit);

        double probabilityOfRuin = (double) outOfMoneyCount / n;
        double probabilityOfReachingTarget = (double) targetReachedCount / n;
        double averageRoundsPlayed = sumRounds / n;

        // Approximate median calculation
        double[] samples = profitSamples.stream().mapToDouble(Double::doubleValue).toArray();
        double medianProfit = medianEstimator.evaluate(samples, 50.0);

        // Calculate 95% confidence interval for mean profit
        double confidenceLevel = 0.95;
        TDistribution tDist = new TDistribution(n - 1);
        double tCritical = tDist.inverseCumulativeProbability(1 - (1 - confidenceLevel) / 2);
        double standardError = profitStdDev / Math.sqrt(n);
        double marginOfError = tCritical * standardError;
        double confidenceIntervalLower = averageProfit - marginOfError;
        double confidenceIntervalUpper = averageProfit + marginOfError;

        return AggregatedMetrics.builder()
                .scenarioId(scenarioId)
                .averageProfit(averageProfit)
                .medianProfit(medianProfit)
                .profitStdDev(profitStdDev)
                .probabilityOfRuin(probabilityOfRuin)
                .probabilityOfReachingTarget(probabilityOfReachingTarget)
                .averageRoundsPlayed(averageRoundsPlayed)
                .confidenceIntervalLower(confidenceIntervalLower)
                .confidenceIntervalUpper(confidenceIntervalUpper)
                .build();
    }
}
