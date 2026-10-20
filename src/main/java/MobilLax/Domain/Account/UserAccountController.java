package MobilLax.Domain.Account;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Validation.FormErrors;
import MobilLax.Common.Validation.PlainText;
import MobilLax.Common.RateLimit.ClientKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 로그인·회원가입 화면과 회원가입 처리 (로그인 처리 자체는 Spring Security 폼 로그인, SecurityConfig)
 */
@Controller
@RequiredArgsConstructor
public class UserAccountController {

    /** 몇 분 남았는지는 알리지 않는다 (이메일마다 다르고, 잠긴 이메일인지 알려 주지 않으려고) */
    static final String LOCKED_MESSAGE = "로그인에 여러 번 실패해 잠시 막았습니다. 15분 안에 다시 시도할 수 있습니다.";

    private final UserAccountService userAccountService;

    /**
     * @param error  로그인 실패 표시 (Spring Security 가 ?error 로 보냄). 값은 쓰지 않고 정해진 문구만 보인다
     * @param logout 로그아웃 완료 표시 (?logout)
     * @param locked 로그인 실패가 많아 잠시 막음 (?locked, 보안 규칙 S11)
     * @param next   로그인 뒤 돌아갈 화면. 허용한 내부 화면만 폼에 다시 싣는다 (LoginRedirect)
     */
    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "error", required = false) @Size(max = 20) @PlainText String error,
                                @RequestParam(value = "logout", required = false) @Size(max = 20) @PlainText String logout,
                                @RequestParam(value = "locked", required = false) @Size(max = 20) @PlainText String locked,
                                @RequestParam(value = LoginRedirect.PARAM, required = false) @Size(max = 40) @PlainText String next,
                                Model model) {
        if (error != null) {
            model.addAttribute("error", "이메일 또는 비밀번호가 잘못되었습니다.");
        }
        if (locked != null) {
            model.addAttribute("error", LOCKED_MESSAGE);
        }
        LoginRedirect.safe(next).ifPresent(path -> model.addAttribute("next", path));
        if (logout != null) {
            model.addAttribute("successMessage", "로그아웃 되었습니다.");
        }
        return "member/login";
    }

    @GetMapping("/register")
    public String showSignupForm(@RequestParam(value = "error", required = false) @Size(max = 20) @PlainText String error,
                                 Model model) {
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "member/register";
    }

    /** 형식(이름·이메일·비밀번호)은 RegisterForm 의 제약으로, 중복 이메일은 서비스가 확인한다 (S10) */
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterForm form,
                           BindingResult binding,
                           Model model,
                           HttpServletRequest request,
                           RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            model.addAttribute("error", FormErrors.first(binding, "name", "email", "password"));
            return "member/register";
        }
        try {
            userAccountService.registerUser(form.getName(), form.getEmail(), form.getPassword(), ClientKey.of(request));
            // 리다이렉트하면 Model 값은 사라지므로 한 번만 쓰는 flash 속성으로 넘긴다
            redirectAttributes.addFlashAttribute("successMessage", "회원가입에 성공했습니다. 로그인해 주세요.");
            return "redirect:/login";
        } catch (UserFacingException e) {
            model.addAttribute("error", e.getMessage());
            return "member/register";
        }
    }
}
