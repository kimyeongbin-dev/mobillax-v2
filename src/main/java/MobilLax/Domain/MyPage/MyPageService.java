package MobilLax.Domain.MyPage;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Domain.Cart.CartService;
import MobilLax.Domain.Payment.PaymentHistoryService;
import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 업무 규칙 — 입력 형식은 컨트롤러 입구의 폼 DTO 가 검사한다 (보안 규칙 S10)
 */
@Service
@RequiredArgsConstructor
public class MyPageService {

    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final CartService cartService;
    private final PaymentHistoryService paymentHistoryService;

    public MyPageDto getMyPageInfo(String email) {
        UserAccount user = userRepo.findByEmail(email).orElseThrow();

        String recentRoute = cartService.getRecentRouteSummary(email);
        int monthlyPayment = paymentHistoryService.getMonthlyPaymentTotal(email);
        int cartCount = cartService.getCartGroupCount(email);

        return new MyPageDto(user.getName(), user.getEmail(), recentRoute, monthlyPayment, cartCount);
    }

    public void updateProfile(String email, String name) {
        UserAccount user = userRepo.findByEmail(email).orElseThrow();
        user.setName(name);
        userRepo.save(user);
    }

    /**
     * 비밀번호 변경 (v2.5)
     * - 현재 비밀번호가 맞아야 한다: 세션을 잠깐 빌린 사람이 비밀번호를 바꿔 계정을 빼앗지 못하게
     * - 지금과 같은 비밀번호로는 바꾸지 않는다
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        UserAccount user = userRepo.findByEmail(email).orElseThrow();
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new UserFacingException("현재 비밀번호가 맞지 않습니다.");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new UserFacingException("새 비밀번호는 지금 비밀번호와 달라야 합니다.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    @Transactional
    public void deleteAccountByEmail(String email) {
        userRepo.deleteByEmail(email);
    }
}
