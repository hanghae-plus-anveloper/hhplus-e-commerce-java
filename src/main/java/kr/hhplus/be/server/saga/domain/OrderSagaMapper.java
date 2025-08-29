package kr.hhplus.be.server.saga.domain;

import kr.hhplus.be.server.order.facade.OrderItemCommand;

import java.util.List;
import java.util.stream.Collectors;

public class OrderSagaMapper {

    public static List<OrderSagaItem> toSagaItems(List<OrderItemCommand> commands) {
        if (commands == null) return List.of();
        return commands.stream()
                .map(cmd -> new OrderSagaItem(cmd.getProductId(), cmd.getQuantity()))
                .collect(Collectors.toList());
    }
}
