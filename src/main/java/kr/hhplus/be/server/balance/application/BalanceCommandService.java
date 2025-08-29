package kr.hhplus.be.server.balance.application;

import kr.hhplus.be.server.balance.domain.Balance;
import kr.hhplus.be.server.balance.domain.BalanceRepository;
import kr.hhplus.be.server.common.event.balance.BalanceDeductedEvent;
import kr.hhplus.be.server.common.event.balance.BalanceDeductionFailedEvent;
import kr.hhplus.be.server.common.lock.DistributedLock;
import kr.hhplus.be.server.common.lock.LockKey;
import kr.hhplus.be.server.saga.domain.OrderSagaItem;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BalanceCommandService {

    private final BalanceRepository balanceRepository;
    private final ApplicationEventPublisher publisher;

    @Transactional
    @DistributedLock(prefix = LockKey.BALANCE, ids = "#userId")
    public void deductBalance(Long orderId, Long userId, int amount, List<OrderSagaItem> items, Long couponId) {
        Balance balance = balanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("잔액 정보를 찾을 수 없습니다. userId=" + userId));

        try {
            balance.use(amount);
            balanceRepository.save(balance);

            publisher.publishEvent(new BalanceDeductedEvent(orderId, amount));

        } catch (Exception e) {
            publisher.publishEvent(new BalanceDeductionFailedEvent(orderId, items, couponId, e.getMessage()));
        }
    }

    @Transactional
    @DistributedLock(prefix = LockKey.BALANCE, ids = "#userId")
    public void restoreBalance(Long userId, int amount) {
        Balance balance = balanceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("잔액 정보가 없습니다. userId=" + userId));

        balance.restore(amount);
        balanceRepository.save(balance);
    }
}
