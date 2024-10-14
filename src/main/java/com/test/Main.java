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
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.test.models.enums.Color.BLACK;
import static com.test.models.enums.Color.RED;
import static com.test.services.RouletteUtils.calculateInitialBalance;
import static com.test.services.RouletteUtils.estimatedProfit;

@Slf4j
public class Main {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        double[] baseBetAmounts = {1, 2, 5};
        int[] maxRoundsOptions = {100, 200, 500};
        boolean[] changeBetColorAfterWinOptions = {true, false};

        // Number of simulations to run for each parameter combination
        int simulationsPerCombination = 100;

        List<SimulationResult> results = new ArrayList<>();

        // Use for parallel execution
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        List<Future<List<SimulationResult>>> futures = new ArrayList<>();
        for (double baseBetAmount : baseBetAmounts) {
            for (int maxRounds : maxRoundsOptions) {
                for (boolean changeBetColorAfterWin : changeBetColorAfterWinOptions) {
                    final double initialBalance = calculateInitialBalance(baseBetAmount);
                    final double targetBalance = initialBalance + estimatedProfit(initialBalance, baseBetAmount, maxRounds);

                    futures.add(executorService.submit(() -> {
                        List<SimulationResult> simulationResults = new ArrayList<>();
                        for (int i = 0; i < simulationsPerCombination; i++) {
                            SimulationResult result = runSimulation(
                                    initialBalance,
                                    baseBetAmount,
                                    targetBalance,
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

        for (Future<List<SimulationResult>> future : futures) {
            results.addAll(future.get());
        }
        executorService.shutdown();

        analyzeResultsCsv(results); // change to analyzeResultsConsole(results) to print to console
    }

    public static SimulationResult runSimulation(double initialBalance, double baseBetAmount, double targetBalance,
                                                 int maxRounds, boolean changeBetColorAfterWin
    ) {
        RouletteService rouletteService = RouletteService.builder()
                .wheel(RouletteService.createWheel())
                .random(new Random())
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

        int round = 0;
        while (balance >= betAmount && !targetReached) {
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

                currentLossStreak = 0;
                currentWinStreak++;
                if (currentWinStreak > maxWinStreak) {
                    maxWinStreak = currentWinStreak;
                }
            } else {
                totalLoss += betAmount;

                betAmount *= 2;

                if (betAmount > balance) {
                    betAmount = balance;
                }

                currentWinStreak = 0;
                currentLossStreak++;
                if (currentLossStreak > maxLossStreak) {
                    maxLossStreak = currentLossStreak;
                }
            }

            if (balance >= targetBalance) {
                targetReached = true;
            }
            if (balance <= 0) {
                outOfMoney = true;
                break;
            }
            if (round > maxRounds && betAmount == baseBetAmount) { // do not stop if you are on a losing streak
                break;
            }

            round++;
        }

        return SimulationResult.builder()
                .initialBalance(initialBalance)
                .baseBetAmount(baseBetAmount)
                .maxRounds(maxRounds)
                .targetBalance(targetBalance)
                .balance(balance)
                .profit(balance - initialBalance)
                .totalProfit(totalProfit)
                .totalLoss(totalLoss)
                .roundsPlayed(round)
                .maxWinStreak(maxWinStreak)
                .maxLossStreak(maxLossStreak)
                .targetReached(targetReached)
                .outOfMoney(outOfMoney)
                .changeBetColorAfterWin(changeBetColorAfterWin)
                .build();
    }

    @SneakyThrows
    public static void analyzeResultsCsv(List<SimulationResult> results) {
        File csvFile = new File("simulation_results.csv");
        if (!csvFile.exists()) {
            csvFile.createNewFile();
        }
        try (Writer writer = new FileWriter(csvFile)) {
            writer.write("InitialBalance,BaseBetAmount,MaxRounds,TargetBalance,Balance,Profit,TotalProfit,TotalLoss,RoundsPlayed,MaxWinStreak,MaxLoseStreak,TargetReached,OutOfMoney,ChangeColorAfterWin\n");

            ColumnPositionMappingStrategy<SimulationResult> strategy = new ColumnPositionMappingStrategy<>();
            strategy.setType(SimulationResult.class);
            strategy.setColumnMapping("initialBalance", "baseBetAmount", "maxRounds", "targetBalance",
                    "balance", "profit", "totalProfit", "totalLoss", "roundsPlayed", "maxWinStreak", "maxLossStreak",
                    "targetReached", "outOfMoney", "changeBetColorAfterWin");

            StatefulBeanToCsv<SimulationResult> beanToCsv = new StatefulBeanToCsvBuilder<SimulationResult>(writer)
                    .withMappingStrategy(strategy)
                    .withQuotechar(CSVWriter.NO_QUOTE_CHARACTER)
                    .build();

            beanToCsv.write(results);
        }

        System.out.println("Simulation results have been written to " + csvFile.getAbsolutePath());
    }
}
