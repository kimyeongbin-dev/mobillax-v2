package MobilLax.Domain.MyPage;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@Controller
public class MyPageController {

    private final MyPageServiceInterface myPageService;

    @Autowired
    public MyPageController(MyPageServiceInterface myPageService) {
        this.myPageService = myPageService;
    }

    @GetMapping("/mypage")
    public String showMyPage(Model model, Principal principal) {
        String email = principal.getName();  // 인증된 사용자의 이메일

        MyPageDto dto = myPageService.getMyPageInfo(email);
        model.addAttribute("name", dto.getName());
        model.addAttribute("email", dto.getEmail());
        model.addAttribute("recentRoute", dto.getRecentRoute());
        model.addAttribute("totalPayment", dto.getTotalPayment());
        model.addAttribute("cartCount", dto.getCartCount());

        return "mypage";
    }

    @GetMapping("/mypage/edit")
    public String editPage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) return "redirect:/login";

        String email = user.getUsername(); // Security User의 username은 보통 email
        MyPageDto myPageDto = myPageService.getMyPageInfo(email);
        model.addAttribute("profile", myPageDto);
        return "mypage-edit";
    }

    @PostMapping("/mypage/edit")
    public String updateProfile(@AuthenticationPrincipal User user,
                                @ModelAttribute ProfileUpdateDto dto) {
        if (user == null) return "redirect:/login";

        myPageService.updateProfile(user.getUsername(), dto.getName(), null);
        return "redirect:/mypage";
    }

    // ✅ 회원 탈퇴 요청 처리
    @PostMapping("/mypage/delete")
    public String deleteAccount(@AuthenticationPrincipal UserDetails userDetails,
                                HttpSession session) {
        String email = userDetails.getUsername();
        myPageService.deleteAccountByEmail(email);
        session.invalidate(); // 세션 만료 처리 (로그아웃 효과)
        return "redirect:/home"; // 홈으로 이동
    }
}
