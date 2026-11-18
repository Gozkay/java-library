package com.library.policy;

import java.math.BigDecimal;

public class StaffPolicy implements MembershipPolicy {

    @Override
    public int getLoanLimit() {
        return 6;
    }

    @Override
    public int getRenewalLimit() {
        return 2;
    }

    @Override
    public BigDecimal getDailyFineRate() {
        return new BigDecimal("30.00");
    }

    @Override
    public int getAdditionalLoanPeriodDays() {
        return 7;
    }
}