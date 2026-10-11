package com.cognition.est.domain.repo;

import com.cognition.est.domain.model.CatalogItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static final RowMapper<CatalogItem> MAPPER =
            (rs, i) ->
                    new CatalogItem(
                            rs.getString("id"),
                            rs.getString("kind"),
                            rs.getString("label"),
                            rs.getString("description"),
                            Rows.pipeList(rs.getString("keywords")),
                            Rows.integer(rs, "typical_minutes"),
                            rs.getBoolean("archived"),
                            rs.getInt("sort_order"),
                            rs.getInt("version"),
                            rs.getString("updated_by"),
                            Rows.instant(rs, "updated_at"));

    private static final String SELECT =
            "select id, kind, label, description, keywords, typical_minutes, archived, sort_order, version, updated_by, updated_at from catalog_items";

    public List<CatalogItem> findAll(String orgId) {
        return jdbc.sql(SELECT + " where organization_id = :org order by kind, sort_order, label")
                .param("org", orgId)
                .query(MAPPER)
                .list();
    }

    public List<CatalogItem> findByKind(String orgId, String kind, boolean includeArchived) {
        return jdbc.sql(
                        SELECT
                                + " where organization_id = :org and kind = :kind and (:all = true or archived = false) order by sort_order, label")
                .param("org", orgId)
                .param("kind", kind)
                .param("all", includeArchived)
                .query(MAPPER)
                .list();
    }

    public Optional<CatalogItem> findById(String orgId, String id) {
        return jdbc.sql(SELECT + " where organization_id = :org and id = :id")
                .param("org", orgId)
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    public boolean existsId(String orgId, String id) {
        return jdbc.sql(
                                "select count(*) from catalog_items where organization_id = :org and id = :id")
                        .param("org", orgId)
                        .param("id", id)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    public void insert(String orgId, CatalogItem c, Instant now) {
        jdbc.sql(
                        """
                insert into catalog_items (organization_id, id, kind, label, description, keywords, typical_minutes, archived, sort_order, version, created_at, updated_at, updated_by)
                values (:org, :id, :kind, :label, :desc, :kw, :min, :archived, :sort, 0, :now, :now, :by)
                """)
                .param("org", orgId)
                .param("id", c.id())
                .param("kind", c.kind())
                .param("label", c.label())
                .param("desc", c.description())
                .param("kw", Rows.pipeJoin(c.keywords()))
                .param("min", c.typicalMinutes())
                .param("archived", c.archived())
                .param("sort", c.sortOrder())
                .param("now", now)
                .param("by", c.updatedBy())
                .update();
    }

    /** Optimistic update; returns false when the stored version differs. */
    public boolean update(String orgId, CatalogItem c, int expectedVersion, Instant now) {
        return jdbc.sql(
                                """
                update catalog_items set label = :label, description = :desc, keywords = :kw, typical_minutes = :min, archived = :archived, sort_order = :sort,
                       version = version + 1, updated_at = :now, updated_by = :by
                 where organization_id = :org and id = :id and version = :v
                """)
                        .param("label", c.label())
                        .param("desc", c.description())
                        .param("kw", Rows.pipeJoin(c.keywords()))
                        .param("min", c.typicalMinutes())
                        .param("archived", c.archived())
                        .param("sort", c.sortOrder())
                        .param("now", now)
                        .param("by", c.updatedBy())
                        .param("org", orgId)
                        .param("id", c.id())
                        .param("v", expectedVersion)
                        .update()
                == 1;
    }

    public void setSortOrder(String orgId, String id, int sortOrder) {
        jdbc.sql(
                        "update catalog_items set sort_order = :s where organization_id = :org and id = :id")
                .param("s", sortOrder)
                .param("org", orgId)
                .param("id", id)
                .update();
    }
}
