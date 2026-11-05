package MobilLax.Domain.Inquiry;

import MobilLax.Common.Validation.MessageText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 1:1 문의 작성 폼 (v2.12, 보안 규칙 S10)
 * 종류는 enum 으로 받아 정해진 값만 들어온다. 없는 값은 형 변환 실패로 거절된다(FormErrors 가 일반 문구로 바꿈)
 */
@Data
public class InquiryForm {

    /** 화면 입력 순서 (오류 문구를 고를 때) */
    static final String[] FIELD_ORDER = {"category", "title", "content"};

    @NotNull(message = "문의 종류를 골라 주세요.")
    private Inquiry.Category category;

    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = Inquiry.TITLE_MAX, message = "제목은 " + Inquiry.TITLE_MAX + "자까지 쓸 수 있습니다.")
    @MessageText
    private String title;

    @NotBlank(message = "내용을 입력해 주세요.")
    @Size(max = Inquiry.CONTENT_MAX, message = "내용은 " + Inquiry.CONTENT_MAX + "자까지 쓸 수 있습니다.")
    @MessageText
    private String content;
}
