package MobilLax;

import MobilLax.Common.Time.KoreaTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling // 매칭 캐시 24시간 정리 (PlaceMatchCleanup)
public class MobilLaxApplication {

	static {
		// 날짜·시각은 한국 시간 기준 (테스트도 이 클래스를 불러오므로 함께 적용된다)
		KoreaTime.applyAsDefault();
	}

	public static void main(String[] args) {
		SpringApplication.run(MobilLaxApplication.class, args);
	}

}
