package MobilLax.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaData {
    private RequestParameters requestParameters;
    private Plan plan;
}
