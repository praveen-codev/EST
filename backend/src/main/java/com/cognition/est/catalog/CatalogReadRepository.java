package com.cognition.est.catalog;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Phase 0 read path over plain JDBC so the Flyway schema can be smoke-tested on both H2 and
 * PostgreSQL before the JPA layer lands in Phase 1A. Keywords are stored as a '|'-joined TEXT
 * column to stay portable across both databases.
 */
@Repository
public class CatalogReadRepository {

    private final JdbcClient jdbc;

    public CatalogReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CatalogItemDto> findByKind(String orgId, String kind, boolean includeArchived) {
        return jdbc.sql(
                        """
                        select id, kind, label, description, keywords, typical_minutes, archived, sort_order,
                               updated_by, updated_at
                          from catalog_items
                         where organization_id = :org and kind = :kind
                           and (:all = true or archived = false)
                         order by sort_order, label
                        """)
                .param("org", orgId)
                .param("kind", kind)
                .param("all", includeArchived)
                .query(
                        (rs, i) ->
                                new CatalogItemDto(
                                        rs.getString("id"),
                                        rs.getString("kind"),
                                        rs.getString("label"),
                                        rs.getString("description"),
                                        splitKeywords(rs.getString("keywords")),
                                        (Integer) rs.getObject("typical_minutes"),
                                        rs.getBoolean("archived"),
                                        rs.getInt("sort_order"),
                                        rs.getString("updated_by"),
                                        toInstant(rs.getTimestamp("updated_at"))))
                .list();
    }

    static List<String> splitKeywords(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static java.time.Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
