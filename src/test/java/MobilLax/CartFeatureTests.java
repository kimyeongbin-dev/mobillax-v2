package MobilLax;

import MobilLax.Domain.Cart.CartItemEntity;
import MobilLax.Domain.Cart.CartItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * v2.6 장바구니 화면: 서버가 만든 표시 값, 결제 가능 여부, 결제창 단위
 * 테스트 데이터는 트랜잭션으로 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = CartFeatureTests.USER)
class CartFeatureTests {

    static final String USER = "cart-test@test.local";
    private static final String FUTURE = "20991231";
    private static final String PAST = "202001010800";

    @Autowired
    MockMvc mvc;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    ObjectMapper objectMapper;

    private void item(String groupId, String mode, String start, String end, int fare, String departure, Boolean verified) {
        String label = "TRAIN".equals(mode) ? "기차" : "고속버스";
        cartItemRepository.save(CartItemEntity.builder()
                .email(USER).mode(mode).route(label + ":" + start + "-" + end + " · 일반 출발")
                .routeId("00001").routePayment(fare).startName(start).endName(end).departureTime(departure)
                .totalFareGroupId(groupId).isDeleted(false).fareVerified(verified).build());
    }

    private JsonNode list() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/api/cart/list")).andReturn().getResponse().getContentAsString());
    }

    /** DB 항목(이메일·내부 번호·확인 표시)은 내보내지 않고, 화면에 필요한 값만 서버가 만든다 (S10) */
    @Test
    void listSendsOnlyServerBuiltViewValues() throws Exception {
        String group = UUID.randomUUID().toString();
        item(group, "TRAIN", "서울", "대전", 23700, FUTURE + "0800", true);

        String body = mvc.perform(get("/api/cart/list")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(USER, "fareVerified", "\"id\"", "isDeleted", "\"mode\"", "routeId");

        JsonNode view = list().get(0);
        assertThat(view.get("groupId").asText()).isEqualTo(group);
        assertThat(view.get("title").asText()).isEqualTo("서울 → 대전");
        assertThat(view.get("departure").asText()).isEqualTo("12월 31일 08:00");
        assertThat(view.get("payable").asBoolean()).isTrue();
        assertThat(view.get("blockReason").asText()).isEmpty();
    }

    /** 결제창은 교통수단마다 한 번: 화면에 미리 보이는 단위가 결제 준비와 같아야 한다 */
    @Test
    void groupShowsPaymentWindowsPerTransportAndLegsInDepartureOrder() throws Exception {
        String group = UUID.randomUUID().toString();
        item(group, "EXPRESSBUS", "대전복합", "광주", 17000, FUTURE + "1100", true);
        item(group, "TRAIN", "서울", "대전", 23700, FUTURE + "0800", true);
        item(group, "TRAIN", "광주송정", "목포", 8400, FUTURE + "1500", true);

        JsonNode view = list().get(0);
        assertThat(view.get("title").asText()).isEqualTo("서울 → 목포");
        assertThat(view.get("totalFare").asInt()).isEqualTo(49100);
        assertThat(view.get("items")).extracting(i -> i.get("startName").asText())
                .containsExactly("서울", "대전복합", "광주송정");
        assertThat(view.get("payments")).hasSize(2);
        assertThat(view.get("payments")).extracting(p -> p.get("transport").asText() + " " + p.get("amount").asInt())
                .containsExactlyInAnyOrder("기차 32100", "고속·시외버스 17000");
    }

    /** 출발한 편이 있는 묶음: 화면이 미리 막고, 결제 준비도 같은 이유로 거절한다 (규칙은 CartPayability 한 곳) */
    @Test
    void departedGroupIsBlockedWithTheSameReasonAsPaymentPreparation() throws Exception {
        String group = UUID.randomUUID().toString();
        item(group, "TRAIN", "서울", "대전", 23700, PAST, true);

        JsonNode view = list().get(0);
        assertThat(view.get("payable").asBoolean()).isFalse();
        String reason = view.get("blockReason").asText();
        assertThat(reason).contains("출발 시각이 지났거나");

        MvcResult prepared = mvc.perform(post("/payment/sdk-ready/" + group).with(csrf())).andReturn();
        assertThat(prepared.getResponse().getStatus()).isEqualTo(400);
        assertThat(prepared.getResponse().getContentAsString()).contains(reason);
    }

    /** 담을 때 서버가 운임을 확인하지 않은 예전 항목은 결제할 수 없다 (S1) */
    @Test
    void unverifiedFareGroupIsBlocked() throws Exception {
        String group = UUID.randomUUID().toString();
        item(group, "TRAIN", "서울", "대전", 23700, FUTURE + "0800", null);

        JsonNode view = list().get(0);
        assertThat(view.get("payable").asBoolean()).isFalse();
        assertThat(view.get("blockReason").asText()).contains("운임을 확인하지 않은");
    }

    /** 결제할 수 있는 묶음을 먼저, 그 안에서는 출발이 이른 순 */
    @Test
    void payableGroupsComeFirstThenEarliestDeparture() throws Exception {
        String late = UUID.randomUUID().toString();
        String early = UUID.randomUUID().toString();
        String departed = UUID.randomUUID().toString();
        item(late, "TRAIN", "늦은역", "대전", 1000, FUTURE + "2000", true);
        item(departed, "TRAIN", "지난역", "대전", 1000, PAST, true);
        item(early, "TRAIN", "이른역", "대전", 1000, FUTURE + "0600", true);

        assertThat(list()).extracting(v -> v.get("groupId").asText()).containsExactly(early, late, departed);
    }

    /** v2.10: 묶음 삭제와 전체 비우기 모두 행을 지우지 않고 화면에서만 뺀다 (최근 이용 경로는 남는다) */
    @Test
    void deleteAndClearOnlyHideRows() throws Exception {
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        item(first, "TRAIN", "서울", "대전", 1000, FUTURE + "0800", true);
        item(second, "TRAIN", "대전", "부산", 1000, FUTURE + "1200", true);

        mvc.perform(delete("/api/cart/group/" + first).with(csrf()));
        assertThat(list()).extracting(v -> v.get("groupId").asText()).containsExactly(second);

        mvc.perform(delete("/api/cart/clear").with(csrf()));
        assertThat(list()).isEmpty();
        assertThat(cartItemRepository.findByEmailOrderByIdDesc(USER)).as("행은 남는다").hasSize(2);
    }
}
