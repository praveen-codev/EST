package com.cognition.est.api;

import com.cognition.est.app.security.Principal;
import com.cognition.est.app.web.NotFoundException;
import com.cognition.est.audit.AuditRepository;
import com.cognition.est.auth.TokenService;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.repo.MemberRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final MemberRepository members;
    private final AuditRepository audit;
    private final TokenService tokens;

    public AdminController(MemberRepository members, AuditRepository audit, TokenService tokens) {
        this.members = members;
        this.audit = audit;
        this.tokens = tokens;
    }

    public record MemberPatch(String status, String role) {}

    @GetMapping("/members")
    public List<Member> members() {
        return members.findAll(Principal.current().orgId());
    }

    @PatchMapping("/members/{id}")
    public Member patch(@PathVariable String id, @RequestBody MemberPatch body) {
        Principal p = Principal.current();
        Member m =
                members.findById(id)
                        .filter(x -> x.orgId().equals(p.orgId()))
                        .orElseThrow(() -> new NotFoundException("No member " + id));
        String status = body.status() == null ? m.status() : body.status();
        String role = body.role() == null ? m.role() : body.role();
        if (!Set.of("PENDING", "ACTIVE", "SUSPENDED").contains(status)
                || !Set.of("MEMBER", "MANAGER", "ADMIN").contains(role))
            throw new IllegalArgumentException("Invalid status or role");
        if (m.id().equals(p.memberId()) && (!"ADMIN".equals(role) || !"ACTIVE".equals(status)))
            throw new IllegalArgumentException("You cannot demote or suspend yourself");
        Instant now = Instant.now();
        members.updateRoleStatus(id, role, status, now);
        if (!"ACTIVE".equals(status) || !role.equals(m.role())) tokens.revokeAll(id, now);
        audit.record(
                p.orgId(),
                p.memberId(),
                "member.updated",
                "member",
                id,
                Map.of("status", status, "role", role));
        return members.findById(id).orElseThrow();
    }
}
