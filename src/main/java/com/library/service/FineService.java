package com.library.service;
import com.library.model.Fine;
import java.math.BigDecimal;
import java.util.List;

public interface FineService {
    List<Fine> getMemberFines(String memberId);
    void recordPayment(String memberId, BigDecimal amount);
}
