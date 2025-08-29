package kr.hhplus.be.server.analytics.application;

import kr.hhplus.be.server.analytics.infrastructure.TopProductRecord;
import kr.hhplus.be.server.common.event.order.OrderLineSummary;

public class TopProductMapper {
    public static TopProductRecord toRecord(OrderLineSummary line) {
        String productId = (line.productId() == null) ? null : String.valueOf(line.productId());
        return new TopProductRecord(productId, line.quantity());
    }
}
