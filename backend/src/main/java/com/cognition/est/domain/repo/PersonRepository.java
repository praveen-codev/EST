package com.cognition.est.domain.repo;

import com.cognition.est.domain.model.Person;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PersonRepository {
    private final JdbcClient jdbc;

    public PersonRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static final RowMapper<Person> MAPPER =
            (rs, i) ->
                    new Person(
                            rs.getString("id"),
                            rs.getString("client_id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("role_id"));

    public List<Person> findAll(String orgId) {
        return jdbc.sql(
                        "select id, client_id, name, email, role_id from people where organization_id = :org order by name")
                .param("org", orgId)
                .query(MAPPER)
                .list();
    }

    public boolean exists(String id) {
        return jdbc.sql("select count(*) from people where id = :id")
                        .param("id", id)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    public void insert(String orgId, Person p, Instant now) {
        jdbc.sql(
                        "insert into people (id, organization_id, client_id, name, email, role_id, created_at, updated_at) values (:id, :org, :client, :name, :email, :role, :now, :now)")
                .param("id", p.id())
                .param("org", orgId)
                .param("client", p.clientId())
                .param("name", p.name())
                .param("email", p.email() == null ? null : p.email().toLowerCase())
                .param("role", p.roleId())
                .param("now", now)
                .update();
    }

    public void insertMissing(String orgId, List<Person> people, Instant now) {
        for (Person p : people) if (!exists(p.id())) insert(orgId, p, now);
    }

    public void attachClient(List<String> personIds, String clientId, Instant now) {
        for (String id : personIds) {
            jdbc.sql(
                            "update people set client_id = :c, updated_at = :now where id = :id and client_id is null")
                    .param("c", clientId)
                    .param("now", now)
                    .param("id", id)
                    .update();
        }
    }
}
