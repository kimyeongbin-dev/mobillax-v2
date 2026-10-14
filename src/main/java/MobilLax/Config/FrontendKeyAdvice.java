package MobilLax.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 템플릿에서 사용하는 지도 SDK 키를 환경 변수에서 주입
 */
@ControllerAdvice
public class FrontendKeyAdvice {

    @Value("${TMAP_WEB_APPKEY}")
    private String tmapWebAppKey;

    @Value("${KAKAO_JS_APPKEY}")
    private String kakaoJsAppKey;

    @ModelAttribute("tmapWebAppKey")
    public String tmapWebAppKey() {
        return tmapWebAppKey;
    }

    @ModelAttribute("kakaoJsAppKey")
    public String kakaoJsAppKey() {
        return kakaoJsAppKey;
    }
}
