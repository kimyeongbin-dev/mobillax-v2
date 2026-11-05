package MobilLax.Domain.Inquiry;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Validation.FormErrors;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * 마이페이지 1:1 문의 (v2.12): 로그인한 사용자 본인 문의만 (보안 규칙 S2·S4)
 * 답변은 앱 안에서만 보여 준다. 관리자가 사용자 이메일을 볼 필요가 없다
 */
@Controller
@RequestMapping("/mypage/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("inquiries", inquiryService.listMine(principal.getName()));
        return "inquiry/list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new InquiryForm());
        }
        model.addAttribute("categories", Inquiry.Category.values());
        return "inquiry/new";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") InquiryForm form, BindingResult binding, Principal principal,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            return backToForm(form, FormErrors.first(binding, InquiryForm.FIELD_ORDER), redirectAttributes);
        }
        try {
            Long id = inquiryService.create(principal.getName(), form);
            redirectAttributes.addFlashAttribute("successMessage", "문의를 남겼습니다. 답변이 달리면 마이페이지에 표시됩니다.");
            return "redirect:/mypage/inquiries/" + id;
        } catch (UserFacingException e) {
            return backToForm(form, e.getMessage(), redirectAttributes);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("inquiry", inquiryService.openMine(principal.getName(), id));
        return "inquiry/detail";
    }

    /** 오류가 나도 쓰던 글은 다시 채워 준다 (값은 서버가 받은 그대로, th:value·th:text 로 이스케이프) */
    private String backToForm(InquiryForm form, String error, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("form", form);
        redirectAttributes.addFlashAttribute("error", error);
        return "redirect:/mypage/inquiries/new";
    }
}
