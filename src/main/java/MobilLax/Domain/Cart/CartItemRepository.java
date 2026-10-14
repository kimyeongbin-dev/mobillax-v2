package MobilLax.Domain.Cart;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {
    List<CartItemEntity> findByEmail(String email);
    void deleteByEmail(String email); // 실제 삭제

    void deleteByEmailAndTotalFareGroupId(String email, String totalFareGroupId);

    List<CartItemEntity> findByEmailAndTotalFareGroupId(String email, String totalFareGroupId);

    List<CartItemEntity> findByEmailOrderByIdDesc(String email);

    List<CartItemEntity> findByEmailAndTotalFareGroupIdAndIsDeletedFalse(String email, String totalFareGroupId);

    List<CartItemEntity> findByEmailAndIsDeletedFalse(String email);

    List<CartItemEntity> findByEmailAndIsDeletedFalseOrderByIdDesc(String email);

    @Query("SELECT c FROM CartItemEntity c WHERE c.email = :email AND c.totalFareGroupId = :groupId AND c.isDeleted = false")
    List<CartItemEntity> findActiveByGroupId(@Param("email") String email, @Param("groupId") String groupId);

    @Transactional
    @Modifying
    @Query("UPDATE CartItemEntity c SET c.isDeleted = true WHERE c.email = :email AND c.totalFareGroupId = :groupId")
    void markAsDeletedByEmailAndGroupId(@Param("email") String email, @Param("groupId") String groupId);

    @Transactional
    @Modifying
    @Query("UPDATE CartItemEntity c SET c.isDeleted = false WHERE c.email = :email AND c.totalFareGroupId = :groupId AND c.mode = :transport")
    void restoreByEmailAndGroupIdAndTransport(@Param("email") String email, @Param("groupId") String groupId, @Param("transport") String transport);
}
