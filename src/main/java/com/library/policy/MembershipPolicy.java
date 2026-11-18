package com.library.policy;

import java.math.BigDecimal;

public interface MembershipPolicy {

    int getLoanLimit();

    int getRenewalLimit();

    BigDecimal getDailyFineRate();

    int getAdditionalLoanPeriodDays();
}