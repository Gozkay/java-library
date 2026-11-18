package com.library.service;

import com.library.model.Fine;
import java.math.BigDecimal;
import java.util.List;

public interface FineServiceApi {
    Fine payFine(String fineId, BigDecimal amount);
    List<Fine> getMemberFines(String memberId);
    BigDecimal getUnpaidTotal(String memberId);
}
