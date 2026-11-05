package MobilLax.Domain.Inquiry;

import MobilLax.Common.Time.KoreaTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 1:1 문의 (inquiry 테이블, v2.12)
 * - 문의한 사람은 이메일 대신 계정 ID 로 묶는다. 관리자 화면에 이메일을 내보낼 일이 없고, 탈퇴 뒤 같은 이메일로
 *   다시 가입한 사람에게 예전 문의가 보이지 않는다
 * - 답변은 한 번만 단다. 단 뒤에는 고치지 않는다(사용자가 받은 공식 답변이 나중에 바뀌지 않게). 더 물을 것은 새 문의로
 * - 상태는 정해진 메서드(answer, markAnswerRead)로만 바꾼다
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "inquiry", indexes = @Index(name = "idx_inquiry_user", columnList = "user_id"))
public class Inquiry {

    public static final int TITLE_MAX = 100;
    public static final int CONTENT_MAX = 2000;

    public enum Category {
        BOOKING("예매·결제"), ROUTE("경로 검색"), ACCOUNT("계정"), ETC("기타");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Status {
        WAITING("답변 대기"), ANSWERED("답변 완료");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Category category;

    @Column(nullable = false, length = TITLE_MAX)
    private String title;

    @Column(nullable = false, length = CONTENT_MAX)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(length = CONTENT_MAX)
    private String answer;

    /** 답한 관리자 (감사용. 사용자 화면에는 "MobilLax 고객센터"로만 보인다) */
    @Column(name = "answered_by", length = 100)
    private String answeredBy;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    /** 사용자가 답변을 처음 본 시각. 없으면 마이페이지에 "새 답변"으로 보인다 */
    @Column(name = "answer_read_at")
    private LocalDateTime answerReadAt;

    static Inquiry open(Long userId, Category category, String title, String content) {
        Inquiry inquiry = new Inquiry();
        inquiry.userId = userId;
        inquiry.category = category;
        inquiry.title = title;
        inquiry.content = content;
        inquiry.status = Status.WAITING;
        inquiry.createdAt = KoreaTime.now();
        return inquiry;
    }

    void answer(String adminEmail, String text) {
        if (status == Status.ANSWERED) {
            throw new IllegalStateException("이미 답변한 문의입니다");
        }
        this.answer = text;
        this.answeredBy = adminEmail;
        this.answeredAt = KoreaTime.now();
        this.status = Status.ANSWERED;
    }

    void markAnswerRead() {
        if (status == Status.ANSWERED && answerReadAt == null) {
            answerReadAt = KoreaTime.now();
        }
    }

    public boolean answered() {
        return status == Status.ANSWERED;
    }

    public boolean newAnswer() {
        return answered() && answerReadAt == null;
    }
}
