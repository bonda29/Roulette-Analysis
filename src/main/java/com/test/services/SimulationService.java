package com.test.services;

import com.test.models.Bet;
import com.test.models.RouletteNumber;
import com.test.models.SimulationParameters;
import com.test.models.SimulationResult;
import com.test.models.enums.BetType;
import com.test.models.enums.Color;
import com.test.utils.RouletteUtils;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

import static com.test.models.SimulationParameters.generateScenarioId;
import static com.test.models.enums.Color.BLACK;
import static com.test.models.enums.Color.RED;
import static com.test.utils.RouletteUtils.*;
import static com.test.utils.RouletteUtils.calculateInitialBalance;

@Slf4j
@Builder
public class SimulationService {
    private final SimulationParameters parameters;

    public SimulationResult runSimulation() {
        RouletteService rouletteService = RouletteService.builder()
                .wheel(RouletteService.createWheel())
                .random(ThreadLocalRandom.current())
                .build();

        Color betColor = BLACK;

        double initialBalance = calculateInitialBalance(parameters.getBaseBetAmount());
        double balance = initialBalance;

        double estimatedProfit = estimatedProfit(initialBalance, parameters.getBaseBetAmount(), parameters.getMaxRounds());

        double betAmount = parameters.getBaseBetAmount();

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
                betAmount = parameters.getBaseBetAmount();

                if (parameters.isChangeBetColorAfterWin()) {
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
            if (roundsPlayed > parameters.getMaxRounds() && betAmount == parameters.getBaseBetAmount()) {
                break;
            }

            roundsPlayed++;
        }

        return SimulationResult.builder()
                .initialBalance(initialBalance)
                .baseBetAmount(parameters.getBaseBetAmount())
                .maxRounds(parameters.getMaxRounds())
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
                .changeBetColorAfterWin(parameters.isChangeBetColorAfterWin())
                .scenarioId(generateScenarioId(parameters))
                .build();
    }
}
