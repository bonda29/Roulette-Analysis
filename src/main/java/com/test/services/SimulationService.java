package com.test.services;

import com.test.models.Bet;
import com.test.models.RouletteNumber;
import com.test.models.SimulationResult;
import com.test.models.enums.BetType;
import com.test.models.enums.Color;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadLocalRandom;

import static com.test.models.enums.Color.BLACK;
import static com.test.models.enums.Color.RED;

@Slf4j
@Builder
public class SimulationService {

    private double initialBalance;
    private double baseBetAmount;
    private double estimatedProfit;
    private int maxRounds;
    private boolean changeBetColorAfterWin;
    private int scenarioId;

    public SimulationResult runSimulation() {
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
                break;
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
}
