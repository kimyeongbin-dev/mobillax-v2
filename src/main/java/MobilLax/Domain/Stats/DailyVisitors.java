package MobilLax.Domain.Stats;

import MobilLax.Common.Time.KoreaTime;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 오늘 처음 온 방문자인지 판단한다 (v2.13, 보안 규칙 S13)
 * - 쿠키를 쓰지 않는다. IP 와 브라우저 정보를 그날의 임의 값과 섞은 해시만 메모리에 두고, DB 에는 합계만 남긴다
 * - 날이 바뀌면 임의 값과 해시를 모두 버린다. 그래서 다른 날의 방문자와 이을 수 없다
 * - 앱을 다시 띄우면 그날 온 사람을 다시 세어 방문자가 조금 많게 나올 수 있다
 * - 해시 수에 상한을 두어 값을 바꿔 가며 보내는 요청이 메모리를 채우지 못하게 한다 (상한을 넘으면 그날은 더 세지 않는다)
 */
@Component
public class DailyVisitors {

    private static final int MAX_PER_DAY = 100_000;
    private static final int SALT_BYTES = 32;
    private static final int KEPT_HASH_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final int limit;
    private LocalDate day;
    private byte[] salt;
    private Set<ByteBuffer> seen = new HashSet<>();

    public DailyVisitors() {
        this(MAX_PER_DAY);
    }

    DailyVisitors(int limit) {
        this.limit = limit;
    }

    public boolean firstVisitToday(String clientIp, String userAgent) {
        return firstVisit(KoreaTime.today(), clientIp, userAgent);
    }

    synchronized boolean firstVisit(LocalDate today, String clientIp, String userAgent) {
        if (!today.equals(day)) {
            day = today;
            salt = new byte[SALT_BYTES];
            RANDOM.nextBytes(salt);
            seen = new HashSet<>();
        }
        if (seen.size() >= limit) {
            return false;
        }
        return seen.add(hash(clientIp, userAgent));
    }

    private ByteBuffer hash(String clientIp, String userAgent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update((clientIp + '\n' + userAgent).getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest.digest(), 0, KEPT_HASH_BYTES).slice();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
