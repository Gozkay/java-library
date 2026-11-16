package com.library.policy;
import java.math.BigDecimal;

public interface MembershipPolicy {
    int getMaxItems();
    int getLoanExtensionDays();
    BigDecimal getFinePerDay();
    int getMaxRenewals();
}
