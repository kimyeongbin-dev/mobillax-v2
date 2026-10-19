package MobilLax.Domain.Cart;

import java.util.List;

/**
 * 장바구니 화면에 보내는 묶음 하나 (v2.6)
 * - 화면에 필요한 값만 서버가 만든다. DB 항목(이메일, 내부 번호, 확인 표시)은 내보내지 않는다 (보안 규칙 S10)
 * - 결제할 수 있는지와 막힌 이유도 서버가 정한다 ({@link CartPayability}, 결제 준비와 같은 규칙)
 *
 * @param groupId     묶음 번호 (삭제·결제 준비에 씀, 소유권은 서버가 다시 확인)
 * @param title       첫 출발지 → 마지막 도착지
 * @param departure   가장 이른 출발 (예: "10월 18일 07:02"), 모르면 빈 문자열
 * @param items       구간 (출발 순)
 * @param payments    결제창 단위 (교통수단마다 한 번)
 * @param totalFare   묶음 합계
 * @param payable     지금 결제할 수 있는지
 * @param blockReason 결제할 수 없는 이유, 결제할 수 있으면 빈 문자열
 */
public record CartGroupView(String groupId, String title, String departure, List<Item> items,
                            List<Payment> payments, int totalFare, boolean payable, String blockReason) {

    /**
     * @param route     서버가 만든 노선 표시 (예: "기차:서울-부산 · KTX 08:00 출발")
     * @param departure 출발 (예: "10월 18일 07:02"), 모르면 빈 문자열
     */
    public record Item(String startName, String endName, String route, int fare, String departure) {
    }

    /** 결제창 한 번: 교통수단 이름과 금액 */
    public record Payment(String transport, int amount) {
    }
}
