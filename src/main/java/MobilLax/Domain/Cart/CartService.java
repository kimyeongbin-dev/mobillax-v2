package MobilLax.Domain.Cart;

import MobilLax.Domain.Trip.TripVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
     * @param legs 선택된 경로 리스트
     * @return 총 결제 금액
     */
    public int saveCartItems(List<CartItemRequestDTO.LegDTO> legs) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();  // 현재 로그인된 사용자 이메일 획득
        String groupId = UUID.randomUUID().toString();  // 새 그룹 ID 생성
        int totalFare = 0;

        // 요금이 없는 구간(도보 등)은 담지 않고, 나머지는 모두 운임을 확인한 뒤에 저장
        List<CartItemRequestDTO.LegDTO> paidLegs = legs.stream().filter(leg -> leg.getRoutePayment() > 0).toList();
        if (paidLegs.isEmpty()) {
            throw new IllegalArgumentException("요금이 있는 구간만 담을 수 있습니다.");
        }
        paidLegs.forEach(tripVerifier::verifiedFare);

        for (CartItemRequestDTO.LegDTO leg : paidLegs) {
            CartItemEntity item = CartItemEntity.builder()
                    .email(email)
                    .mode(leg.getMode())
                    .route(leg.getRoute())
                    .routeId(leg.getRouteId())
                    .routePayment(leg.getRoutePayment())
                    .startName(leg.getStartName())
                    .endName(leg.getEndName())
                    .departureTime(leg.getDepartureTime())
                    .totalFareGroupId(groupId)
                    .isDeleted(false)  // 신규 저장 시 isDeleted는 false
                    .fareVerified(true)
                    .build();

            cartItemRepository.save(item);
            totalFare += leg.getRoutePayment();
        }

        return totalFare;
    }

    /**
     * 로그인된 사용자의 삭제되지 않은 장바구니 항목을 그룹별로 묶어 반환
     * @return Map<그룹ID, 장바구니 항목 리스트>
     */
    public Map<String, List<CartItemEntity>> getGroupedCartItems() {
        String email = getCurrentUserEmail();

        // isDeleted=false 조건으로 활성 상태인 항목만 조회
        List<CartItemEntity> allItems = cartItemRepository.findByEmailAndIsDeletedFalse(email);

        return allItems.stream()
                .collect(Collectors.groupingBy(CartItemEntity::getTotalFareGroupId));
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
