package com.test;

import com.opencsv.CSVWriter;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.test.models.Bet;
import com.test.models.RouletteNumber;
import com.test.models.SimulationResult;
import com.test.models.enums.BetType;
import com.test.models.enums.Color;
import com.test.services.RouletteService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.test.models.enums.Color.BLACK;
import static com.test.models.enums.Color.RED;

@Slf4j
public class Main {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        double initialBalance = 500;

        double[] baseBetAmounts = {1, 2, 5};
        double[] targetBalanceMultipliers = {1.2, 1.3, 1.4, 1.5, 2}; // As multiples of initial balance
        double[] stopLossLimitMultipliers = {1, 1.1, 1.2}; // As multiples of initial balance
        double[] maxBetPercentages = {0.25, 0.5, 0.75}; // As fractions of initial balance
        int[] maxRoundsOptions = {100, 200, 500};
        boolean[] changeBetColorAfterWinOptions = {true, false};

        // Number of simulations to run for each parameter combination
        int simulationsPerCombination = 10;

        List<SimulationResult> results = new ArrayList<>();

        // Use for parallel execution
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        List<Future<List<SimulationResult>>> futures = new ArrayList<>();
        for (double baseBetAmount : baseBetAmounts) {
            for (double targetBalanceMultiplier : targetBalanceMultipliers) {
                for (double stopLossLimitMultiplier : stopLossLimitMultipliers) {
                    for (double maxBetPercentage : maxBetPercentages) {
                        for (int maxRounds : maxRoundsOptions) {
                            for (boolean changeBetColorAfterWin : changeBetColorAfterWinOptions) {
                                final double targetBalance = initialBalance * targetBalanceMultiplier;
                                final double stopLossLimit = initialBalance * stopLossLimitMultiplier;

                                futures.add(executorService.submit(() -> {
                                    List<SimulationResult> simulationResults = new ArrayList<>();
                                    for (int i = 0; i < simulationsPerCombination; i++) {
                                        SimulationResult result = runSimulation(
                                                initialBalance,
                                                baseBetAmount,
                                                targetBalance,
                                                stopLossLimit,
                                                maxBetPercentage,
                                                maxRounds,
                                                changeBetColorAfterWin
                                        );
                                        simulationResults.add(result);
                                    }
                                    return simulationResults;
                                }));
                            }
                        }
                    }
                }
            }
        }

        for (Future<List<SimulationResult>> future : futures) {
            results.addAll(future.get());
        }
        executorService.shutdown();

