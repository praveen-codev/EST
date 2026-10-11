package com.cognition.est.api;

import com.cognition.est.app.config.EstProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, non-sensitive runtime flags the sign-in page needs before anyone is authenticated. */
@RestController
@RequestMapping("/api/v1/features")
@Tag(name = "Me")
public class FeaturesController {
    private final EstProperties props;

    public FeaturesController(EstProperties props) {
        this.props = props;
    }

    @GetMapping
    @Operation(summary = "Dev mode, mock Google and the public OAuth client id")
    public Map<String, Object> features() {
        return Map.of(
                "devMode",
                props.dev().endpointsEnabled(),
                "mockGoogle",
                props.google().mock(),
                "googleClientId",
                props.google().clientId());
    }
}
