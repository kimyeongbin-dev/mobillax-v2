package MobilLax.Domain.Admin;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.RateLimit.RateLimiter;
import MobilLax.Common.Time.KoreaTime;
import MobilLax.Domain.Account.PasswordAttemptGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * 관리자 2단계 인증 (TOTP, 보안 규칙 S12)
 * - 처음: QR(또는 직접 입력 키)로 인증 앱에 등록 → 앱의 번호로 한 번 확인해야 켜진다 → 복구 코드 8개를 한 번만 보여 준다
 * - 그 뒤: 비밀번호 로그인 다음 OTP 번호(또는 복구 코드)를 확인해야 관리자 권한을 받는다
 * - 같은 번호는 두 번 받지 않고, 틀리면 계정마다 15분에 5번까지 (PasswordAttemptGuard)
 * - 계정 정지 같은 민감한 작업도 지금 번호를 다시 확인한다 (stepUp)
 */
@Service
@RequiredArgsConstructor
public class AdminOtpService {

    static final int RECOVERY_CODES = 8;
    private static final String ISSUER = "MobilLax";
    private static final char[] RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminOtpRepository otpRepository;
    private final AdminRecoveryCodeRepository recoveryCodeRepository;
    private final OtpSecretCipher cipher;
    private final PasswordAttemptGuard attemptGuard;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService audit;
    private final Clock clock;

    public enum Status {
        /** ADMIN_OTP_KEY 가 없음: 관리자 화면을 열지 않는다 */
        NOT_CONFIGURED,
        /** 아직 인증 앱을 등록하지 않음 */
        NEEDS_SETUP,
        READY
    }

    /** 등록 화면에 보여 줄 값 (비밀값이 들어 있으니 이 화면 밖으로 내보내지 않는다) */
    public record Setup(String qrSvg, String manualKey) {
    }

    @Transactional(readOnly = true)
    public Status status(String email) {
        if (!cipher.configured()) {
            return Status.NOT_CONFIGURED;
        }
        return otpRepository.findById(email).filter(AdminOtp::enabled).isPresent() ? Status.READY : Status.NEEDS_SETUP;
    }

    /** 새 비밀값으로 등록을 시작한다 (등록을 마치기 전이면 다시 열 때마다 새 값). 이미 켜져 있으면 거절 */
    @Transactional
    public Setup startSetup(String email, String clientIp) {
        requireConfigured();
        if (status(email) == Status.READY) {
            throw new UserFacingException("이미 OTP 를 등록했습니다.");
        }
        byte[] secret = Totp.newSecret();
        otpRepository.save(new AdminOtp(email, cipher.encrypt(secret)));
        audit.record(email, AdminAuditLog.Action.OTP_SETUP_STARTED, null, null, clientIp);
        String key = Totp.base32(secret);
        String uri = "otpauth://totp/" + ISSUER + ":" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "?secret=" + key + "&issuer=" + ISSUER + "&digits=" + Totp.DIGITS + "&period=" + Totp.STEP_SECONDS;
        return new Setup(QrSvg.render(uri), key);
    }

    /** 앱에 뜬 번호로 등록을 마친다. 복구 코드 원문을 돌려준다 (이때 한 번만 보여 준다) */
    @Transactional
    public List<String> confirmSetup(String email, String code, String clientIp) {
        AdminOtp otp = otpRepository.findById(email).filter(o -> !o.enabled())
                .orElseThrow(() -> new UserFacingException("등록을 다시 시작해 주세요."));
        acceptCode(otp, code, clientIp, AdminAuditLog.Action.OTP_FAILED, null);
        otp.setEnabledAt(KoreaTime.now());
        otpRepository.save(otp);
        audit.record(email, AdminAuditLog.Action.OTP_ENABLED, null, null, clientIp);
        return newRecoveryCodes(email);
    }

    /** 로그인 두 번째 단계 */
    @Transactional
    public void verify(String email, String code, String clientIp) {
        AdminOtp otp = enabledOtp(email);
        acceptCode(otp, code, clientIp, AdminAuditLog.Action.OTP_FAILED, null);
        audit.record(email, AdminAuditLog.Action.OTP_VERIFIED, null, null, clientIp);
    }

