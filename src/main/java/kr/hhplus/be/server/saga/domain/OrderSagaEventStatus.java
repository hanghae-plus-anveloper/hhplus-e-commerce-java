package kr.hhplus.be.server.saga.domain;

public enum OrderSagaEventStatus {
    PENDING, // 대기 중
    SUCCESS, // 성공 처리됨
    FAILED, // 실패 처리됨
    CANCELED, // 취소됨
    RESTORED // 복구 완료
}
