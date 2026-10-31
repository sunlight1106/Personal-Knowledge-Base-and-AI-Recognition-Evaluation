package com.robustvision.platform.service;

import com.robustvision.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;

/** All callers hold app_user's write lock. No IP, password or raw identifier is stored. */
@Service
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = BusinessException.class)
public class LoginAttemptGuard {
    private static final int MAX_FAILURES = 8;
    private final JdbcTemplate db;
    public LoginAttemptGuard(JdbcTemplate db) { this.db = db; }

    public void requireAllowed(Long owner) {
        if (owner == null) return;
        var rows = db.queryForList("SELECT blocked_until FROM account_login_throttle WHERE owner_id=?", owner);
        if (!rows.isEmpty() && rows.get(0).get("blocked_until") != null
                && JdbcTime.instant(rows.get(0).get("blocked_until")).isAfter(Instant.now())) {
            // Same public response as an incorrect password; attempts during the cooldown do not extend it.
            throw failedLogin();
        }
    }

    public void failed(Long owner) {
        if (owner == null) return; // Unknown names cannot grow the table without bound.
        Instant now = Instant.now();
        var rows = db.queryForList("SELECT failures,window_started_at,blocked_until FROM account_login_throttle WHERE owner_id=?", owner);
        if (rows.isEmpty()) {
            db.update("INSERT INTO account_login_throttle(owner_id,failures,window_started_at) VALUES (?,1,?)", owner, Timestamp.from(now));
            return;
        }
        var row = rows.get(0);
        boolean expired = !JdbcTime.instant(row.get("window_started_at")).isAfter(now.minusSeconds(900))
                || row.get("blocked_until") != null && !JdbcTime.instant(row.get("blocked_until")).isAfter(now);
        int failures = expired ? 1 : ((Number) row.get("failures")).intValue() + 1;
        db.update("UPDATE account_login_throttle SET failures=?,window_started_at=?,blocked_until=? WHERE owner_id=?",
                failures, Timestamp.from(expired ? now : JdbcTime.instant(row.get("window_started_at"))),
                failures >= MAX_FAILURES ? Timestamp.from(now.plusSeconds(300)) : null, owner);
    }

    public void succeeded(Long owner) {
        db.update("DELETE FROM account_login_throttle WHERE owner_id=?", owner);
    }

    public static BusinessException failedLogin() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "用户名或密码错误");
    }
}
