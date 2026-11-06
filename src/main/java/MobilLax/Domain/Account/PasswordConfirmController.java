package MobilLax.Domain.Account;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Validation.FormErrors;
import MobilLax.Common.Validation.PlainText;
import MobilLax.Common.Web.RecentPasswordInterceptor;
import MobilLax.Common.Web.RecentPasswordSession;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

/**
 * 비밀번호 재확인 (v2.14, 보안 규칙 S14): 개인정보 수정·탈퇴는 화면으로, 결제는 fetch 로 확인한다
 * 돌아갈 화면은 정해 둔 곳만 받는다 (열린 리다이렉트 방지)
 */
@Controller
@RequiredArgsConstructor
public class PasswordConfirmController {

    private static final Set<String> RETURN_PAGES = Set.of("/mypage/edit", "/mypage/withdraw");
    private static final String DEFAULT_RETURN = "/mypage";

    private final PasswordConfirmService confirmService;

    @GetMapping(RecentPasswordInterceptor.CONFIRM_PAGE)
    public String page(@RequestParam(required = false) @Size(max = 40) @PlainText String next, Model model) {
        model.addAttribute("next", safeReturn(next));
        return "mypage-confirm";
    }

    @PostMapping(RecentPasswordInterceptor.CONFIRM_PAGE)
    public String confirm(@Valid @ModelAttribute PasswordConfirmForm form, BindingResult binding, Principal principal,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String next = safeReturn(form.getNext());
        String retry = "redirect:" + RecentPasswordInterceptor.CONFIRM_PAGE + "?next=" + next;
        if (binding.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", FormErrors.first(binding, "password"));
            return retry;
        }
        try {
            confirmService.verify(principal.getName(), form.getPassword());
        } catch (UserFacingException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return retry;
        }
        RecentPasswordSession.mark(request);
        return "redirect:" + next;
    }

    /** 결제처럼 화면을 떠나면 안 되는 작업의 확인 창. 틀리면 GlobalExceptionHandler 가 400 과 안내 문구로 응답한다 */
    @PostMapping("/api/account/confirm-password")
    @ResponseBody
    public Map<String, Boolean> confirmApi(@RequestBody PasswordConfirmForm form, Principal principal, HttpServletRequest request) {
        confirmService.verify(principal.getName(), form.getPassword());
        RecentPasswordSession.mark(request);
        return Map.of("confirmed", true);
    }

    private static String safeReturn(String next) {
        return next != null && RETURN_PAGES.contains(next) ? next : DEFAULT_RETURN;
    }
}
