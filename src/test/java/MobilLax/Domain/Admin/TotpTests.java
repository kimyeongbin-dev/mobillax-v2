package MobilLax.Domain.Admin;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TOTP 계산(RFC 6238 부록 B 시험 값)과 OTP 비밀값 암호화 (보안 규칙 S12) */
class TotpTests {

    /** RFC 6238 부록 B 의 SHA1 비밀값 */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII); // secret-scan: allow (RFC 공개 시험 값)

    @Test
    void matchesRfc6238TestVectors() {
        // RFC 는 8자리 값을 준다. 6자리는 그 끝 6자리와 같다
        assertThat(Totp.code(RFC_SECRET, Totp.step(59))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.step(1111111109))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.step(1111111111))).isEqualTo("050471");
        assertThat(Totp.code(RFC_SECRET, Totp.step(1234567890))).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, Totp.step(2000000000))).isEqualTo("279037");
    }

    @Test
    void acceptsOneStepOfClockDriftAndRejectsReuse() {
        long now = 1_234_567_890L;
        long step = Totp.step(now);
        String previous = Totp.code(RFC_SECRET, step - 1);
        String next = Totp.code(RFC_SECRET, step + 1);
        String tooOld = Totp.code(RFC_SECRET, step - 2);

        assertThat(Totp.matchingStep(RFC_SECRET, previous, now, 0)).hasValue(step - 1);
        assertThat(Totp.matchingStep(RFC_SECRET, next, now, 0)).hasValue(step + 1);
        assertThat(Totp.matchingStep(RFC_SECRET, tooOld, now, 0)).isEmpty();
        assertThat(Totp.matchingStep(RFC_SECRET, Totp.code(RFC_SECRET, step), now, step))
                .as("이미 쓴 시간 칸의 번호는 다시 받지 않는다").isEmpty();
        assertThat(Totp.matchingStep(RFC_SECRET, "12345", now, 0)).isEmpty();
    }

    @Test
    void base32MatchesRfc4648WithoutPadding() {
        assertThat(Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        assertThat(Totp.base32("f".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MY");
    }

    @Test
    void cipherRoundTripsAndRejectsTamperingOrMissingKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        OtpSecretCipher cipher = new OtpSecretCipher(Base64.getEncoder().encodeToString(key));
        byte[] secret = Totp.newSecret();

        String stored = cipher.encrypt(secret);
        assertThat(stored).doesNotContain(Totp.base32(secret));
        assertThat(cipher.decrypt(stored)).isEqualTo(secret);
        assertThat(cipher.encrypt(secret)).as("IV 가 매번 달라 같은 값도 다르게 저장").isNotEqualTo(stored);

        byte[] raw = Base64.getDecoder().decode(stored);
        raw[raw.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(raw))).isInstanceOf(IllegalStateException.class);

        assertThat(new OtpSecretCipher("").configured()).isFalse();
        assertThat(new OtpSecretCipher("짧은키").configured()).isFalse();
        assertThat(new OtpSecretCipher(Base64.getEncoder().encodeToString(new byte[16])).configured())
                .as("AES-256 키는 32바이트").isFalse();
    }

    @Test
    void maskingKeepsOnlyTheFirstLetter() {
        assertThat(AdminMasking.email("kim@gmail.com")).isEqualTo("k***@gmail.com");
        assertThat(AdminMasking.name("김영빈")).isEqualTo("김**");
        assertThat(AdminMasking.name("김")).isEqualTo("김*");
    }
}
