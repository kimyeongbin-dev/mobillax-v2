package MobilLax.Model;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class RegularFare {
    private int totalFare;
    private Currency currency;
}
