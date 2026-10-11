package com.cognition.est.domain.service;

import com.cognition.est.app.web.ConflictException;
import com.cognition.est.app.web.NotFoundException;
import com.cognition.est.domain.model.CatalogItem;
import com.cognition.est.domain.repo.CatalogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Port of the catalogue operations in web/lib/domain/ops.ts (upsert, reorder, "Other…" reuse). */
@Service
public class CatalogService {
    public static final Set<String> KINDS =
            Set.of("sessionType", "sessionOutcome", "technology", "useCaseType", "contactRole");

    private final CatalogRepository repo;

    public CatalogService(CatalogRepository repo) {
        this.repo = repo;
    }

    public record Write(
            String label,
            String description,
            List<String> keywords,
            Integer typicalMinutes,
            Boolean archived) {}

    public static void requireKind(String kind) {
        if (!KINDS.contains(kind)) throw new NotFoundException("Unknown catalog kind: " + kind);
    }

    public List<CatalogItem> list(String orgId, String kind, boolean includeArchived) {
        requireKind(kind);
        return repo.findByKind(orgId, kind, includeArchived);
    }

    static String slugify(String label) {
        String[] words =
                label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].isEmpty()) continue;
            sb.append(
                    i == 0
                            ? words[i]
                            : Character.toUpperCase(words[i].charAt(0)) + words[i].substring(1));
        }
        return sb.isEmpty() ? "item" : sb.toString();
    }

    private String newId(String orgId, String label) {
        String id = slugify(label);
        while (repo.existsId(orgId, id)) id = id + "2";
        return id;
    }

    /**
     * Create a row. With {@code adhoc}, a same-label row (case-insensitive) is reused and
     * un-archived instead of duplicated.
     */
    @Transactional
    public CatalogItem create(
            String orgId, String kind, Write w, String by, boolean adhoc, Instant now) {
        requireKind(kind);
        String label = w.label() == null ? "" : w.label().trim();
        if (label.isEmpty()) throw new IllegalArgumentException("label is required");
        if (adhoc) {
            var existing =
                    repo.findByKind(orgId, kind, true).stream()
                            .filter(c -> c.label().trim().equalsIgnoreCase(label))
                            .findFirst();
            if (existing.isPresent()) {
                CatalogItem e = existing.get();
                if (e.archived()) {
                    CatalogItem restored =
                            new CatalogItem(
                                    e.id(),
                                    e.kind(),
                                    e.label(),
                                    e.description(),
                                    e.keywords(),
                                    e.typicalMinutes(),
                                    false,
                                    e.sortOrder(),
                                    e.version(),
                                    by,
                                    now);
                    repo.update(orgId, restored, e.version(), now);
                    return repo.findById(orgId, e.id()).orElseThrow();
                }
                return e;
            }
        }
        Set<String> keywords = new LinkedHashSet<>();
        keywords.add(label.toLowerCase(Locale.ROOT));
        if (w.keywords() != null)
            w.keywords().stream()
                    .map(k -> k.trim().toLowerCase(Locale.ROOT))
                    .filter(k -> !k.isEmpty())
                    .forEach(keywords::add);
        int sort = repo.findByKind(orgId, kind, true).size();
        Integer minutes =
                "sessionType".equals(kind)
                        ? (w.typicalMinutes() == null ? 60 : w.typicalMinutes())
                        : null;
        CatalogItem row =
                new CatalogItem(
                        newId(orgId, label),
                        kind,
                        label,
                        w.description() == null ? "" : w.description(),
                        new ArrayList<>(keywords),
                        minutes,
                        false,
                        sort,
                        0,
                        by,
                        now);
        repo.insert(orgId, row, now);
        return repo.findById(orgId, row.id()).orElseThrow();
    }

    @Transactional
    public CatalogItem update(
            String orgId,
            String kind,
            String id,
            Write w,
            Integer expectedVersion,
            String by,
            Instant now) {
        requireKind(kind);
        CatalogItem c =
                repo.findById(orgId, id)
                        .filter(x -> x.kind().equals(kind))
                        .orElseThrow(() -> new NotFoundException("No " + kind + " row " + id));
        int expected = expectedVersion == null ? c.version() : expectedVersion;
        List<String> keywords =
                w.keywords() == null
                        ? c.keywords()
                        : new ArrayList<>(
                                new LinkedHashSet<>(
                                        w.keywords().stream()
                                                .map(k -> k.trim().toLowerCase(Locale.ROOT))
                                                .filter(k -> !k.isEmpty())
                                                .toList()));
        CatalogItem next =
                new CatalogItem(
                        c.id(),
                        c.kind(),
                        w.label() == null || w.label().isBlank() ? c.label() : w.label().trim(),
                        w.description() == null ? c.description() : w.description(),
                        keywords,
                        w.typicalMinutes() == null ? c.typicalMinutes() : w.typicalMinutes(),
                        w.archived() == null ? c.archived() : w.archived(),
                        c.sortOrder(),
                        c.version(),
                        by,
                        now);
        if (!repo.update(orgId, next, expected, now))
            throw new ConflictException("Row was changed by someone else; reload and retry.");
        return repo.findById(orgId, id).orElseThrow();
    }

    @Transactional
    public void reorder(String orgId, String kind, List<String> ids) {
        requireKind(kind);
        int i = 0;
        for (String id : ids) repo.setSortOrder(orgId, id, i++);
    }
}
