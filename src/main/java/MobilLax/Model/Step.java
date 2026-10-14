package MobilLax.Model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class Step {
    private String streetName;
    private int distance;
    private String description;
    private String linestring;
}
