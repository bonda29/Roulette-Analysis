package com.test.models;

import com.test.models.abstracts.BetOption;
import com.test.models.enums.BetType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Bet {
    private BetType type;
    private int amount;
    private BetOption bet;
}
