package MobilLax.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 템플릿에 넣는 브라우저용 키 (보안 규칙 S9, docs/SECURITY_RULES.md)
 * - 브라우저로 보내도 되는 키는 카카오 JavaScript 키 하나뿐이다: 카카오 콘솔에 등록한 JavaScript SDK 도메인에서만 동작하도록 설계된 키
 * - TMAP·카카오 REST·TAGO·도로명주소·PortOne 비밀키는 서버 전용이라 여기에 추가하지 않는다 (KeyExposureRegressionTests 가 지킨다)
 */
@ControllerAdvice
public class FrontendKeyAdvice {

    @Value("${KAKAO_JS_APPKEY}")
    private String kakaoJsAppKey;

    @ModelAttribute("kakaoJsAppKey")
    public String kakaoJsAppKey() {
        return kakaoJsAppKey;
    }
}
