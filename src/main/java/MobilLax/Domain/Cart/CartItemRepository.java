package MobilLax.Domain.Cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 장바구니 행 (v2.10: 행을 지우지 않는다)
 * - 장바구니에서 빠진 항목(사용자가 삭제·비우기, 결제 완료)은 isDeleted 로만 표시하고 화면·결제에서 뺀다
 * - 행은 남아 마이페이지 "최근 이용 경로"와 문의 대응에 쓴다. 보존 기간이 지난 행 정리는 배포(v3) 때 정한다
 */
public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {

    /** 최근 이용 경로: 장바구니에서 빠진 항목까지 최신순 */
    List<CartItemEntity> findByEmailOrderByIdDesc(String email);

    /** 장바구니에 있는 항목 */
    List<CartItemEntity> findByEmailAndIsDeletedFalse(String email);

    @Query("SELECT c FROM CartItemEntity c WHERE c.email = :email AND c.totalFareGroupId = :groupId AND c.isDeleted = false")
    List<CartItemEntity> findActiveByGroupId(@Param("email") String email, @Param("groupId") String groupId);

    /** 묶음 하나를 장바구니에서 뺀다 (삭제, 결제 완료) */
    @Transactional
    @Modifying
    @Query("UPDATE CartItemEntity c SET c.isDeleted = true WHERE c.email = :email AND c.totalFareGroupId = :groupId")
    void markAsDeletedByEmailAndGroupId(@Param("email") String email, @Param("groupId") String groupId);

    /** 장바구니 전체를 뺀다 (전체 비우기) */
    @Transactional
    @Modifying
    @Query("UPDATE CartItemEntity c SET c.isDeleted = true WHERE c.email = :email AND c.isDeleted = false")
    void markAllAsDeletedByEmail(@Param("email") String email);
}
