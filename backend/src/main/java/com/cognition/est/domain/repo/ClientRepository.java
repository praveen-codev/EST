package com.cognition.est.domain.repo;

import com.cognition.est.domain.model.Client;
import com.cognition.est.domain.model.Team;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClientRepository {
    private final JdbcClient jdbc;

    public ClientRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Client> findAll(String orgId) {
        Map<String, List<String>> domains = new LinkedHashMap<>();
        jdbc.sql(
                        "select d.client_id, d.domain from client_domains d join clients c on c.id = d.client_id where c.organization_id = :org order by d.domain")
                .param("org", orgId)
                .query(
                        (rs, i) ->
                                domains.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                                        .add(rs.getString(2)))
                .list();
        return jdbc.sql(
                        "select id, name, industry, hue from clients where organization_id = :org and archived = false order by name")
                .param("org", orgId)
                .query(
                        (rs, i) ->
                                new Client(
                                        rs.getString("id"),
                                        rs.getString("name"),
                                        rs.getString("industry"),
                                        List.copyOf(
                                                domains.getOrDefault(
                                                        rs.getString("id"), List.of())),
                                        rs.getInt("hue")))
                .list();
    }

    public Optional<Client> findById(String orgId, String id) {
        return findAll(orgId).stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public void insert(String orgId, Client c, String by, Instant now) {
        jdbc.sql(
                        "insert into clients (id, organization_id, name, industry, hue, archived, version, created_at, updated_at, updated_by) values (:id, :org, :name, :industry, :hue, false, 0, :now, :now, :by)")
                .param("id", c.id())
                .param("org", orgId)
                .param("name", c.name())
                .param("industry", c.industry())
                .param("hue", c.hue())
                .param("now", now)
                .param("by", by)
                .update();
        for (String d : c.domains()) {
            jdbc.sql("insert into client_domains (client_id, domain) values (:c, :d)")
                    .param("c", c.id())
                    .param("d", d.toLowerCase())
                    .update();
        }
    }

    public List<Team> findTeams(String orgId) {
        return jdbc.sql(
                        "select id, client_id, name, keywords from teams where organization_id = :org and archived = false order by name")
                .param("org", orgId)
                .query(
                        (rs, i) ->
                                new Team(
                                        rs.getString("id"),
                                        rs.getString("client_id"),
                                        rs.getString("name"),
                                        Rows.pipeList(rs.getString("keywords"))))
                .list();
    }

    public void insertTeam(String orgId, Team t, Instant now) {
        jdbc.sql(
                        "insert into teams (id, organization_id, client_id, name, keywords, archived, created_at, updated_at) values (:id, :org, :client, :name, :kw, false, :now, :now)")
                .param("id", t.id())
                .param("org", orgId)
                .param("client", t.clientId())
                .param("name", t.name())
                .param("kw", Rows.pipeJoin(t.keywords()))
                .param("now", now)
                .update();
    }
}
