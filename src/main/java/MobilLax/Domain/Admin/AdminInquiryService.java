package MobilLax.Domain.Admin;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Domain.Inquiry.Inquiry;
import MobilLax.Domain.Inquiry.InquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 1:1 문의 처리 (v2.12, 보안 규칙 S12)
 * - 문의에는 계정 ID 만 있어 관리자 화면에 이메일이 나가지 않는다
 * - 답변은 지금 OTP 번호로 다시 확인한다(stepUp). 세션을 빼앗은 사람이 공식 답변으로 사용자를 속이지 못하게
 * - 목록·조회·답변을 감사 로그에 남긴다
 */
@Service
@RequiredArgsConstructor
public class AdminInquiryService {

    private final InquiryService inquiryService;
    private final AdminOtpService otpService;
    private final AdminAuditService audit;

    public long waitingCount() {
        return inquiryService.waitingCount();
    }

    public Page<Inquiry> list(String adminEmail, Inquiry.Status status, int page, String clientIp) {
        audit.record(adminEmail, AdminAuditLog.Action.INQUIRY_LIST_VIEWED, null, status.label(), clientIp);
        return inquiryService.page(status, page);
    }

    public Inquiry detail(String adminEmail, Long id, String clientIp) {
        Inquiry inquiry = inquiryService.get(id);
        audit.record(adminEmail, AdminAuditLog.Action.INQUIRY_VIEWED, inquiry.getUserId(), "문의 #" + id, clientIp);
        return inquiry;
    }

    @Transactional
    public void answer(String adminEmail, Long id, String otpCode, String text, String clientIp) {
        Inquiry inquiry = inquiryService.get(id);
        if (inquiry.answered()) {
            // OTP 를 확인하기 전에 거절한다 (확인 뒤 되돌아가면 쓴 번호가 다시 쓰일 수 있음)
            throw new UserFacingException("이미 답변한 문의입니다. 더 안내할 내용은 사용자가 새 문의로 남기게 해 주세요.");
        }
        otpService.stepUp(adminEmail, otpCode, inquiry.getUserId(), clientIp);
        inquiryService.answer(id, adminEmail, text);
        audit.record(adminEmail, AdminAuditLog.Action.INQUIRY_ANSWERED, inquiry.getUserId(), "문의 #" + id, clientIp);
    }
}
