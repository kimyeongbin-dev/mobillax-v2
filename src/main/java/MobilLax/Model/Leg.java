package MobilLax.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Leg {
    private String mode;
    private int sectionTime;
    private int distance;
    private StartEnd start;
    private StartEnd end;

    private String routeColor;
    private int type;
    private String route;
    private String routeId;
    private int service;
    private int routePayment;

    private List<Station> Lane;  // Optional, for TRAIN only
    private PassStopList passStopList;
    private PassShape passShape;
    private List<Step> steps;    // Optional, for WALK
}
