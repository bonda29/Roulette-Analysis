package com.test;

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
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class Main {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        double initialBalance = 500;
        double[] baseBetAmounts = {1, 2, 5};
        double[] targetProfits = {50, 100, 200};
        double[] stopLossLimits = {100, 200, 300};
        double[] maxBetPercentages = {0.25, 0.5, 0.75}; // As fractions of initial balance
        int[] maxRoundsOptions = {100, 500, 1000};
        Color[] betColors = {Color.RED, Color.BLACK};

        // Number of simulations to run for each parameter combination
        int simulationsPerCombination = 1000;

        List<SimulationResult> results = new ArrayList<>();

        int totalCombinations = baseBetAmounts.length * targetProfits.length * stopLossLimits.length
                * maxBetPercentages.length * maxRoundsOptions.length * betColors.length;

        int combinationCounter = 1;

        // Use for parallel execution
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        List<Future<List<SimulationResult>>> futures = new ArrayList<>();

        for (double baseBetAmount : baseBetAmounts) {
            for (double targetProfit : targetProfits) {
                for (double stopLossLimit : stopLossLimits) {
                    for (double maxBetPercentage : maxBetPercentages) {
                        for (int maxRounds : maxRoundsOptions) {
                            for (Color betColor : betColors) {
                                final double bbAmount = baseBetAmount;
                                final double tProfit = targetProfit;
                                final double sLossLimit = stopLossLimit;
                                final double mBetPercentage = maxBetPercentage;
                                final int mRounds = maxRounds;
                                final Color bColor = betColor;

                                futures.add(executorService.submit(() -> {
                                    List<SimulationResult> simulationResults = new ArrayList<>();
                                    for (int i = 0; i < simulationsPerCombination; i++) {
                                        SimulationResult result = runSimulation(
                                                initialBalance,
                                                bbAmount,
                                                tProfit,
                                                sLossLimit,
                                                mBetPercentage,
                                                mRounds,
                                                bColor,
                                                false // Set to true if you want detailed logs
                                        );
                                        simulationResults.add(result);
                                    }
                                    return simulationResults;
                                }));

                                System.out.println("Submitted simulation batch " + combinationCounter + " of " + totalCombinations);
                                combinationCounter++;
                            }
                        }
                    }
                }
            }
        }

        // Wait for all simulations to complete and collect results
        for (Future<List<SimulationResult>> future : futures) {
            results.addAll(future.get());
        }

        executorService.shutdown();

        analyzeResultsCsv(results);
    }

    public static SimulationResult runSimulation(double initialBalance, double baseBetAmount, double targetProfit,
                                                 double stopLossLimit, double maxBetPercentage, int maxRounds,
                                                 Color betColor, boolean verbose) {
        RouletteService rouletteService = RouletteService.builder()
                .wheel(RouletteService.createWheel())
                .random(new Random())
                .build();

        double balance = initialBalance;
        double betAmount = baseBetAmount;
        double maxBetLimit = initialBalance * maxBetPercentage;
        double totalProfit = 0;
        double totalLoss = 0;
        int round = 0;
        boolean targetReached = false;
        boolean stopLossReached = false;
        boolean outOfMoney = false;

        while (balance >= betAmount && round < maxRounds && !targetReached && !stopLossReached) {
            Bet bet = Bet.builder()
                    .type(BetType.COLOR)
                    .amount(betAmount)
                    .bet(betColor)
                    .build();

            RouletteNumber result = rouletteService.spinWheel();
            if (verbose) {
                System.out.println("Round " + (round + 1) + ": The ball landed on " + result.getNumber() + " " + result.getColor());
            }

            double payout = rouletteService.evaluateBet(bet, result);

            balance -= betAmount;
            if (payout > 0) {
                balance += payout;
                totalProfit += payout - betAmount;
                if (verbose) {
                    System.out.println("Bet on " + bet.getBet() + " wins! Payout: $" + payout);
                    System.out.println("Balance: $" + balance);
                }

                // Reset bet amount to base
                betAmount = baseBetAmount;
            } else {
                totalLoss += betAmount;
                if (verbose) {
                    System.out.println("Bet on " + bet.getBet() + " loses.");
                    System.out.println("Balance: $" + balance);
                }

                betAmount *= 2;
                if (betAmount > maxBetLimit) {
                    betAmount = maxBetLimit;
                }

                if (betAmount > balance) {
                    betAmount = balance;
                }
            }

            if (totalProfit >= targetProfit) {
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
                .targetProfit(targetProfit)
                .stopLossLimit(stopLossLimit)
                .maxBetPercentage(maxBetPercentage)
                .maxRounds(maxRounds)
                .betColor(betColor)
                .balance(balance)
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
                .collect(Collectors.groupingBy(SimulationResult::getParameterCombinationKey));

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
        String csvFile = "simulation_results.csv";
        File file = new File(csvFile);
            if (!file.exists()) {
                file.createNewFile();
            }
            try (FileWriter writer = new FileWriter(file)) {
                writer.append("InitialBalance,BaseBetAmount,TargetProfit,StopLossLimit,MaxBetPercentage,MaxRounds,BetColor,")
                        .append("FinalBalance,TotalProfit,TotalLoss,RoundsPlayed,TargetReached,StopLossReached,OutOfMoney\n");

                for (SimulationResult result : results) {
                    writer.append(String.valueOf(result.getInitialBalance())).append(",")
                            .append(String.valueOf(result.getBaseBetAmount())).append(",")
                            .append(String.valueOf(result.getTargetProfit())).append(",")
                            .append(String.valueOf(result.getStopLossLimit())).append(",")
                            .append(String.valueOf(result.getMaxBetPercentage())).append(",")
                            .append(String.valueOf(result.getMaxRounds())).append(",")
                            .append(result.getBetColor().toString()).append(",")
                            .append(String.valueOf(result.getBalance())).append(",")
                            .append(String.valueOf(result.getTotalProfit())).append(",")
                            .append(String.valueOf(result.getTotalLoss())).append(",")
                            .append(String.valueOf(result.getRoundsPlayed())).append(",")
                            .append(String.valueOf(result.isTargetReached())).append(",")
                            .append(String.valueOf(result.isStopLossReached())).append(",")
                            .append(String.valueOf(result.isOutOfMoney())).append("\n");
                }

                System.out.println("Simulation results have been written to " + csvFile);
            }

            System.out.println("Simulation results have been written to " + file.getAbsolutePath());
    }
}
