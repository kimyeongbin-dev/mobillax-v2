package MobilLax.Domain.Admin;

import MobilLax.Domain.Account.AccountRoles;
import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 지정 (v2.11, 보안 규칙 S12): 앱이 뜰 때 ADMIN_EMAIL 계정만 관리자로 둔다
 * - 화면이나 API 로는 관리자를 만들 수 없다. 서버 설정(.env)을 바꿀 수 있는 사람만 관리자를 정한다
 * - 지정한 계정은 먼저 일반 회원가입으로 만들어 둔다. 없으면 경고만 남긴다
 * - 다른 관리자 계정은 일반 회원으로 내린다 (관리자는 한 명)
 * - 테스트에서는 끈다 (admin.bootstrap.enabled=false, build.gradle)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class AdminBootstrap implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;

    @Value("${admin.email:}")
    private String adminEmail;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        apply(adminEmail == null ? "" : adminEmail.trim());
    }

    void apply(String email) {
        for (UserAccount other : userAccountRepository.findByRole(AccountRoles.ADMIN)) {
            if (!other.getEmail().equalsIgnoreCase(email)) {
                other.setRole(AccountRoles.USER);
                userAccountRepository.save(other);
                log.warn("관리자 지정 해제: 계정 ID {}", other.getId());
            }
        }
        if (email.isEmpty()) {
            log.info("ADMIN_EMAIL 이 비어 있어 관리자가 없습니다");
            return;
        }
        userAccountRepository.findByEmail(email).ifPresentOrElse(account -> {
            if (!AccountRoles.ADMIN.equals(account.getRole())) {
                account.setRole(AccountRoles.ADMIN);
                userAccountRepository.save(account);
            }
            log.info("관리자 계정: ID {}", account.getId());
        }, () -> log.warn("ADMIN_EMAIL 계정이 없습니다. 그 이메일로 회원가입한 뒤 앱을 다시 띄워 주세요"));
    }
}