        analyzeResultsCsv(results); // change to analyzeResultsConsole(results) to print to console
    }

    public static SimulationResult runSimulation(double initialBalance, double baseBetAmount, double targetBalance,
                                                 double stopLossLimit, double maxBetPercentage, int maxRounds,
                                                 boolean changeBetColorAfterWin) {
        RouletteService rouletteService = RouletteService.builder()
                .wheel(RouletteService.createWheel())
                .random(new Random())
                .build();

        Color betColor = BLACK;

        double balance = initialBalance;

        double betAmount = baseBetAmount;
        double maxBetLimit = initialBalance * maxBetPercentage;

        double totalProfit = 0;
        double totalLoss = 0;

        boolean targetReached = false;
        boolean stopLossReached = false;
        boolean outOfMoney = false;

        int round = 0;
        while (balance >= betAmount && round < maxRounds && !targetReached && !stopLossReached) {
            Bet bet = Bet.builder()
                    .type(BetType.COLOR)
                    .amount(betAmount)
                    .bet(betColor)
                    .build();

            balance -= betAmount; // place bet

            RouletteNumber result = rouletteService.spinWheel();
            double payout = rouletteService.evaluateBet(bet, result);

            if (payout > 0) {
                balance += payout;
                totalProfit += payout - betAmount;

                // Reset bet amount
                betAmount = baseBetAmount;

                if (changeBetColorAfterWin) {
                    betColor = betColor == BLACK ? RED : BLACK;
                }
            } else {
                totalLoss += betAmount;

                betAmount *= 2;
                if (betAmount > maxBetLimit) {
                    betAmount = maxBetLimit;
                }

                if (betAmount > balance) {
                    betAmount = balance;
                }
            }

            if (balance >= targetBalance) {
                targetReached = true;
            }
            if (totalLoss >= stopLossLimit) {
                stopLossReached = true;
            }
            if (balance <= 0) {
                outOfMoney = true;
                break;
            }

            round++;
        }

        return SimulationResult.builder()
                .initialBalance(initialBalance)
                .baseBetAmount(baseBetAmount)
                .maxBetPercentage(maxBetPercentage)
                .maxRounds(maxRounds)
                .targetBalance(targetBalance)
                .stopLossLimit(stopLossLimit)
                .balance(balance)
                .profit(balance - initialBalance)
                .totalProfit(totalProfit)
                .totalLoss(totalLoss)
                .roundsPlayed(round)
                .targetReached(targetReached)
                .stopLossReached(stopLossReached)
                .outOfMoney(outOfMoney)
                .build();
    }

    public static void analyzeResultsConsole(List<SimulationResult> results) {
        // Group results by parameter combinations
        Map<String, List<SimulationResult>> groupedResults = results.stream()
                .collect(Collectors.groupingBy(SimulationResult::toString));

        for (Map.Entry<String, List<SimulationResult>> entry : groupedResults.entrySet()) {
            String key = entry.getKey();
            List<SimulationResult> simulations = entry.getValue();

            long successCount = simulations.stream().filter(SimulationResult::isTargetReached).count();
            long failureCount = simulations.stream().filter(s -> s.isStopLossReached() || s.isOutOfMoney()).count();
            double averageFinalBalance = simulations.stream().mapToDouble(SimulationResult::getBalance).average().orElse(0);
            double averageRounds = simulations.stream().mapToInt(SimulationResult::getRoundsPlayed).average().orElse(0);

            System.out.println("Parameters: " + key);
            System.out.println("Simulations run: " + simulations.size());
            System.out.println("Success count: " + successCount);
            System.out.println("Failure count: " + failureCount);
            System.out.println("Average final balance: $" + String.format("%.2f", averageFinalBalance));
            System.out.println("Average rounds played: " + String.format("%.2f", averageRounds));
            System.out.println("-------------------------------------------");
        }
    }

    @SneakyThrows
    public static void analyzeResultsCsv(List<SimulationResult> results) {
        File csvFile = new File("simulation_results.csv");
        if (!csvFile.exists()) {
            csvFile.createNewFile();
        }
        try (Writer writer = new FileWriter(csvFile)) {
            writer.write("InitialBalance,BaseBetAmount,MaxBetPercentage,MaxRounds,TargetBalance,StopLossLimit,Balance,Profit,TotalProfit,TotalLoss,RoundsPlayed,TargetReached,StopLossReached,OutOfMoney\n");


            ColumnPositionMappingStrategy<SimulationResult> strategy = new ColumnPositionMappingStrategy<>();
            strategy.setType(SimulationResult.class);
            strategy.setColumnMapping("initialBalance", "baseBetAmount", "maxBetPercentage", "maxRounds", "targetBalance",
                    "stopLossLimit", "balance", "profit", "totalProfit", "totalLoss", "roundsPlayed",
                    "targetReached", "stopLossReached", "outOfMoney");

            StatefulBeanToCsv<SimulationResult> beanToCsv = new StatefulBeanToCsvBuilder<SimulationResult>(writer)
                    .withMappingStrategy(strategy)
                    .withQuotechar(CSVWriter.NO_QUOTE_CHARACTER)
                    .build();

            beanToCsv.write(results);
        }

        System.out.println("Simulation results have been written to " + csvFile.getAbsolutePath());
    }
}