    /** 민감한 작업 전 재확인: 지금 번호를 다시 받는다 */
    @Transactional
    public void stepUp(String email, String code, Long targetUserId, String clientIp) {
        AdminOtp otp = enabledOtp(email);
        try {
            acceptCode(otp, code, clientIp, AdminAuditLog.Action.STEP_UP_FAILED, targetUserId);
        } catch (UserFacingException e) {
            throw new UserFacingException("OTP 번호가 맞지 않아 작업하지 않았습니다. " + e.getMessage());
        }
    }

    /** 휴대폰을 잃어버렸을 때: 복구 코드 하나를 쓰고, 남은 개수를 돌려준다 */
    @Transactional
    public int verifyRecovery(String email, String recoveryCode, String clientIp) {
        enabledOtp(email);
        requireAttemptsLeft(email);
        for (AdminRecoveryCode candidate : recoveryCodeRepository.findByEmailAndUsedAtIsNull(email)) {
            if (passwordEncoder.matches(recoveryCode, candidate.getCodeHash())) {
                candidate.setUsedAt(KoreaTime.now());
                recoveryCodeRepository.save(candidate);
                attemptGuard.otpMatched(email);
                audit.record(email, AdminAuditLog.Action.RECOVERY_CODE_USED, null, null, clientIp);
                return recoveryCodeRepository.findByEmailAndUsedAtIsNull(email).size();
            }
        }
        attemptGuard.otpFailed(email);
        audit.record(email, AdminAuditLog.Action.OTP_FAILED, null, "복구 코드", clientIp);
        throw new UserFacingException("복구 코드가 맞지 않거나 이미 쓴 코드입니다.");
    }

    private void acceptCode(AdminOtp otp, String code, String clientIp, AdminAuditLog.Action failure, Long targetUserId) {
        requireAttemptsLeft(otp.getEmail());
        OptionalLong step = Totp.matchingStep(cipher.decrypt(otp.getSecretEnc()), code,
                clock.instant().getEpochSecond(), otp.getLastUsedStep());
        if (step.isEmpty()) {
            attemptGuard.otpFailed(otp.getEmail());
            audit.record(otp.getEmail(), failure, targetUserId, null, clientIp);
            throw new UserFacingException("OTP 번호가 맞지 않거나 이미 쓴 번호입니다. 앱의 새 번호를 입력해 주세요.");
        }
        otp.setLastUsedStep(step.getAsLong());
        otpRepository.save(otp);
        attemptGuard.otpMatched(otp.getEmail());
    }

    private void requireAttemptsLeft(String email) {
        RateLimiter.Decision decision = attemptGuard.checkOtp(email);
        if (!decision.allowed()) {
            throw new UserFacingException("OTP 를 여러 번 틀렸습니다. " + decision.retryAfterText() + " 뒤에 다시 시도해 주세요.");
        }
    }

    private AdminOtp enabledOtp(String email) {
        requireConfigured();
        return otpRepository.findById(email).filter(AdminOtp::enabled)
                .orElseThrow(() -> new UserFacingException("OTP 를 먼저 등록해 주세요."));
    }

    private void requireConfigured() {
        if (!cipher.configured()) {
            throw new UserFacingException("관리자 OTP 키(ADMIN_OTP_KEY)가 설정되지 않아 관리자 화면을 열 수 없습니다.");
        }
    }

    private List<String> newRecoveryCodes(String email) {
        recoveryCodeRepository.deleteByEmail(email);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String code = randomChunk() + "-" + randomChunk();
            codes.add(code);
            recoveryCodeRepository.save(new AdminRecoveryCode(email, passwordEncoder.encode(code)));
        }
        return codes;
    }

    /** 헷갈리는 글자(0·o·1·l·i)를 뺀 4자리 */
    private static String randomChunk() {
        char[] chunk = new char[4];
        for (int i = 0; i < chunk.length; i++) {
            chunk[i] = RECOVERY_ALPHABET[RANDOM.nextInt(RECOVERY_ALPHABET.length)];
        }
        return new String(chunk);
    }
}
