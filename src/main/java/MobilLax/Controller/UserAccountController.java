/**
 * UserAccountController.java
 *
 * ✅ 파일 목적: 사용자 로그인 및 회원가입과 관련된 HTTP 요청을 처리하는 Spring MVC 컨트롤러 클래스입니다.
 *              사용자 인증 화면 렌더링 및 폼 제출을 처리합니다.
 *
 * 작성자: 김영빈
 * 마지막 수정일: 2025-05-11
 */

package MobilLax.Controller;

import MobilLax.Service.UserAccountService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * ✅ 클래스 설명:
 * 사용자 로그인, 회원가입 폼 페이지 요청 및 회원가입 처리를 담당하는 컨트롤러입니다.
 * Thymeleaf 등 템플릿 엔진을 사용하는 MVC 구조에서 사용됩니다.
 */
@Controller
public class UserAccountController {

    private final UserAccountService userAccountService;

    /**
     * 생성자 주입
     * @param userAccountService 사용자 서비스 로직을 담당하는 서비스 클래스
     */
    public UserAccountController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    /**
     * ✅ 로그인 폼 요청 핸들러
     * 로그인 페이지를 렌더링하며, 에러나 로그아웃 메시지를 모델에 추가합니다.
     *
     * @param error 로그인 실패 여부 (Spring Security에서 전달)
     * @param logout 로그아웃 성공 여부
     * @param model View에 전달할 메시지 설정
     * @return 로그인 뷰 템플릿 경로 (member/login.html)
     */
    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "error", required = false) String error,
                                @RequestParam(value = "logout", required = false) String logout,
                                Model model) {
        if (error != null) {
            model.addAttribute("error", "이메일 또는 비밀번호가 잘못되었습니다.");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "로그아웃 되었습니다.");
        }
        return "member/login";
    }

    /**
     * ✅ 회원가입 폼 요청 핸들러
     * 회원가입 페이지를 렌더링하며, 전달된 에러 메시지를 출력합니다.
     *
     * @param error 회원가입 실패 시 전달된 오류 메시지
     * @param model View에 전달할 모델 객체
     * @return 회원가입 뷰 템플릿 경로 (member/register.html)
     */
    @GetMapping("/register")
    public String showSignupForm(@RequestParam(value = "error", required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "member/register";
    }

    /**
     * ✅ 회원가입 처리 핸들러
     * POST 방식으로 전송된 회원가입 정보를 받아 유효성 검사 및 사용자 등록을 수행합니다.
     *
     * @param name 사용자 이름
     * @param email 사용자 이메일
     * @param password 사용자 비밀번호
     * @param model View에 전달할 메시지 (성공 또는 오류)
     * @return 회원가입 성공 시 로그인 페이지로 리디렉션, 실패 시 다시 회원가입 페이지로 이동
     */
    @PostMapping("/register")
    public String register(@RequestParam String name,
                           @RequestParam String email,
                           @RequestParam String password,
                           Model model) {
        try {
            userAccountService.registerUser(name, email, password);
            model.addAttribute("successMessage", "회원가입에 성공했습니다.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "member/register";
        }
    }
}
