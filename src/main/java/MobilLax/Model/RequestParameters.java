package MobilLax.Model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class RequestParameters {
    private int busCount;
    private int expressbusCount;
    private int subwayCount;
    private int airplaneCount;
    private String locale;
    private String endY;
    private String endX;
    private int wideareaRouteCount;
    private int subwayBusCount;
    private String startY;
    private String startX;
    private int ferryCount;
    private int trainCount;
    private String reqDttm;
}
