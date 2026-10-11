package com.cognition.est.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Catalog", description = "Admin-managed reference tables")
public class CatalogController {

    public static final Set<String> KINDS =
            Set.of("sessionType", "sessionOutcome", "technology", "useCaseType", "contactRole");

    private final CatalogReadRepository repo;

    public CatalogController(CatalogReadRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/{kind}")
    @Operation(summary = "List rows of one reference table for the caller's organization")
    public List<CatalogItemDto> list(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String kind,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        if (!KINDS.contains(kind)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Unknown catalog kind: " + kind);
        }
        return repo.findByKind(principal.getClaimAsString("org"), kind, includeArchived);
    }
}
