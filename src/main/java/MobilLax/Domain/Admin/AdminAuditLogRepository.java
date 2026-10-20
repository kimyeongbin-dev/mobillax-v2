package MobilLax.Domain.Admin;

import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * 감사 로그 저장소: 추가와 조회만 있다 (보안 규칙 S12)
 * JpaRepository 를 쓰지 않아 delete·saveAll 로 기록을 지우거나 덮을 수 없다 (AdminSecurityRegressionTests 가 검사)
 */
public interface AdminAuditLogRepository extends Repository<AdminAuditLog, Long> {

    AdminAuditLog save(AdminAuditLog log);

    List<AdminAuditLog> findTop100ByOrderByIdDesc();

    List<AdminAuditLog> findTop20ByTargetUserIdOrderByIdDesc(Long targetUserId);
}
