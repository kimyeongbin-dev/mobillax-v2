package MobilLax.Domain.Cart;

import MobilLax.Domain.Trip.TripDisplay;
import MobilLax.Domain.Trip.TripVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final TripVerifier tripVerifier;

    /**
     * 사용자가 선택한 구간들을 장바구니에 저장
     * - 운임은 서버가 TAGO 에서 다시 확인한 값만 저장한다 (보안 규칙 S1). 하나라도 확인되지 않으면 아무것도 담지 않는다
     * - 역·터미널 이름과 노선 표시도 TAGO 값으로 서버가 만든다. 브라우저가 보낸 표시 문자열은 저장하지 않는다 (S10)
     * @param legs 선택된 경로 리스트
     * @return 총 결제 금액
     */
    public int saveCartItems(List<CartItemRequestDTO.LegDTO> legs) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();  // 현재 로그인된 사용자 이메일 획득
        String groupId = UUID.randomUUID().toString();  // 새 그룹 ID 생성
        int totalFare = 0;

        // 모든 구간을 먼저 확인하고, 하나라도 실패하면 아무것도 담지 않는다 (요청 형식은 @Valid 가 먼저 확인)
        List<TripVerifier.VerifiedTrip> verified = legs.stream().map(tripVerifier::verify).toList();

        for (int i = 0; i < legs.size(); i++) {
            CartItemRequestDTO.LegDTO leg = legs.get(i);
            TripVerifier.VerifiedTrip trip = verified.get(i);
            CartItemEntity item = CartItemEntity.builder()
                    .email(email)
                    .mode(leg.getMode())
                    .route(trip.route())
                    .routeId(leg.getRouteId())
                    .routePayment(trip.fare())
                    .startName(trip.startName())
                    .endName(trip.endName())
                    .departureTime(leg.getDepartureTime())
                    .totalFareGroupId(groupId)
                    .isDeleted(false)  // 신규 저장 시 isDeleted는 false
                    .fareVerified(true)
                    .build();

            cartItemRepository.save(item);
            totalFare += trip.fare();
        }

        return totalFare;
    }

    /**
     * 로그인한 사용자의 장바구니 화면 (v2.6, 보안 규칙 S4: 내 활성 항목만)
     * - 결제할 수 있는 묶음을 먼저, 그 안에서는 출발이 이른 순
     */
    @Transactional(readOnly = true)
    public List<CartGroupView> getCartView() {
        String email = getCurrentUserEmail();
        String now = DepartureCheck.now();
        Map<String, List<CartItemEntity>> groups = cartItemRepository.findByEmailAndIsDeletedFalse(email).stream()
                .sorted(ITEM_ORDER)
                .collect(Collectors.groupingBy(CartItemEntity::getTotalFareGroupId, LinkedHashMap::new, Collectors.toList()));

        return groups.entrySet().stream()
                .map(e -> toView(e.getKey(), e.getValue(), now))
                .sorted(Comparator.comparing((CartGroupView v) -> !v.payable())
                        .thenComparing(v -> firstDeparture(groups.get(v.groupId()))))
                .toList();
    }

    /** 출발 순, 출발 일시를 모르는 예전 항목은 담은 순으로 뒤에 */
    private static final Comparator<CartItemEntity> ITEM_ORDER = Comparator
            .comparing((CartItemEntity i) -> i.getDepartureTime() == null ? "~" : i.getDepartureTime())
            .thenComparing(CartItemEntity::getId);

    private static String firstDeparture(List<CartItemEntity> items) {
        return items.get(0).getDepartureTime() == null ? "" : items.get(0).getDepartureTime();
    }

    private static CartGroupView toView(String groupId, List<CartItemEntity> items, String now) {
        CartItemEntity first = items.get(0);
        CartItemEntity last = items.get(items.size() - 1);

        // 결제 준비(PaymentController)와 같이 교통수단마다 결제창을 한 번씩 연다
        Map<String, Integer> byTransport = items.stream().collect(Collectors.groupingBy(
                CartItemEntity::getMode, LinkedHashMap::new, Collectors.summingInt(CartItemEntity::getRoutePayment)));
        List<CartGroupView.Payment> payments = byTransport.entrySet().stream()
                .map(e -> new CartGroupView.Payment(TripDisplay.transportLabel(e.getKey()), e.getValue()))
                .toList();

        Optional<String> blocked = CartPayability.blockReason(items, now);
        return new CartGroupView(
                groupId,
                first.getStartName() + " → " + last.getEndName(),
                TripDisplay.departure(first.getDepartureTime()),
                items.stream().map(i -> new CartGroupView.Item(i.getStartName(), i.getEndName(), i.getRoute(),
                        i.getRoutePayment(), TripDisplay.departure(i.getDepartureTime()))).toList(),
                payments,
                items.stream().mapToInt(CartItemEntity::getRoutePayment).sum(),
                blocked.isEmpty(),
                blocked.orElse(""));
    }

    /**
     * 로그인된 사용자의 모든 장바구니 항목 논리 삭제 처리
     */
    public void clearCart() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        // 실제 삭제가 아닌 isDeleted 플래그 처리 필요하면 메서드 변경 필요
        cartItemRepository.deleteByEmail(email);
    }

    /**
     * 특정 그룹 단위로 논리 삭제 처리 (isDeleted = true)
     * @param groupId 삭제할 그룹 UUID
     */
    public void deleteGroup(String groupId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        cartItemRepository.markAsDeletedByEmailAndGroupId(email, groupId);
    }

    /**
     * 최근 장바구니 경로 요약 반환 (시작지 → 종착지)
     */
    public String getRecentRouteSummary(String email) {
        List<CartItemEntity> items = cartItemRepository.findByEmailOrderByIdDesc(email);
        if (items.isEmpty()) return "없음";

        String latestGroupId = items.get(0).getTotalFareGroupId();
        List<CartItemEntity> latestGroup = items.stream()
                .filter(i -> latestGroupId.equals(i.getTotalFareGroupId()))
                .sorted((a, b) -> a.getId().compareTo(b.getId())) // ID 기준 오름차순 정렬
                .toList();

        if (latestGroup.isEmpty()) return "없음";

        String start = latestGroup.get(0).getStartName();
        String end = latestGroup.get(latestGroup.size() - 1).getEndName();
        return start + " → " + end;
    }

    /**
     * 로그인된 사용자의 장바구니 그룹 수 반환
     */
    public int getCartGroupCount(String email) {
        // 삭제했거나 결제한(isDeleted) 묶음은 세지 않는다
        List<CartItemEntity> items = cartItemRepository.findByEmailAndIsDeletedFalse(email);
        return (int) items.stream()
                .map(CartItemEntity::getTotalFareGroupId)
                .distinct()
                .count();
    }

    /**
     * 인증된 사용자 이메일 조회
     */
    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("인증되지 않은 사용자입니다.");
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else {
            return principal.toString();
        }
    }
}
