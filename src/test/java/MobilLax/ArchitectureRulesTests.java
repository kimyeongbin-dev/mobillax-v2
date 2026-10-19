package MobilLax;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코드 구조 규칙 (v2.8, CONTRIBUTING.md "코드 품질")
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * - 패키지: 공통 계층(Common), 설정(Config), 기능(Domain/<기능>)만 둔다. 예전의 Controller/Service/Model 같은
 *   계층별 패키지가 다시 생기면 기능 코드가 흩어지므로 실패한다
 */
class ArchitectureRulesTests {

    private static final String ROOT = "MobilLax";
    private static final Set<String> TOP_LEVEL_PACKAGES = Set.of("Common", "Config", "Domain");

    /** 앱의 모든 클래스 이름 (컴파일된 main 클래스, 테스트 클래스 제외) */
    static List<String> appClassNames() throws IOException {
        List<String> names = new ArrayList<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:" + ROOT + "/**/*.class")) {
            String path = resource.getURL().getPath();
            if (path.contains("/test/")) {
                continue;
            }
            String relative = path.substring(path.lastIndexOf("/" + ROOT + "/") + 1, path.length() - ".class".length());
            names.add(relative.replace('/', '.'));
        }
        return names;
    }

    @Test
    void classesLiveInCommonConfigOrDomain() throws IOException {
        List<String> names = appClassNames();
        assertThat(names).as("검사한 클래스").hasSizeGreaterThan(50);

        List<String> misplaced = names.stream()
                .filter(name -> !name.equals(ROOT + ".MobilLaxApplication"))
                .filter(name -> !TOP_LEVEL_PACKAGES.contains(name.split("\\.")[1]))
                .toList();
        assertThat(misplaced).as("Common·Config·Domain 밖에 있는 클래스").isEmpty();
    }
}
