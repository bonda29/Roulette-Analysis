package com.test.models.enums;

import com.test.models.abstracts.BetOption;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Dozen implements BetOption {
    FIRST_DOZEN(1),
    SECOND_DOZEN(2),
    THIRD_DOZEN(3);

    private final int dozenNumber;
}
