package com.test.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SimulationParameters {
    private static final int BASE_BET_AMOUNT_SCALE = 1000;

    private double baseBetAmount;
    private int maxRounds;
    private boolean changeBetColorAfterWin;

    public static long generateScenarioId(SimulationParameters parameters) {
        // Convert baseBetAmount to an integer to preserve decimal places
        int baseBetAmountInt = (int) Math.round(parameters.getBaseBetAmount() * BASE_BET_AMOUNT_SCALE);

        int changeBetColorAfterWinInt = parameters.isChangeBetColorAfterWin() ? 1 : 0;

        // Pack parameters into a long using bit-shifting
        // Allocate bits:
        // baseBetAmountInt: 20 bits (supports values up to ~1 million when scaled)
        // maxRounds: 30 bits (supports values up to ~1 billion)
        // changeBetColorAfterWinInt: 1 bit
        // Total bits used: 20 + 30 + 1 = 51 bits (fits within long)
        return ((long) baseBetAmountInt << 31) | ((long) parameters.getMaxRounds() << 1) | changeBetColorAfterWinInt;
    }

    public static SimulationParameters getParametersFromScenarioId(long scenarioId) {
        // Extract changeBetColorAfterWinInt (last bit)
        int changeBetColorAfterWinInt = (int) (scenarioId & 0b1);
        boolean changeBetColorAfterWin = changeBetColorAfterWinInt == 1;

        // Shift to get the remaining bits
        long temp = scenarioId >> 1;

        // Extract maxRounds (next 30 bits)
        int maxRounds = (int) (temp & 0x3FFFFFFFL); // Mask for 30 bits

        // Shift to get baseBetAmountInt
        int baseBetAmountInt = (int) (temp >> 30);

        // Convert baseBetAmountInt back to double
        double baseBetAmount = baseBetAmountInt / (double) BASE_BET_AMOUNT_SCALE;

        return SimulationParameters.builder()
                .baseBetAmount(baseBetAmount)
                .maxRounds(maxRounds)
                .changeBetColorAfterWin(changeBetColorAfterWin)
                .build();
    }
}
