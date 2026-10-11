package com.cognition.est.auth;

import com.cognition.est.app.config.EstProperties;
import com.cognition.est.audit.AuditRepository;
import com.cognition.est.domain.model.Ids;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.repo.MemberRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Member resolution for a verified Google identity: domain allow-list, bootstrap admins, pending
 * approval.
 */
@Service
public class AuthService {
    public static final String ORG = "org_cognition";

    private final MemberRepository members;
    private final EstProperties props;
    private final AuditRepository audit;

    public AuthService(MemberRepository members, EstProperties props, AuditRepository audit) {
        this.members = members;
        this.props = props;
        this.audit = audit;
    }

    public static class SignInRejected extends RuntimeException {
        public final String reason;

        public SignInRejected(String reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    /**
     * Not transactional on purpose: a PENDING member must persist even though sign-in is then
     * rejected.
     */
    public Member resolve(GoogleIdentity g, Instant now) {
        if (!g.emailVerified())
            throw new SignInRejected("unverified", "Google account email is not verified.");
        String email = g.email().toLowerCase(Locale.ROOT);
        String domain = email.substring(email.indexOf('@') + 1);
        var allowed =
                props.allowedDomains().stream()
                        .map(d -> d.trim().toLowerCase(Locale.ROOT))
                        .filter(d -> !d.isEmpty())
                        .toList();
        if (!allowed.isEmpty() && !allowed.contains(domain))
            throw new SignInRejected("domain", "Accounts from " + domain + " cannot sign in here.");

        Member m =
                members.findByGoogleSub(g.sub()).or(() -> members.findByEmail(email)).orElse(null);
        if (m == null) {
            boolean bootstrapAdmin =
                    props.bootstrapAdminEmails().stream()
                            .map(e -> e.trim().toLowerCase(Locale.ROOT))
                            .anyMatch(email::equals);
            m =
                    new Member(
                            Ids.fresh("mem"),
                            ORG,
                            email,
                            g.name() == null ? email : g.name(),
                            null,
                            bootstrapAdmin ? "ADMIN" : "MEMBER",
                            bootstrapAdmin ? "ACTIVE" : "PENDING",
                            Math.floorMod(email.hashCode(), 360),
                            "UTC");
            members.insert(m, g.sub(), now);
            audit.record(
                    ORG,
                    m.id(),
                    "member.created",
                    "member",
                    m.id(),
                    Map.of("status", m.status(), "role", m.role()));
        } else {
            members.recordSignIn(m.id(), g.sub(), g.name() == null ? m.name() : g.name(), now);
        }
        if (!"ACTIVE".equals(m.status()))
            throw new SignInRejected(
                    m.status().toLowerCase(Locale.ROOT),
                    "PENDING".equals(m.status())
                            ? "Your account is waiting for an admin to approve it."
                            : "Your account is suspended.");
        audit.record(ORG, m.id(), "member.signed_in", "member", m.id(), Map.of());
        return m;
    }
}
