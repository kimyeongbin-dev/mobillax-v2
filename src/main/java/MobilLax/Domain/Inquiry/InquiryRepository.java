package MobilLax.Domain.Inquiry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
