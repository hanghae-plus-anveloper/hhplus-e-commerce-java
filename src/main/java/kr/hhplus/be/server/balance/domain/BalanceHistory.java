package kr.hhplus.be.server.balance.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "balance_history", indexes = {
        @Index(name = "idx_balance_history_timestamp", columnList = "timestamp"), // 검색 및 조회 목적
        @Index(name = "idx_balance_history_type", columnList = "type") // 필터 목적
})
public class BalanceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "balance_id", nullable = false)
    @JsonBackReference
    private Balance balance;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private int remainingBalance;

    @Column(nullable = false)
    private BalanceChangeType type;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    protected BalanceHistory() {
    }

    public BalanceHistory(Balance balance, int amount, int remainingBalance, BalanceChangeType type) {
        this.balance = balance;
        this.amount = amount;
        this.remainingBalance = remainingBalance;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }

    public static BalanceHistory charge(Balance balance, int amount, int remainingBalance) {
        return new BalanceHistory(balance, amount, remainingBalance, BalanceChangeType.CHARGE);
    }

    public static BalanceHistory use(Balance balance, int amount, int remainingBalance) {
        return new BalanceHistory(balance, -amount, remainingBalance, BalanceChangeType.USE);
    }
    public static BalanceHistory restore(Balance balance, int amount, int afterBalance) {
        return new BalanceHistory(balance, amount, afterBalance, BalanceChangeType.RESTORE);
    }
}
