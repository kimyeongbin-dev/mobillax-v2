/**
 * FrontendConfigController.java
 *
 * ✅ 파일 목적: 정적 페이지(map.html 등)가 사용하는 Tmap 키를 .env 값으로 내려주는 config.js 를 동적으로 생성합니다.
 *              키를 정적 파일에 두지 않고 .env 한 곳에서만 관리하기 위함입니다.
 */

package MobilLax.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FrontendConfigController {

    @Value("${TMAP_WEB_APPKEY}")
    private String tmapWebAppKey;

    @GetMapping(value = "/module/config.js", produces = "application/javascript;charset=UTF-8")
    public ResponseEntity<String> configJs() {
        String escaped = tmapWebAppKey.replace("\\", "\\\\").replace("\"", "\\\"");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body("window.TMAP_APPKEY = \"" + escaped + "\";\n");
    }
}
