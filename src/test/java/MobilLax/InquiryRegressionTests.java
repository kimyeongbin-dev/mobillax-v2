package MobilLax;

import MobilLax.Common.RateLimit.RateLimiter;
import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import MobilLax.Domain.Inquiry.Inquiry;
import MobilLax.Domain.Inquiry.InquiryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 회귀 테스트 — 1:1 문의 사용자 쪽 (v2.12, docs/SECURITY_RULES.md S2·S3·S4·S10·S12)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 * 관리자 답변(OTP 재확인·감사 로그)은 AdminSecurityRegressionTests.Inquiries 에서 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InquiryRegressionTests {

    private static final String OWNER = "inquiry-owner@test.local";
    private static final String OTHER = "inquiry-other@test.local";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    InquiryRepository inquiryRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    RateLimiter rateLimiter;

    private Long ownerId;

    @BeforeEach
    void setUp() {
        rateLimiter.clear();
        ownerId = account(OWNER).getId();
        account(OTHER);
    }

    @AfterEach
    void clearLimits() {
        rateLimiter.clear();
    }

    private UserAccount account(String email) {
        UserAccount account = new UserAccount();
        account.setEmail(email);
        account.setName("문의검사");
        account.setPassword(passwordEncoder.encode("FAKE_password1"));
        account.setRole("USER");
        return userAccountRepository.save(account);
    }

    private MockHttpServletRequestBuilder write(String category, String title, String content) {
        return post("/mypage/inquiries").with(csrf()).with(user(OWNER))
                .param("category", category).param("title", title).param("content", content);
    }

    private Long createdId(MvcResult result) {
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/mypage/inquiries/");
        return Long.valueOf(location.substring("/mypage/inquiries/".length()));
    }

    @Test
    void anonymousCannotOpenOrWrite() throws Exception {
        mvc.perform(get("/mypage/inquiries")).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(post("/mypage/inquiries").with(csrf()).param("category", "ETC").param("title", "t").param("content", "c"))
                .andExpect(redirectedUrlPattern("**/login"));
        assertThat(inquiryRepository.findByUserIdOrderByIdDesc(ownerId)).isEmpty();
    }

    @Test
    void writingNeedsCsrf() throws Exception {
        mvc.perform(post("/mypage/inquiries").with(user(OWNER)).param("category", "ETC").param("title", "제목").param("content", "내용"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerWritesAndReadsWithLineBreaksAndEscaping() throws Exception {
        String content = "첫 줄\n둘째 줄 <script>alert('x')</script> \"따옴표\"";
        Long id = createdId(mvc.perform(write("BOOKING", "결제 확인 부탁드려요", content)).andReturn());

        Inquiry saved = inquiryRepository.findById(id).orElseThrow();
        assertThat(saved.getUserId()).as("이메일 대신 계정 ID 로 묶는다").isEqualTo(ownerId);
        assertThat(saved.getStatus()).isEqualTo(Inquiry.Status.WAITING);
        assertThat(saved.getContent()).isEqualTo(content);

        String page = mvc.perform(get("/mypage/inquiries/" + id).with(user(OWNER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("결제 확인 부탁드려요").contains("&lt;script&gt;").doesNotContain("<script>alert");

        String list = mvc.perform(get("/mypage/inquiries").with(user(OWNER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).contains("결제 확인 부탁드려요").contains("답변 대기");
    }

    @Test
    void othersCannotSeeAnInquiry() throws Exception {
        Long id = createdId(mvc.perform(write("ETC", "남이 보면 안 되는 문의", "내용")).andReturn());
        mvc.perform(get("/mypage/inquiries/" + id).with(user(OTHER))).andExpect(status().isNotFound());
        String list = mvc.perform(get("/mypage/inquiries").with(user(OTHER)))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("남이 보면 안 되는 문의");
    }

    @Test
    void invalidInputIsRejectedWithoutSaving() throws Exception {
        String tooLong = "가".repeat(Inquiry.CONTENT_MAX + 1);
        String[][] cases = {
                {"ETC", " ", "내용", "제목을 입력해 주세요"},
                {"ETC", "제목", tooLong, "2000자까지"},
                {"ETC", "제목", "제어 문자\u0007", "쓸 수 없는 문자"},
                {"NOT_A_CATEGORY", "제목", "내용", "입력값이 올바르지 않습니다"},
                {"", "제목", "내용", "문의 종류를 골라 주세요"},
        };
        for (String[] c : cases) {
            MvcResult result = mvc.perform(write(c[0], c[1], c[2])).andExpect(redirectedUrl("/mypage/inquiries/new")).andReturn();
            assertThat(String.valueOf(result.getFlashMap().get("error"))).as(c[3]).contains(c[3])
                    .doesNotContain("Failed to convert").doesNotContain("MobilLax.");
        }
        assertThat(inquiryRepository.findByUserIdOrderByIdDesc(ownerId)).isEmpty();
    }

    @Test
    void writingIsLimitedPerHour() throws Exception {
        for (int i = 0; i < 5; i++) {
            createdId(mvc.perform(write("ETC", "문의 " + i, "내용")).andReturn());
        }
        MvcResult sixth = mvc.perform(write("ETC", "여섯 번째", "내용")).andExpect(redirectedUrl("/mypage/inquiries/new")).andReturn();
        assertThat(String.valueOf(sixth.getFlashMap().get("error"))).contains("문의 등록 가능 횟수");
        assertThat(inquiryRepository.findByUserIdOrderByIdDesc(ownerId)).hasSize(5);
    }
}
