package com.test.services;

import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.test.models.AggregatedMetrics;
import com.test.models.SimulationResult;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class SimulationResultAnalyzer {

    @SneakyThrows
    public void generateAggregatedReport(Stream<SimulationResult> simulationResults) {
        File csvFile = new File("simulation_results_analysed.csv");
        if (!csvFile.exists()) {
            csvFile.createNewFile();
        }

        try (Writer writer = new FileWriter(csvFile)) {
            writer.write(String.join(",", AggregatedMetrics.getFieldNames()) + "\n");

            ColumnPositionMappingStrategy<AggregatedMetrics> strategy = new ColumnPositionMappingStrategy<>();
            strategy.setType(AggregatedMetrics.class);
            String[] memberFieldsToBindTo = AggregatedMetrics.getFieldNames().toArray(new String[0]);
            strategy.setColumnMapping(memberFieldsToBindTo);

            StatefulBeanToCsv<AggregatedMetrics> beanToCsv = new StatefulBeanToCsvBuilder<AggregatedMetrics>(writer)
                    .withMappingStrategy(strategy)
                    .withApplyQuotesToAll(false)
                    .build();

            beanToCsv.write(calculateAggregatedMetrics(simulationResults));
        }

        System.out.println("Simulation results have been written to " + csvFile.getAbsolutePath());
    }

    public List<AggregatedMetrics> calculateAggregatedMetrics(Stream<SimulationResult> simulationResults) {
        Map<Long, List<SimulationResult>> groupedResults = simulationResults
                .collect(Collectors.groupingBy(SimulationResult::getScenarioId));

        List<AggregatedMetrics> metrics = new ArrayList<>();
        for (var entry : groupedResults.entrySet()) {
            long scenarioId = entry.getKey();
            List<SimulationResult> results = entry.getValue();

            DescriptiveStatistics profitStats = new DescriptiveStatistics();
            DescriptiveStatistics roundsStats = new DescriptiveStatistics();
            int outOfMoneyCount = 0;
            int targetReachedCount = 0;

            for (SimulationResult result : results) {
                profitStats.addValue(result.getProfit());
                roundsStats.addValue(result.getRoundsPlayed());
                if (result.isOutOfMoney()) outOfMoneyCount++;
                if (result.isTargetReached()) targetReachedCount++;
            }

            int n = results.size();

            double averageProfit = profitStats.getMean();
            double medianProfit = profitStats.getPercentile(50);
            double profitStdDev = profitStats.getStandardDeviation();
            double probabilityOfRuin = (double) outOfMoneyCount / n;
            double probabilityOfReachingTarget = (double) targetReachedCount / n;
            double averageRoundsPlayed = roundsStats.getMean();

            // Calculate 95% confidence interval for mean profit
            double confidenceLevel = 0.95;
            TDistribution tDist = new TDistribution(n - 1);
            double tCritical = tDist.inverseCumulativeProbability(1 - (1 - confidenceLevel) / 2);
            double standardError = profitStdDev / Math.sqrt(n);
            double marginOfError = tCritical * standardError;
            double confidenceIntervalLower = averageProfit - marginOfError;
            double confidenceIntervalUpper = averageProfit + marginOfError;

            metrics.add(AggregatedMetrics.builder()
                    .scenarioId(scenarioId)
                    .averageProfit(averageProfit)
                    .medianProfit(medianProfit)
                    .profitStdDev(profitStdDev)
                    .probabilityOfRuin(probabilityOfRuin)
                    .probabilityOfReachingTarget(probabilityOfReachingTarget)
                    .averageRoundsPlayed(averageRoundsPlayed)
                    .confidenceIntervalLower(confidenceIntervalLower)
                    .confidenceIntervalUpper(confidenceIntervalUpper)
                    .build());
        }

        return metrics;
    }
}
