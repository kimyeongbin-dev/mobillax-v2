package MobilLax.Domain.MyPage;

import MobilLax.Domain.Cart.CartService;
import MobilLax.Domain.Payment.PaymentService;
import MobilLax.Model.UserAccount;
import MobilLax.Repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyPageService implements MyPageServiceInterface {

    private final UserAccountRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final CartService cartService;
    private final PaymentService paymentService;

    @Autowired
    public MyPageService(UserAccountRepository userRepo,
                         PasswordEncoder passwordEncoder,
                         CartService cartService,
                         PaymentService paymentService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.cartService = cartService;
        this.paymentService = paymentService;
    }

    @Override
    public MyPageDto getMyPageInfo(String email) {
        UserAccount user = userRepo.findByEmail(email).orElseThrow();

        String recentRoute = cartService.getRecentRouteSummary(email);
        int totalPayment = paymentService.getMonthlyPaymentTotal(email);
        int cartCount = cartService.getCartGroupCount(email);

        return new MyPageDto(user.getName(), user.getEmail(), recentRoute, totalPayment, cartCount);
    }

    @Override
    public void updateProfile(String email, String name, String password) {
        UserAccount user = userRepo.findByEmail(email).orElseThrow();

        if (name != null && !name.isBlank()) {
            user.setName(name);
        }

        userRepo.save(user);
    }

    @Override
    @Transactional
    public void deleteAccountByEmail(String email) {
        userRepo.deleteByEmail(email);
    }
}
