package com.test;

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

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.test.models.enums.Color.BLACK;
import static com.test.models.enums.Color.RED;
import static com.test.services.RouletteUtils.calculateInitialBalance;
import static com.test.services.RouletteUtils.estimatedProfit;

public class Main {

    @SneakyThrows
    public static void main(String[] args) {
        double[] baseBetAmounts = {1, 1.5, 2, 5};
        int[] maxRoundsOptions = {50, 100, 150, 200, 250, 500};
        boolean[] changeBetColorAfterWinOptions = {true, false};

        // Number of simulations to run for each parameter combination
        final int simulationsPerCombination = 100;

        List<SimulationResult> results = new ArrayList<>();

        // Use for parallel execution
        int totalTasks = baseBetAmounts.length * maxRoundsOptions.length * changeBetColorAfterWinOptions.length;
        ExecutorService executorService = Executors.newFixedThreadPool(Math.min(totalTasks, Runtime.getRuntime().availableProcessors()));

        List<Future<List<SimulationResult>>> futures = new ArrayList<>();
        int scenarioId = 1;
        for (double baseBetAmount : baseBetAmounts) {
            for (int maxRounds : maxRoundsOptions) {
                for (boolean changeBetColorAfterWin : changeBetColorAfterWinOptions) {
                    final int currentScenarioId = scenarioId;
                    final double initialBalance = calculateInitialBalance(baseBetAmount);
                    final double estimatedProfit = estimatedProfit(initialBalance, baseBetAmount, maxRounds);

                    Callable<List<SimulationResult>> task = () -> {
                        List<SimulationResult> simulationResults = new ArrayList<>();
                        for (int i = 0; i < simulationsPerCombination; i++) {
                            SimulationResult result = runSimulation(
                                    initialBalance,
                                    baseBetAmount,
                                    estimatedProfit,
                                    maxRounds,
                                    changeBetColorAfterWin,
                                    currentScenarioId
                            );
                            simulationResults.add(result);
                        }
                        return simulationResults;
                    };

                    scenarioId++;
                    futures.add(executorService.submit(task));
                }
            }
        }

        for (Future<List<SimulationResult>> future : futures) {
            results.addAll(future.get());
        }

        executorService.shutdown();
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }

        analyzeResultsCsv(results);
    }

    private static SimulationResult runSimulation(double initialBalance, double baseBetAmount, double estimatedProfit,
                                                  int maxRounds, boolean changeBetColorAfterWin, int scenarioId
    ) {
        RouletteService rouletteService = RouletteService.builder()
                .wheel(RouletteService.createWheel())
                .random(ThreadLocalRandom.current())
                .build();

        Color betColor = BLACK;
        double balance = initialBalance;
        double betAmount = baseBetAmount;

        double totalProfit = 0;
        double totalLoss = 0;

        int maxWinStreak = 0;
        int currentWinStreak = 0;
        int maxLossStreak = 0;
        int currentLossStreak = 0;

        boolean targetReached = false;
        boolean outOfMoney = false;

        int roundsPlayed = 0;

        while (balance >= betAmount) {
            Bet bet = Bet.builder()
                    .type(BetType.COLOR)
                    .amount(betAmount)
                    .bet(betColor)
                    .build();

            balance -= betAmount; // Place bet

            RouletteNumber result = rouletteService.spinWheel();
            double payout = rouletteService.evaluateBet(bet, result);

            if (payout > 0) {
                balance += payout;
                totalProfit += payout - betAmount;

                // Reset bet amount
                betAmount = baseBetAmount;

                if (changeBetColorAfterWin) {
                    betColor = (betColor == BLACK) ? RED : BLACK;
                }

                currentLossStreak = 0;
                currentWinStreak++;
                maxWinStreak = Math.max(maxWinStreak, currentWinStreak);
            } else {
                totalLoss += betAmount;

                betAmount *= 2;

                // Adjust bet amount if it exceeds the balance
                if (betAmount > balance) {
                    betAmount = balance;
                }

                currentWinStreak = 0;
                currentLossStreak++;
                maxLossStreak = Math.max(maxLossStreak, currentLossStreak);
            }

            if (balance >= initialBalance + estimatedProfit) {
                targetReached = true;
            }
            if (balance <= 0) {
                outOfMoney = true;
                break;
            }
            // If the player is not in a losing streak and max rounds exceeded, stop the simulation
            if (roundsPlayed > maxRounds && betAmount == baseBetAmount) {
                break;
            }

            roundsPlayed++;
        }

        return SimulationResult.builder()
                .initialBalance(initialBalance)
                .baseBetAmount(baseBetAmount)
                .maxRounds(maxRounds)
                .estimatedProfit(estimatedProfit)
                .balance(balance)
                .profit(balance - initialBalance)
                .totalProfit(totalProfit)
                .totalLoss(totalLoss)
                .roundsPlayed(roundsPlayed)
                .maxWinStreak(maxWinStreak)
                .maxLossStreak(maxLossStreak)
                .targetReached(targetReached)
                .outOfMoney(outOfMoney)
                .changeBetColorAfterWin(changeBetColorAfterWin)
                .scenarioId(scenarioId)
                .build();
    }

    @SneakyThrows
    private static void analyzeResultsCsv(List<SimulationResult> results) {
        File csvFile = new File("simulation_results.csv");
        if (!csvFile.exists()) {
            csvFile.createNewFile();
        }

        try (Writer writer = new FileWriter(csvFile)) {
            writer.write("initialBalance,baseBetAmount,maxRounds,estimatedProfit," +
                    "balance,profit,totalProfit,totalLoss," +
                    "roundsPlayed,maxWinStreak,maxLossStreak,targetReached," +
                    "outOfMoney,changeBetColorAfterWin,scenarioId\n");

            ColumnPositionMappingStrategy<SimulationResult> strategy = new ColumnPositionMappingStrategy<>();
            strategy.setType(SimulationResult.class);
            String[] memberFieldsToBindTo = {"initialBalance", "baseBetAmount", "maxRounds", "estimatedProfit",
                    "balance", "profit", "totalProfit", "totalLoss", "roundsPlayed", "maxWinStreak", "maxLossStreak",
                    "targetReached", "outOfMoney", "changeBetColorAfterWin", "scenarioId"};
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
