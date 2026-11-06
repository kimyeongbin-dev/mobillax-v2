package MobilLax.Domain.Account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface KeepLoginTokenRepository extends JpaRepository<KeepLoginToken, String> {

    /** 지금 토큰이 그대로일 때만 바꾼다. 동시 요청 중 하나만 성공한다 (0 이면 다른 요청이 먼저 바꿨다) */
    @Modifying(clearAutomatically = true)
    @Query("update KeepLoginToken t set t.previousHash = t.tokenHash, t.tokenHash = :next, t.lastUsedAt = :now "
            + "where t.series = :series and t.tokenHash = :current")
    int rotate(@Param("series") String series, @Param("current") String current, @Param("next") String next,
               @Param("now") LocalDateTime now);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("delete from KeepLoginToken t where t.lastUsedAt < :idleBefore or t.createdAt < :createdBefore")
    void deleteExpired(@Param("idleBefore") LocalDateTime idleBefore, @Param("createdBefore") LocalDateTime createdBefore);
}
