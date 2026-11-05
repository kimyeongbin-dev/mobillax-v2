package MobilLax.Domain.Admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 감사 로그 저장소: 추가와 조회만 있다 (보안 규칙 S12)
 * JpaRepository 를 쓰지 않아 delete·saveAll 로 기록을 지우거나 덮을 수 없다 (AdminSecurityRegressionTests 가 검사)
 */
public interface AdminAuditLogRepository extends Repository<AdminAuditLog, Long> {

    AdminAuditLog save(AdminAuditLog log);

    List<AdminAuditLog> findTop100ByOrderByIdDesc();

    List<AdminAuditLog> findTop20ByTargetUserIdOrderByIdDesc(Long targetUserId);

    /** 점검 화면: 기간 안에서 일 종류와 대상 계정으로 좁힌다 (둘 다 없으면 기간 전체), 최근 순 (v2.13) */
    @Query("select a from AdminAuditLog a where a.createdAt >= :from and a.createdAt < :to "
            + "and (:action is null or a.action = :action) and (:targetUserId is null or a.targetUserId = :targetUserId) "
            + "order by a.id desc")
    Page<AdminAuditLog> search(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                               @Param("action") AdminAuditLog.Action action, @Param("targetUserId") Long targetUserId,
                               Pageable pageable);

    /** 점검 화면: 기간 안의 일 종류별 건수 (v2.13) */
    @Query("select a.action as action, count(a) as total from AdminAuditLog a "
            + "where a.createdAt >= :from and a.createdAt < :to group by a.action")
    List<ActionTotal> countByAction(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    boolean existsByActionAndDetail(AdminAuditLog.Action action, String detail);

    interface ActionTotal {
        AdminAuditLog.Action getAction();

        long getTotal();
    }
}
