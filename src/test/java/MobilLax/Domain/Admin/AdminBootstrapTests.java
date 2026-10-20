package MobilLax.Domain.Admin;

import MobilLax.Domain.Account.AccountRoles;
import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** 관리자 지정: ADMIN_EMAIL 계정만 관리자로, 나머지 관리자는 일반 회원으로 (보안 규칙 S12) */
@SpringBootTest
@Transactional
class AdminBootstrapTests {

    @Autowired
    UserAccountRepository repository;

    private UserAccount account(String email, String role) {
        UserAccount account = new UserAccount();
        account.setEmail(email);
        account.setName("지정검사");
        account.setPassword("FAKE_hash");
        account.setRole(role);
        return repository.save(account);
    }

    private String role(String email) {
        return repository.findByEmail(email).orElseThrow().getRole();
    }

    @Test
    void onlyTheConfiguredEmailIsAdmin() {
        account("old-admin@test.local", AccountRoles.ADMIN);
        account("new-admin@test.local", AccountRoles.USER);

        new AdminBootstrap(repository).apply("new-admin@test.local");

        assertThat(role("new-admin@test.local")).isEqualTo(AccountRoles.ADMIN);
        assertThat(role("old-admin@test.local")).isEqualTo(AccountRoles.USER);
    }

    @Test
    void emptyOrUnknownEmailLeavesNoAdmin() {
        account("old-admin2@test.local", AccountRoles.ADMIN);

        new AdminBootstrap(repository).apply("nobody@test.local");
        assertThat(role("old-admin2@test.local")).isEqualTo(AccountRoles.USER);

        account("old-admin3@test.local", AccountRoles.ADMIN);
        new AdminBootstrap(repository).apply("");
        assertThat(role("old-admin3@test.local")).isEqualTo(AccountRoles.USER);
    }
}
