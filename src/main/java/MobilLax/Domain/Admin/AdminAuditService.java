package MobilLax.Domain.Admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 감사 로그 (보안 규칙 S12)
 * - 실패한 시도(OTP 실패, 재확인 실패)도 남긴다. 그래서 바깥 트랜잭션이 되돌려져도 남도록 따로 저장한다
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private static final int DETAIL_MAX = 200;

    private final AdminAuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String adminEmail, AdminAuditLog.Action action, Long targetUserId, String detail, String clientIp) {
        String safeDetail = detail == null || detail.length() <= DETAIL_MAX ? detail : detail.substring(0, DETAIL_MAX);
        repository.save(new AdminAuditLog(adminEmail, action, targetUserId, safeDetail, clientIp));
    }

    public List<AdminAuditLog> recent() {
        return repository.findTop100ByOrderByIdDesc();
    }

    public List<AdminAuditLog> forUser(Long userId) {
        return repository.findTop20ByTargetUserIdOrderByIdDesc(userId);
    }
}
