package com.test.services;

import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.test.models.AggregatedMetrics;
import com.test.models.ScenarioAccumulator;
import com.test.models.SimulationResult;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collector;
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

            Stream<AggregatedMetrics> aggregatedMetricsStream = calculateAggregatedMetrics(simulationResults.parallel());
            beanToCsv.write(aggregatedMetricsStream.toList());
        }

        System.out.println("Simulation results have been written to " + csvFile.getAbsolutePath());
    }

    public Stream<AggregatedMetrics> calculateAggregatedMetrics(Stream<SimulationResult> simulationResults) {
        Map<Long, ScenarioAccumulator> accumulatorMap = simulationResults.collect(
                Collector.of(
                        ConcurrentHashMap::new,
                        (map, result) -> {
                            long scenarioId = result.getScenarioId();
                            map.computeIfAbsent(scenarioId, k -> new ScenarioAccumulator())
                                    .add(result);
                        },
                        (map1, map2) -> {
                            map2.forEach((key, value) -> map1.merge(key, value, ScenarioAccumulator::combine));
                            return map1;
                        }
                )
        );

        return accumulatorMap.entrySet().stream()
                .map(entry -> entry.getValue().toAggregatedMetrics(entry.getKey()));
    }
}
