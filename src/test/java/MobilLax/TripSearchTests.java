package MobilLax;

import MobilLax.Common.RateLimit.RateLimiter;
import MobilLax.Common.Time.KoreaTime;
import MobilLax.Domain.Trip.TagoTripClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 편 조회 규칙 (TripSearchService, v2.10 에 컨트롤러에서 옮김): 날짜 범위, 당일 예매, 공통 오류 응답
 * TAGO 는 가짜(MockBean)로 바꿔 외부 호출 없이 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TripSearchTests {

    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    MockMvc mvc;

    @Autowired
    RateLimiter rateLimiter;

    @MockBean
    TagoTripClient tripClient;

    @BeforeEach
    @AfterEach
    void clearCounts() {
        rateLimiter.clear();
    }

    private ResultActions search(String date) throws Exception {
        return mvc.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"TRAIN\",\"depId\":\"NAT010000\",\"arrId\":\"NAT011668\",\"date\":\"" + date + "\"}"));
    }

    @Test
    void todayTripsAreBookableOnlyBeforeDeparture() throws Exception {
        String today = KoreaTime.today().format(YMD);
        when(tripClient.trips(any(), anyString(), anyString(), anyString())).thenReturn(List.of(
                new TagoTripClient.Trip("2", "KTX", today + "2359", today + "2359", 23700),
                new TagoTripClient.Trip("1", "KTX", today + "0000", today + "0100", 23700),
                new TagoTripClient.Trip("3", "KTX", "", "", 23700)));

        search(today).andExpect(status().isOk())
                .andExpect(jsonPath("$.bookableDate").value(true))
                .andExpect(jsonPath("$.trips.length()").value(2))
                .andExpect(jsonPath("$.trips[0].no").value("1"))
                .andExpect(jsonPath("$.trips[0].bookable").value(false))
                .andExpect(jsonPath("$.trips[1].bookable").value(true));
    }

    @Test
    void laterDayIsViewOnly() throws Exception {
        String later = KoreaTime.today().plusDays(3).format(YMD);
        when(tripClient.trips(any(), anyString(), anyString(), anyString())).thenReturn(List.of(
                new TagoTripClient.Trip("1", "KTX", later + "0800", later + "0900", 23700)));

        search(later).andExpect(jsonPath("$.bookableDate").value(false))
                .andExpect(jsonPath("$.trips[0].bookable").value(false));
    }

    @Test
    void outOfRangeOrImpossibleDatesAreRejectedWithCommonErrorBody() throws Exception {
        LocalDate today = KoreaTime.today();
        search(today.minusDays(1).format(YMD)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("14일")));
        search(today.plusDays(15).format(YMD)).andExpect(status().isBadRequest());
        search("20261399").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("교통수단 또는 날짜가 올바르지 않습니다."));
    }

    @Test
    void tagoFailureIs502WithoutInternalDetails() throws Exception {
        when(tripClient.trips(any(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("https://apis.data.go.kr/?serviceKey=FAKE_secret"));

        search(KoreaTime.today().format(YMD)).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("편 정보를 불러오지 못했습니다."));
    }
}
