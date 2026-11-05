package MobilLax.Domain.Inquiry;

import MobilLax.Common.Usage.DailyCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findByUserIdOrderByIdDesc(Long userId);

    /** 본인 문의만 (남의 문의 번호를 넣으면 없음, 보안 규칙 S4) */
    Optional<Inquiry> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatusAndAnswerReadAtIsNull(Long userId, Inquiry.Status status);

    long countByStatus(Inquiry.Status status);

    /** 관리자: 답변 대기는 오래된 것부터 (먼저 온 문의에 먼저 답하게) */
    Page<Inquiry> findByStatusOrderByIdAsc(Inquiry.Status status, Pageable pageable);

    /** 관리자: 답변 완료는 최근 것부터 */
    Page<Inquiry> findByStatusOrderByIdDesc(Inquiry.Status status, Pageable pageable);

    /** 통계: 날짜별 받은 문의 수 (v2.13) */
    @Query("select cast(q.createdAt as LocalDate) as statDate, count(q) as total from Inquiry q "
            + "where q.createdAt >= :from and q.createdAt < :to group by cast(q.createdAt as LocalDate)")
    List<DailyCount> countByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
