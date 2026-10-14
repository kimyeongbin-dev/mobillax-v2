package MobilLax.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Itinerary {
    private Fare fare;
    private int totalTime;
    private List<Leg> legs;
    private int totalWalkTime;
    private int transferCount;
    private int totalDistance;
    private int pathType;
    private int totalWalkDistance;
}
