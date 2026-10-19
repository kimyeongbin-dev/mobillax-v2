package MobilLax;

import MobilLax.Domain.Place.KakaoLocalClient;
import MobilLax.Domain.Place.KakaoPlace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v2.7 홈 메뉴: 주변정보 API(카카오 로컬 대신 호출)와 요금비교·주변정보 화면
 * 카카오는 가짜(MockBean)로 바꿔 외부 호출 없이 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "menu-test@test.local")
class HomeMenuFeatureTests {

    private static final double LAT = 37.5547;
    private static final double LON = 126.9706;

    @Autowired
    MockMvc mvc;

    @MockBean
    KakaoLocalClient kakaoClient;

    @BeforeEach
    void configured() {
        when(kakaoClient.isConfigured()).thenReturn(true);
    }

    private org.springframework.test.web.servlet.ResultActions nearby(String category, String lat, String lon)
            throws Exception {
        return mvc.perform(get("/api/nearby").param("category", category).param("lat", lat).param("lon", lon));
    }

    /** 화면에는 서버가 만든 표시 값만: 좌표는 보내지 않고, 지도 링크는 숫자 장소 ID 로만 만든다 (S10) */
    @Test
    void nearbySendsOnlyServerBuiltValues() throws Exception {
        when(kakaoClient.categorySearch("CE7", LAT, LON, 1000, 15)).thenReturn(List.of(
                new KakaoPlace("12345", "역앞 카페", "음식점 > 카페 > 커피전문점", "서울 중구 한강대로 405", "서울 중구 봉래동2가 122",
                        "02-000-0000", 120),
                new KakaoPlace("javascript:alert(1)", "<b>이상한 곳</b>", "카페", "", "서울 용산구 동자동 43", "", 1530)));

        String body = nearby("CAFE", String.valueOf(LAT), String.valueOf(LON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("역앞 카페"))
                .andExpect(jsonPath("$[0].category").value("커피전문점"))
                .andExpect(jsonPath("$[0].address").value("서울 중구 한강대로 405"))
                .andExpect(jsonPath("$[0].distance").value("120m"))
                .andExpect(jsonPath("$[0].placeUrl").value("https://place.map.kakao.com/12345"))
                // 도로명 주소가 없으면 지번 주소, 장소 ID 가 숫자가 아니면 링크를 만들지 않는다
                .andExpect(jsonPath("$[1].address").value("서울 용산구 동자동 43"))
                .andExpect(jsonPath("$[1].distance").value("1.5km"))
                .andExpect(jsonPath("$[1].placeUrl").value(""))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("javascript:", "\"x\"", "\"y\"", "lat", "lon", "\"id\"");
    }

    /** 분류는 정해진 값만, 좌표는 국내 범위만: 틀리면 카카오를 부르지 않고 400 (S10) */
    @Test
    void nearbyRejectsUnknownCategoryAndForeignCoordinates() throws Exception {
        nearby("FD6", String.valueOf(LAT), String.valueOf(LON)).andExpect(status().isBadRequest());
        nearby("CAFE", "51.5", String.valueOf(LON)).andExpect(status().isBadRequest());
        nearby("CAFE", String.valueOf(LAT), "-0.12").andExpect(status().isBadRequest());
        nearby("CAFE", "abc", String.valueOf(LON)).andExpect(status().isBadRequest());
        verify(kakaoClient, never()).categorySearch(anyString(), anyDouble(), anyDouble(), anyInt(), anyInt());
    }

    /** 카카오 장애: 내부 정보 없이 안내 문구만 (S5) */
    @Test
    void nearbyHidesKakaoFailureDetails() throws Exception {
        when(kakaoClient.categorySearch(anyString(), anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenThrow(new ResourceAccessException("I/O error on GET request for https://dapi.kakao.com/v2/local"));

        String body = nearby("RESTAURANT", String.valueOf(LAT), String.valueOf(LON))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("주변정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("dapi.kakao.com", "ResourceAccessException");
    }

    @Test
    void nearbyWithoutKakaoKeyIsUnavailable() throws Exception {
        when(kakaoClient.isConfigured()).thenReturn(false);
        nearby("CAFE", String.valueOf(LAT), String.valueOf(LON)).andExpect(status().isServiceUnavailable());
        verify(kakaoClient, never()).categorySearch(anyString(), anyDouble(), anyDouble(), anyInt(), anyInt());
    }

    /** 분류 버튼은 서버의 분류 목록으로 그린다 (화면과 서버가 다른 목록을 갖지 않게) */
    @Test
    void nearbyPageDrawsCategoriesFromServerList() throws Exception {
        mvc.perform(get("/nearby"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-category=\"RESTAURANT\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-category=\"PARKING\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">관광명소<")));
    }

    /** 요금비교는 브라우저의 마지막 검색 결과만 쓴다: 화면은 서버 호출 없이 열린다 */
    @Test
    void comparePageOpens() throws Exception {
        mvc.perform(get("/compare"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/javascript/compare.js")));
    }
}
