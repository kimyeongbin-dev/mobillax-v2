package MobilLax.Domain.Account;

import MobilLax.Common.Usage.DailyCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

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

    /** 통계: 날짜별 가입 수 (v2.13). 탈퇴하면 행이 지워지므로 남아 있는 계정만 센다 */
    @Query("select cast(u.createdAt as LocalDate) as statDate, count(u) as total from UserAccount u "
            + "where u.createdAt >= :from and u.createdAt < :to group by cast(u.createdAt as LocalDate)")
    List<DailyCount> countSignupsByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
