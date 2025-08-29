package kr.hhplus.be.server.balance.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import kr.hhplus.be.server.user.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "balance")
public class Balance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private int balance;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "balance", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<BalanceHistory> histories = new ArrayList<>();

    protected Balance() {
    }

    @Builder
    public Balance(User user, int balance) {
        this.user = user;
        this.balance = balance;
        this.updatedAt = LocalDateTime.now();
    }

    public void charge(int amount) {
        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
        this.histories.add(BalanceHistory.charge(this, amount, this.balance));
    }

    public void use(int amount) {
        if (amount > balance)
            throw new IllegalArgumentException("잔액 부족");
        this.balance -= amount;
        this.updatedAt = LocalDateTime.now();
        this.histories.add(BalanceHistory.use(this, amount, this.balance));
    }

    public void restore(int amount) {
        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
        this.histories.add(BalanceHistory.restore(this, amount, this.balance));
    }
}
