package MobilLax.Domain.Account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    void deleteByEmail(String email);

    /** 관리자 지정 (AdminBootstrap) */
    List<UserAccount> findByRole(String role);

    /** 관리자 계정 관리: 이메일 일부로 검색, 최근 가입 순 (v2.11) */
    Page<UserAccount> findByEmailContainingIgnoreCaseOrderByIdDesc(String email, Pageable pageable);

    long countBySuspendedAtIsNotNull();
}
