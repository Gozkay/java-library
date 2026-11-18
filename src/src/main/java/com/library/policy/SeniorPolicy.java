package com.library.policy;

import java.math.BigDecimal;

public class SeniorPolicy implements MembershipPolicy {

    @Override
    public int getLoanLimit() {
        return 4;
    }

    @Override
    public int getRenewalLimit() {
        return 2;
    }

    @Override
    public BigDecimal getDailyFineRate() {
        return new BigDecimal("0.00");
    }

    @Override
    public int getAdditionalLoanPeriodDays() {
        return 7;
    }
}