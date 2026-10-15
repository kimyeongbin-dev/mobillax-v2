package MobilLax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling // 매칭 캐시 24시간 정리 (PlaceMatchCleanup)
public class MobilLaxApplication {

	public static void main(String[] args) {
		SpringApplication.run(MobilLaxApplication.class, args);
	}

}
