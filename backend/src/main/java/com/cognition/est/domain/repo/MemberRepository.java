package com.cognition.est.domain.repo;

import com.cognition.est.domain.model.Member;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {
    private final JdbcClient jdbc;

    public MemberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static final RowMapper<Member> MAPPER =
            (rs, i) ->
                    new Member(
                            rs.getString("id"),
                            rs.getString("organization_id"),
                            rs.getString("email"),
                            rs.getString("name"),
                            rs.getString("title"),
                            rs.getString("role"),
                            rs.getString("status"),
                            rs.getInt("hue"),
                            rs.getString("time_zone"));

    private static final String SELECT =
            "select id, organization_id, email, name, title, role, status, hue, time_zone from members";

    public List<Member> findAll(String orgId) {
        return jdbc.sql(SELECT + " where organization_id = :org order by name")
                .param("org", orgId)
                .query(MAPPER)
                .list();
    }

    public Optional<Member> findById(String id) {
        return jdbc.sql(SELECT + " where id = :id").param("id", id).query(MAPPER).optional();
    }

    public Optional<Member> findByEmail(String email) {
        return jdbc.sql(SELECT + " where lower(email) = :email")
                .param("email", email.toLowerCase())
                .query(MAPPER)
                .optional();
    }

    public Optional<Member> findByGoogleSub(String sub) {
        return jdbc.sql(SELECT + " where google_sub = :sub")
                .param("sub", sub)
                .query(MAPPER)
                .optional();
    }

    public void insert(Member m, String googleSub, Instant now) {
        jdbc.sql(
                        """
                insert into members (id, organization_id, google_sub, email, name, title, role, status, hue, time_zone, last_sign_in_at, created_at, updated_at)
                values (:id, :org, :sub, :email, :name, :title, :role, :status, :hue, :tz, :now, :now, :now)
                """)
                .param("id", m.id())
                .param("org", m.orgId())
                .param("sub", googleSub)
                .param("email", m.email().toLowerCase())
                .param("name", m.name())
                .param("title", m.title())
                .param("role", m.role())
                .param("status", m.status())
                .param("hue", m.hue())
                .param("tz", m.timeZone())
                .param("now", now)
                .update();
    }

    public void updateRoleStatus(String id, String role, String status, Instant now) {
        jdbc.sql(
                        "update members set role = :role, status = :status, updated_at = :now where id = :id")
                .param("role", role)
                .param("status", status)
                .param("now", now)
                .param("id", id)
                .update();
    }

    public void recordSignIn(String id, String googleSub, String name, Instant now) {
        jdbc.sql(
                        "update members set google_sub = coalesce(:sub, google_sub), name = :name, last_sign_in_at = :now, updated_at = :now where id = :id")
                .param("sub", googleSub)
                .param("name", name)
                .param("now", now)
                .param("id", id)
                .update();
    }
}
