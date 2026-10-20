package MobilLax.Domain.Admin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.OptionalLong;

/**
 * 시간 기반 일회용 번호 TOTP (RFC 6238, 보안 규칙 S12)
 * - Google Authenticator 등 인증 앱과 같은 기본값: HMAC-SHA1, 30초, 6자리
 * - 외부 서비스 없이 서버와 앱이 같은 비밀값과 현재 시각으로 각자 계산한다 (도메인·인터넷 불필요)
 */
public final class Totp {

    static final int DIGITS = 6;
    static final long STEP_SECONDS = 30;
    /** 앱과 서버의 시계 차이를 앞뒤 한 칸(30초)까지 받아 준다 */
    static final int ALLOWED_DRIFT_STEPS = 1;
    private static final int SECRET_BYTES = 20;
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray(); // secret-scan: allow (RFC 4648 알파벳)
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** 새 비밀값 (160비트, RFC 4226 권장 길이) */
    public static byte[] newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }

    /** epoch 초가 속한 시간 칸 번호 */
    public static long step(long epochSecond) {
        return Math.floorDiv(epochSecond, STEP_SECONDS);
    }

    /** 시간 칸 하나의 번호 (앞자리 0 포함 6자리) */
    public static String code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % (int) Math.pow(10, DIGITS));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 을 쓸 수 없습니다", e);
        }
    }

    /**
     * 입력한 번호가 맞는 시간 칸. 앞뒤 한 칸까지 보고, 이미 쓴 칸(lastUsedStep 이하)은 받지 않는다 (같은 번호 재사용 막기)
     * 비교는 길이가 같으면 끝까지 다 본다 (앞자리가 맞을수록 빨라지지 않게)
     */
    public static OptionalLong matchingStep(byte[] secret, String input, long epochSecond, long lastUsedStep) {
        long now = step(epochSecond);
        for (long candidate = now - ALLOWED_DRIFT_STEPS; candidate <= now + ALLOWED_DRIFT_STEPS; candidate++) {
            if (candidate > lastUsedStep && constantTimeEquals(code(secret, candidate), input)) {
                return OptionalLong.of(candidate);
            }
        }
        return OptionalLong.empty();
    }

    /** 인증 앱에 직접 입력하는 키 (Base32, RFC 4648, 패딩 없음) */
    public static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 0x1f]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
        }
        return out.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
