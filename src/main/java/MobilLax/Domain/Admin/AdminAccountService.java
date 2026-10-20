package MobilLax.Domain.Admin;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Time.KoreaTime;
import MobilLax.Domain.Account.AccountRoles;
import MobilLax.Domain.Account.PasswordAttemptGuard;
import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import MobilLax.Domain.Payment.PaymentHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 관리자 계정 관리 (v2.11, 보안 규칙 S12)
 * - 화면에는 가린 이메일·이름만 내보낸다 (AdminMasking)
 * - 정지·해제·잠금 풀기는 지금 OTP 번호로 다시 확인하고(stepUp), 모두 감사 로그에 남긴다
 * - 관리자 계정은 정지할 수 없다 (관리자를 바꾸려면 ADMIN_EMAIL 을 바꾼다)
 * - 정지하면 그 계정의 로그인 세션을 바로 끝낸다 (다음 요청부터 로그인 화면으로)
 */
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    static final int PAGE_SIZE = 20;

    private final UserAccountRepository userAccountRepository;
    private final PaymentHistoryService paymentHistoryService;
    private final PasswordAttemptGuard attemptGuard;
    private final AdminOtpService otpService;
    private final AdminAuditService audit;
    private final SessionRegistry sessionRegistry;

    public record UserRow(Long id, String email, String name, boolean admin, boolean suspended, boolean loginLocked,
                          LocalDateTime joinedAt) {
    }

    public record UserDetail(UserRow user, List<PaymentHistoryService.AdminPaymentItem> payments,
                             List<AdminAuditLog> history) {
    }

    public record Summary(long users, long suspended) {
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        return new Summary(userAccountRepository.count(), userAccountRepository.countBySuspendedAtIsNotNull());
    }

    @Transactional(readOnly = true)
    public Page<UserRow> search(String adminEmail, String query, int page, String clientIp) {
        String q = query == null ? "" : query.trim();
        audit.record(adminEmail, AdminAuditLog.Action.USER_LIST_VIEWED, null, q.isEmpty() ? null : "검색어 길이 " + q.length(), clientIp);
        return userAccountRepository.findByEmailContainingIgnoreCaseOrderByIdDesc(q, PageRequest.of(Math.max(page, 0), PAGE_SIZE))
                .map(this::row);
    }

    @Transactional(readOnly = true)
    public UserDetail detail(String adminEmail, Long userId, String clientIp) {
        UserAccount account = find(userId);
        audit.record(adminEmail, AdminAuditLog.Action.USER_DETAIL_VIEWED, userId, null, clientIp);
        return new UserDetail(row(account), paymentHistoryService.getAllForAdmin(account.getEmail()), audit.forUser(userId));
    }

    @Transactional
    public void suspend(String adminEmail, Long userId, String otpCode, String clientIp) {
        UserAccount account = find(userId);
        if (AccountRoles.ADMIN.equals(account.getRole())) {
            throw new UserFacingException("관리자 계정은 정지할 수 없습니다.");
        }
        otpService.stepUp(adminEmail, otpCode, userId, clientIp);
        if (account.getSuspendedAt() == null) {
            account.setSuspendedAt(KoreaTime.now());
            userAccountRepository.save(account);
        }
        expireSessions(account.getEmail());
        audit.record(adminEmail, AdminAuditLog.Action.USER_SUSPENDED, userId, null, clientIp);
    }

    @Transactional
    public void unsuspend(String adminEmail, Long userId, String otpCode, String clientIp) {
        UserAccount account = find(userId);
        otpService.stepUp(adminEmail, otpCode, userId, clientIp);
        account.setSuspendedAt(null);
        userAccountRepository.save(account);
        audit.record(adminEmail, AdminAuditLog.Action.USER_UNSUSPENDED, userId, null, clientIp);
    }

    @Transactional
    public void unlockLogin(String adminEmail, Long userId, String otpCode, String clientIp) {
        UserAccount account = find(userId);
        otpService.stepUp(adminEmail, otpCode, userId, clientIp);
        attemptGuard.unlock(account.getEmail());
        audit.record(adminEmail, AdminAuditLog.Action.LOGIN_UNLOCKED, userId, null, clientIp);
    }

    private UserAccount find(Long userId) {
        return userAccountRepository.findById(userId).orElseThrow(NoSuchElementException::new);
    }

    private UserRow row(UserAccount account) {
        return new UserRow(account.getId(), AdminMasking.email(account.getEmail()), AdminMasking.name(account.getName()),
                AccountRoles.ADMIN.equals(account.getRole()), account.getSuspendedAt() != null,
                attemptGuard.loginLocked(account.getEmail()), account.getCreatedAt());
    }

    /** 그 계정으로 열린 세션을 모두 끝낸다 (SecurityConfig 의 세션 등록부가 다음 요청에서 로그인 화면으로 보낸다) */
    private void expireSessions(String email) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserDetails user && user.getUsername().equals(email))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }
}
