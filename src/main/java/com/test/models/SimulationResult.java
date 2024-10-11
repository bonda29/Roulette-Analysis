package com.test.models;

import com.test.models.enums.Color;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SimulationResult {
    private double initialBalance;
    private double baseBetAmount;
    private double targetProfit;
    private double stopLossLimit;
    private double maxBetPercentage;
    private int maxRounds;
    private Color betColor;

    private double balance;
    private double totalProfit;
    private double totalLoss;
    private int roundsPlayed;
    private boolean targetReached;
    private boolean stopLossReached;
    private boolean outOfMoney;

    public String getParameterCombinationKey() {
        return "BaseBet: $" + baseBetAmount +
                ", TargetProfit: $" + targetProfit +
                ", StopLoss: $" + stopLossLimit +
                ", MaxBet%: " + (maxBetPercentage * 100) + "%" +
                ", MaxRounds: " + maxRounds +
                ", BetColor: " + betColor;
    }
}