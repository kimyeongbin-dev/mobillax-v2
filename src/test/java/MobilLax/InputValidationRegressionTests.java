package MobilLax;

import MobilLax.Domain.Cart.CartItemRepository;
import MobilLax.Domain.Cart.DepartureCheck;
import MobilLax.Domain.Payment.PaymentRepository;
import MobilLax.Domain.Place.TransitKind;
import MobilLax.Domain.Place.TransitPlace;
import MobilLax.Domain.Place.TransitPlaceRepository;
import MobilLax.Domain.Trip.TagoTripClient;
import MobilLax.Repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 보안 회귀 테스트 S10 — 브라우저에서 오는 값은 모두 서버가 검증한다 (docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * 개발자도구로 요청 본문·경로·폼 값을 바꾼 상황을 만들고, 서버가 거절하거나 서버 값으로 바꾸는지 확인한다.
 * TAGO 는 가짜(MockBean)로 바꾸고, 테스트 데이터는 트랜잭션으로 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InputValidationRegressionTests {

    private static final String USER = "input-test@test.local";
    private static final String DEP_ID = "TESTDEP02";
    private static final String ARR_ID = "TESTARR02";
    private static final String TRIP_NO = "00202";
    private static final int FARE = 8400;
    private static final String SCRIPT = "<script>alert(1)</script>";

    @Autowired
    MockMvc mvc;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    TransitPlaceRepository transitPlaceRepository;

    @Autowired
    UserAccountRepository userAccountRepository;

    @MockBean
    TagoTripClient tagoTripClient;

    private final String departAt = DepartureCheck.now().substring(0, 8) + "2359";

    @BeforeEach
    void stationsAndTrip() {
        transitPlaceRepository.save(new TransitPlace(TransitKind.TRAIN, DEP_ID, "검증출발역", "테스트시"));
        transitPlaceRepository.save(new TransitPlace(TransitKind.TRAIN, ARR_ID, "검증도착역", "테스트시"));
        when(tagoTripClient.trips(eq(TransitKind.TRAIN), eq(DEP_ID), eq(ARR_ID), anyString()))
                .thenReturn(List.of(new TagoTripClient.Trip(TRIP_NO, "무궁화호", departAt, departAt, FARE)));
    }

    /** 정상 구간 하나. 필드 하나만 바꿔 위조 요청을 만든다 */
    private String leg(String field, String jsonValue) {
        String base = """
                {"mode":"TRAIN","route":"기차","routeId":"%s","routePayment":%d,"startName":"출발","endName":"도착",
                "departureTime":"%s","kind":"TRAIN","depId":"%s","arrId":"%s"}"""
                .formatted(TRIP_NO, FARE, departAt, DEP_ID, ARR_ID).replace("\n", "");
        if (field == null) {
            return base;
        }
        return base.replaceFirst("\"" + field + "\":(\"[^\"]*\"|-?\\d+)", "\"" + field + "\":" + jsonValue);
    }

    private String legs(String... legs) {
        return "{\"selectedLegs\":[" + String.join(",", legs) + "]}";
    }

    private MvcResult postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    /** 장바구니 담기·바로 결제: 형식이 틀린 구간은 400 이고 아무것도 저장하지 않는다 */
    @Nested
    @WithMockUser(username = USER)
    class BookingRequests {

        private void assertRejectedEverywhere(String body) throws Exception {
            long payments = paymentRepository.count();
            MvcResult cart = postJson("/api/cart/add", body);
            MvcResult direct = postJson("/payment/direct-sdk-ready", body);
            assertThat(cart.getResponse().getStatus()).as("장바구니 담기").isEqualTo(400);
            assertThat(direct.getResponse().getStatus()).as("바로 결제").isEqualTo(400);
            assertThat(cartItemRepository.findByEmailAndIsDeletedFalse(USER)).isEmpty();
            assertThat(paymentRepository.count()).isEqualTo(payments);
            // 거절 응답에 보낸 값을 되돌려 싣지 않는다 (S5)
            assertThat(cart.getResponse().getContentAsString()).doesNotContain("<script", "ROCKET", "../");
        }

        @Test
        void tamperedFieldsAreRejected() throws Exception {
            assertRejectedEverywhere(legs(leg("kind", "\"ROCKET\"")));
            assertRejectedEverywhere(legs(leg("mode", "\"WALK\"")));
            assertRejectedEverywhere(legs(leg("depId", "\"../x\"")));
            assertRejectedEverywhere(legs(leg("arrId", "\"" + SCRIPT + "\"")));
            assertRejectedEverywhere(legs(leg("routeId", "\"<b>1</b>\"")));
            assertRejectedEverywhere(legs(leg("departureTime", "\"tomorrow\"")));
            assertRejectedEverywhere(legs(leg("routePayment", "-1")));
            assertRejectedEverywhere(legs(leg("routePayment", "0")));
            assertRejectedEverywhere(legs(leg("routePayment", "2000000")));
        }

        @Test
        void missingOrTooManyLegsAreRejected() throws Exception {
            assertRejectedEverywhere("{\"selectedLegs\":[]}");
            assertRejectedEverywhere("{}");
            assertRejectedEverywhere("{\"selectedLegs\":[null]}");
            String eleven = IntStream.range(0, 11).mapToObj(i -> leg(null, null)).collect(Collectors.joining(","));
            assertRejectedEverywhere("{\"selectedLegs\":[" + eleven + "]}");
        }

        @Test
        void unknownStationIsRejected() throws Exception {
            assertRejectedEverywhere(legs(leg("depId", "\"NOSUCH999\"")));
        }

        @Test
        void displayTextComesFromServerNotBrowser() throws Exception {
            String body = legs(leg(null, null)
                    .replace("\"route\":\"기차\"", "\"route\":\"" + SCRIPT.replace("\"", "\\\"") + "\"")
                    .replace("\"startName\":\"출발\"", "\"startName\":\"<img src=x onerror=alert(1)>\"")
                    .replace("\"endName\":\"도착\"", "\"endName\":\"관리자에게 문의\""));
            assertThat(postJson("/api/cart/add", body).getResponse().getStatus()).isEqualTo(200);

            assertThat(cartItemRepository.findByEmailAndIsDeletedFalse(USER)).singleElement().satisfies(item -> {
                assertThat(item.getStartName()).isEqualTo("검증출발역");
                assertThat(item.getEndName()).isEqualTo("검증도착역");
                assertThat(item.getRoute()).isEqualTo("기차:검증출발역-검증도착역 · 무궁화호 23:59 출발");
                assertThat(item.getRoutePayment()).isEqualTo(FARE);
            });
        }
    }

    /** 경로·결제 번호 같은 식별자는 서버가 만든 형식만 받는다 */
    @Nested
    @WithMockUser(username = USER)
    class Identifiers {

        @Test
        void cartGroupMustBeUuid() throws Exception {
            int status = mvc.perform(delete("/api/cart/group/1 OR 1=1").with(csrf())).andReturn().getResponse().getStatus();
            assertThat(status).isEqualTo(400);
        }

        @Test
        void paymentIdMustHaveServerFormat() throws Exception {
            for (String id : new String[]{"x' OR '1'='1", "", "payment-" + "a".repeat(200), SCRIPT}) {
                String body = "{\"paymentId\":\"" + id.replace("\"", "\\\"") + "\"}";
                assertThat(postJson("/payment/complete", body).getResponse().getStatus()).as("complete %s", id).isEqualTo(400);
                assertThat(postJson("/payment/fail", body).getResponse().getStatus()).as("fail %s", id).isEqualTo(400);
            }
            assertThat(postJson("/payment/complete", "{}").getResponse().getStatus()).isEqualTo(400);
        }

        @Test
        void unusedTerminalProxyIsGone() throws Exception {
            // 화면에서 쓰지 않으면서 TAGO 응답(XML)을 그대로 넘기던 API (v2.4 삭제)
            assertThat(mvc.perform(get("/api/terminals").param("name", "동서울")).andReturn().getResponse().getStatus())
                    .isEqualTo(404);
        }
    }

    /** 장소 매칭: 이름·좌표는 캐시 키로 저장되므로 길이·문자·범위를 제한한다 */
    @Nested
    @WithMockUser(username = USER)
    class PlaceMatch {

        private String point(String name, double lat, double lon) {
            return "{\"mode\":\"TRAIN\",\"name\":\"%s\",\"lat\":%s,\"lon\":%s}".formatted(name, lat, lon);
        }

        private int match(String... points) throws Exception {
            return postJson("/api/place/match", "{\"points\":[" + String.join(",", points) + "]}").getResponse().getStatus();
        }

        @Test
        void invalidPointsAreRejected() throws Exception {
            assertThat(match(point("가".repeat(41), 37.5, 127.0))).as("이름 41자").isEqualTo(400);
            assertThat(match(point("<b>서울</b>", 37.5, 127.0))).as("꺾쇠").isEqualTo(400);
            assertThat(match(point("서울", 0, 0))).as("국외 좌표").isEqualTo(400);
            assertThat(match(point(" ", 37.5, 127.0))).as("빈 이름").isEqualTo(400);
            assertThat(match()).as("빈 목록").isEqualTo(400);
            String[] many = IntStream.range(0, 21).mapToObj(i -> point("서울", 37.5, 127.0)).toArray(String[]::new);
            assertThat(match(many)).as("21개").isEqualTo(400);
            assertThat(match("{\"mode\":\"WALK\",\"name\":\"서울\",\"lat\":37.5,\"lon\":127.0}")).as("mode").isEqualTo(400);
        }
    }

    /** 회원가입: 폼 값도 서버가 검사한다 (브라우저의 maxlength·required 는 우회할 수 있다) */
    @Nested
    class Signup {

        private void register(String name, String email, String password) throws Exception {
            mvc.perform(post("/register").with(csrf())
                    .param("name", name).param("email", email).param("password", password));
        }

        @Test
        void weakOrOversizedValuesAreNotSaved() throws Exception {
            register("검증", "short-pw@test.local", "1234567");
            register("검증", "long-pw@test.local", "가".repeat(25)); // 75바이트: BCrypt 72바이트 초과
            register("검증", "empty-pw@test.local", "");
            register("검증", "a".repeat(96) + "@t.io", "FAKE_password1");
            register("가".repeat(31), "long-name@test.local", "FAKE_password1");

            for (String email : List.of("short-pw@test.local", "long-pw@test.local", "empty-pw@test.local",
                    "a".repeat(96) + "@t.io", "long-name@test.local")) {
                assertThat(userAccountRepository.findByEmail(email)).as(email).isEmpty();
            }
        }

        @Test
        void validSignupStillWorks() throws Exception {
            register("검증", "valid-signup@test.local", "FAKE_password1");
            assertThat(userAccountRepository.findByEmail("valid-signup@test.local")).isPresent();
        }
    }
}
