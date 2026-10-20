package MobilLax.Domain.Admin;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Validation.OtpCode;
import MobilLax.Common.Validation.RecoveryCode;
import MobilLax.Common.Web.CurrentUser;
import MobilLax.Domain.Account.AccountRoles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Duration;
import java.util.List;

/**
 * 관리자 2단계 인증 화면 (v2.11, 보안 규칙 S12) — /admin/otp 는 비밀번호만 맞힌 관리자(ADMIN_PENDING)만 연다
 * OTP 를 확인하면 세션 ID 를 바꾸고 ROLE_ADMIN 을 더하며, 관리자 세션 유지 시간을 줄인다
 */
@Controller
@RequestMapping("/admin/otp")
@RequiredArgsConstructor
public class AdminOtpController {

    private final AdminOtpService otpService;

    @Value("${admin.session-timeout:10m}")
    private Duration adminSessionTimeout;

    @GetMapping
    public String page(Principal principal, HttpServletRequest request, Model model) {
        if (CurrentUser.hasAuthority(AccountRoles.authority(AccountRoles.ADMIN))) {
            return "redirect:/admin";
        }
        AdminOtpService.Status status = otpService.status(principal.getName());
        model.addAttribute("status", status.name());
        if (status == AdminOtpService.Status.NEEDS_SETUP) {
            model.addAttribute("setup", otpService.startSetup(principal.getName(), request.getRemoteAddr()));
        }
        return "admin/otp";
    }

    /** 등록 마치기: 복구 코드를 이 응답에서 한 번만 보여 준다 (리다이렉트·세션에 남기지 않음) */
    @PostMapping("/setup")
    public String confirmSetup(@RequestParam @NotNull @OtpCode String code, Principal principal,
                               HttpServletRequest request, HttpServletResponse response, Model model,
                               RedirectAttributes redirectAttributes) {
        List<String> recoveryCodes;
        try {
            recoveryCodes = otpService.confirmSetup(principal.getName(), code, request.getRemoteAddr());
        } catch (UserFacingException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/otp";
        }
        elevate(request, response);
        model.addAttribute("recoveryCodes", recoveryCodes);
        return "admin/recovery-codes";
    }

    @PostMapping("/verify")
    public String verify(@RequestParam @NotNull @OtpCode String code, Principal principal,
                         HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        try {
            otpService.verify(principal.getName(), code, request.getRemoteAddr());
        } catch (UserFacingException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/otp";
        }
        elevate(request, response);
        return "redirect:/admin";
    }

    @PostMapping("/recovery")
    public String recovery(@RequestParam @NotNull @RecoveryCode String recoveryCode, Principal principal,
                           HttpServletRequest request, HttpServletResponse response,
                           RedirectAttributes redirectAttributes) {
        int left;
        try {
            left = otpService.verifyRecovery(principal.getName(), recoveryCode, request.getRemoteAddr());
        } catch (UserFacingException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/otp";
        }
        elevate(request, response);
        redirectAttributes.addFlashAttribute("warning",
                "복구 코드로 들어왔습니다. 남은 복구 코드 " + left + "개. 휴대폰을 바꿨다면 OTP 를 다시 등록해 주세요.");
        return "redirect:/admin";
    }

    private void elevate(HttpServletRequest request, HttpServletResponse response) {
        CurrentUser.grant(AccountRoles.authority(AccountRoles.ADMIN), request, response);
        request.getSession().setMaxInactiveInterval((int) adminSessionTimeout.toSeconds());
    }
}
