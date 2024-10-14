package com.test;

import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.test.models.SimulationParameters;
import com.test.models.SimulationResult;
import com.test.services.SimulationResultAnalyzer;
import com.test.services.SimulationService;
import lombok.SneakyThrows;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.test.utils.RouletteUtils.calculateInitialBalance;
import static com.test.utils.RouletteUtils.estimatedProfit;

public class Main {

    @SneakyThrows
    public static void main(String[] args) {
        double[] baseBetAmounts = {1, 1.5, 2, 5};
        int[] maxRoundsOptions = {50, 100, 150, 200, 250, 500};
        boolean[] changeBetColorAfterWinOptions = {false};

        // Number of simulations to run for each parameter combination
        final int simulationsPerCombination = 100;

        Stream<SimulationResult> results = Stream.empty();

        // Use for parallel execution
        int totalTasks = baseBetAmounts.length * maxRoundsOptions.length * changeBetColorAfterWinOptions.length;
        ExecutorService executorService = Executors.newFixedThreadPool(Math.min(totalTasks, Runtime.getRuntime().availableProcessors()));

        List<Future<Stream<SimulationResult>>> futures = new ArrayList<>();
        for (double baseBetAmount : baseBetAmounts) {
            for (int maxRounds : maxRoundsOptions) {
                for (boolean changeBetColorAfterWin : changeBetColorAfterWinOptions) {
                    Callable<Stream<SimulationResult>> task = () -> {
                        SimulationService simulationService = SimulationService.builder()
                                .parameters(SimulationParameters.builder()
                                        .baseBetAmount(baseBetAmount)
                                        .maxRounds(maxRounds)
                                        .changeBetColorAfterWin(changeBetColorAfterWin)
                                        .build())
                                .build();

                        return IntStream.range(0, simulationsPerCombination)
                                .mapToObj(i -> simulationService.runSimulation());
                    };

                    futures.add(executorService.submit(task));
                }
            }
        }

        for (Future<Stream<SimulationResult>> future : futures) {
            results = Stream.concat(results, future.get());
        }

        executorService.shutdown();
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }

//        analyzeResultsCsv(results);

        SimulationResultAnalyzer analyzer = new SimulationResultAnalyzer();
        analyzer.generateAggregatedReport(results);

        System.out.println("Simulation results have been analyzed");
    }

    @SneakyThrows
    private static void analyzeResultsCsv(Stream<SimulationResult> results) {
        File csvFile = new File("simulation_results.csv");
        if (!csvFile.exists()) {
            csvFile.createNewFile();
        }

        try (Writer writer = new FileWriter(csvFile)) {
            writer.write(String.join(",", SimulationResult.getFieldNames()) + "\n");

            ColumnPositionMappingStrategy<SimulationResult> strategy = new ColumnPositionMappingStrategy<>();
            strategy.setType(SimulationResult.class);
            String[] memberFieldsToBindTo = SimulationResult.getFieldNames().toArray(new String[0]);
            strategy.setColumnMapping(memberFieldsToBindTo);

            StatefulBeanToCsv<SimulationResult> beanToCsv = new StatefulBeanToCsvBuilder<SimulationResult>(writer)
                    .withMappingStrategy(strategy)
                    .withApplyQuotesToAll(false)
                    .build();

            beanToCsv.write(results);
        }

        System.out.println("Simulation results have been written to " + csvFile.getAbsolutePath());
    }
}
