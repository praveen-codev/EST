package com.cognition.est.api;

import com.cognition.est.app.security.Principal;
import com.cognition.est.domain.model.CatalogItem;
import com.cognition.est.domain.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Catalog", description = "Admin-managed reference tables")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    public record WriteBody(
            @Size(max = 200) String label,
            String description,
            List<String> keywords,
            Integer typicalMinutes,
            Boolean archived) {
        CatalogService.Write toWrite() {
            return new CatalogService.Write(label, description, keywords, typicalMinutes, archived);
        }
    }

    public record ReorderBody(List<String> ids) {}

    @GetMapping("/{kind}")
    @Operation(summary = "List rows of one reference table")
    public List<CatalogItem> list(
            @PathVariable String kind,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(Principal.current().orgId(), kind, includeArchived);
    }

    @PostMapping("/{kind}")
    @Operation(summary = "Add a row (ADMIN), or any member with adhoc=true for an \"Other…\" entry")
    public CatalogItem create(
            @PathVariable String kind,
            @RequestParam(defaultValue = "false") boolean adhoc,
            @Valid @RequestBody WriteBody body) {
        Principal p = Principal.current();
        if (!adhoc && !p.isAdmin())
            throw new AccessDeniedException("Only admins edit reference tables");
        return service.create(p.orgId(), kind, body.toWrite(), p.memberId(), adhoc, Instant.now());
    }

    @PatchMapping("/{kind}/{id}")
    @Operation(summary = "Edit a row (ADMIN); If-Match carries the expected version")
    public CatalogItem update(
            @PathVariable String kind,
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody WriteBody body) {
        Principal p = Principal.current();
        if (!p.isAdmin()) throw new AccessDeniedException("Only admins edit reference tables");
        return service.update(
                p.orgId(),
                kind,
                id,
                body.toWrite(),
                Versions.parse(ifMatch),
                p.memberId(),
                Instant.now());
    }

    @PostMapping("/{kind}/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set the full sort order (ADMIN)")
    public void reorder(@PathVariable String kind, @RequestBody ReorderBody body) {
        Principal p = Principal.current();
        if (!p.isAdmin()) throw new AccessDeniedException("Only admins edit reference tables");
        service.reorder(p.orgId(), kind, body.ids());
    }
}
