package MobilLax.Model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class Station {
    private int index;
    private String stationName;
    private String lon;
    private String lat;
    private String stationID;
}
