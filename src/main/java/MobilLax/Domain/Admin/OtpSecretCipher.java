package MobilLax.Domain.Admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * OTP 비밀값 암호화 (AES-256-GCM, 보안 규칙 S12)
 * - DB 가 새어도 비밀값만으로는 번호를 만들 수 없게, 키는 DB 밖(.env 의 ADMIN_OTP_KEY)에 둔다
 * - 키가 없거나 형식이 틀리면 configured() 가 false 이고, 관리자 화면은 열리지 않는다 (fail closed)
 * - 저장 형식: Base64(IV 12바이트 + 암호문·인증 태그)
 */
@Component
public class OtpSecretCipher {

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public OtpSecretCipher(@Value("${admin.otp-key:}") String base64Key) {
        this.key = parse(base64Key);
    }

    private static SecretKeySpec parse(String base64Key) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Key.trim());
            return bytes.length == KEY_BYTES ? new SecretKeySpec(bytes, "AES") : null;
        } catch (IllegalArgumentException e) {
            return null; // 키 값은 로그에 남기지 않는다
        }
    }

    public boolean configured() {
        return key != null;
    }

    public String encrypt(byte[] secret) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(secret);
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("OTP 비밀값을 암호화하지 못했습니다", e);
        }
    }

    public byte[] decrypt(String stored) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(stored));
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] sealed = new byte[buffer.remaining()];
            buffer.get(sealed);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(sealed);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 키가 바뀌었거나 저장값이 변조됨: 메시지에 저장값을 싣지 않는다
            throw new IllegalStateException("OTP 비밀값을 풀지 못했습니다 (키가 바뀌었는지 확인)");
        }
    }

    private SecretKeySpec requireKey() {
        if (key == null) {
            throw new IllegalStateException("ADMIN_OTP_KEY 가 설정되지 않았습니다");
        }
        return key;
    }
}
