package MobilLax.Common.Usage;

/** 날마다 세는 이용 항목 (v2.13, 보안 규칙 S13). 가입·결제·문의는 각 테이블에 이미 있어 여기서 세지 않는다 */
public enum UsageMetric {
    VISITOR,
    PAGE_VIEW,
    ROUTE_SEARCH,
    NEARBY_SEARCH,
    CART_ADD
}
