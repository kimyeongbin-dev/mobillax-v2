package MobilLax.Domain.Payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PortOne 상점·채널 (결제창 SDK 에 넘기는 식별자, 키가 아님, 보안 규칙 S9)
 * - 결제 준비는 이 값으로 결제창을 열고, 결제 확인은 PortOne 응답의 상점이 이 값과 같은지 본다 (S1)
 */
@Component
public class PortOneStore {

    private final String storeId;
    private final String channelKey;

    public PortOneStore(@Value("${portone_store}") String storeId,
                        @Value("${portone_channel_card}") String channelKey) {
        this.storeId = storeId;
        this.channelKey = channelKey;
    }

    public String storeId() {
        return storeId;
    }

    public String channelKey() {
        return channelKey;
    }
}
