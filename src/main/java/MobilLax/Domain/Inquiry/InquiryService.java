package MobilLax.Domain.Inquiry;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.RateLimit.ClientKey;
import MobilLax.Common.RateLimit.RateLimitPolicy;
import MobilLax.Common.RateLimit.RateLimiter;
import MobilLax.Domain.Account.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 1:1 문의 (v2.12)
 * - 사용자 쪽: 본인 문의만 쓰고 본다 (계정 ID 로 묶음, 보안 규칙 S4). 등록은 횟수를 제한한다
 * - 관리자 쪽 기능(목록·답변)은 여기서 문의만 다루고, OTP 재확인과 감사 로그는 AdminInquiryService 가 맡는다
 */
@Service
@RequiredArgsConstructor
public class InquiryService {

    static final int ADMIN_PAGE_SIZE = 20;

    private final InquiryRepository inquiryRepository;
    private final UserAccountService userAccountService;
    private final RateLimiter rateLimiter;

    @Transactional(readOnly = true)
    public List<Inquiry> listMine(String email) {
        return inquiryRepository.findByUserIdOrderByIdDesc(userAccountService.idOf(email));
    }

    @Transactional
    public Long create(String email, InquiryForm form) {
        RateLimiter.Decision decision = rateLimiter.acquire(RateLimitPolicy.INQUIRY, ClientKey.member(email));
        if (!decision.allowed()) {
            throw new UserFacingException(RateLimitPolicy.INQUIRY.message(decision, true));
        }
        Inquiry inquiry = Inquiry.open(userAccountService.idOf(email), form.getCategory(),
                form.getTitle().trim(), form.getContent().strip());
        return inquiryRepository.save(inquiry).getId();
    }

    /** 본인 문의 하나. 답변이 달려 있으면 읽은 것으로 표시한다 */
    @Transactional
    public Inquiry openMine(String email, Long id) {
        Inquiry inquiry = inquiryRepository.findByIdAndUserId(id, userAccountService.idOf(email))
                .orElseThrow(NoSuchElementException::new);
        inquiry.markAnswerRead();
        return inquiry;
    }

    /** 아직 보지 않은 답변 수 (마이페이지 요약) */
    @Transactional(readOnly = true)
    public long newAnswerCount(String email) {
        return inquiryRepository.countByUserIdAndStatusAndAnswerReadAtIsNull(userAccountService.idOf(email),
                Inquiry.Status.ANSWERED);
    }

    @Transactional(readOnly = true)
    public long waitingCount() {
        return inquiryRepository.countByStatus(Inquiry.Status.WAITING);
    }

    @Transactional(readOnly = true)
    public Page<Inquiry> page(Inquiry.Status status, int page) {
        PageRequest request = PageRequest.of(Math.max(page, 0), ADMIN_PAGE_SIZE);
        return status == Inquiry.Status.WAITING
                ? inquiryRepository.findByStatusOrderByIdAsc(status, request)
                : inquiryRepository.findByStatusOrderByIdDesc(status, request);
    }

    @Transactional(readOnly = true)
    public Inquiry get(Long id) {
        return inquiryRepository.findById(id).orElseThrow(NoSuchElementException::new);
    }

    /** 답변은 한 번만. 이미 답한 문의면 거절한다 (공식 답변이 나중에 바뀌지 않게) */
    @Transactional
    public void answer(Long id, String adminEmail, String text) {
        Inquiry inquiry = get(id);
        if (inquiry.answered()) {
            throw new UserFacingException("이미 답변한 문의입니다. 더 안내할 내용은 사용자가 새 문의로 남기게 해 주세요.");
        }
        inquiry.answer(adminEmail, text.strip());
    }
}
