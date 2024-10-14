package com.test.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SimulationResult {
    private double initialBalance;
    private double baseBetAmount;

    private int maxRounds;

    private double estimatedProfit;

    private double balance;
    private double profit;
    private double totalProfit;
    private double totalLoss;
    private int roundsPlayed;

    private int maxWinStreak;
    private int maxLossStreak;

    private boolean targetReached;
    private boolean outOfMoney;
    private boolean changeBetColorAfterWin;

    private int scenarioId;
}