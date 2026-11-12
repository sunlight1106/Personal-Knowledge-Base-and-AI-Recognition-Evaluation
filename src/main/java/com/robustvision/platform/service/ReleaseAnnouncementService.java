package com.robustvision.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robustvision.platform.common.BusinessException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.List;

/** Bundled release notes and account-owned, idempotent read receipts. */
@Service
public class ReleaseAnnouncementService {
    public record Text(String zh, String en) {}
    public record Release(String version, String date, Text title, List<Text> added,
                          List<Text> fixed, List<Text> improved, List<Text> notes) {}
    public record Notice(Release release, boolean read) {}
    private final Release release;
    private final JdbcTemplate db;
    private final CurrentUserService current;
    private final LiveUpdateService live;

    public ReleaseAnnouncementService(ObjectMapper mapper, JdbcTemplate db,
                                      CurrentUserService current, LiveUpdateService live) throws IOException {
        this.db = db; this.current = current; this.live = live;
        try (var input = new ClassPathResource("release-announcement.json").getInputStream()) {
            release = mapper.readValue(input, Release.class);
        }
    }

    @Transactional(readOnly = true)
    public Notice get() { return get(current.requireCurrent().getId()); }

    public Notice get(long owner) {
        boolean read = db.queryForObject("SELECT COUNT(*) FROM release_read_receipt WHERE owner_id=? AND release_version=?",
                Long.class, owner, release.version()) > 0;
        return new Notice(release, read);
    }

    @Transactional
    public void read(String version) {
        if (!release.version().equals(version))
            throw new BusinessException(HttpStatus.CONFLICT, "RELEASE_CHANGED", "公告已更新，请重新查看最新公告");
        long owner = current.requireCurrent().getId();
        // Serialize concurrent receipts using the existing account row, on H2 and MySQL alike.
        db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", Long.class, owner);
        if (!get(owner).read()) {
            db.update("INSERT INTO release_read_receipt(owner_id,release_version,read_at) VALUES (?,?,CURRENT_TIMESTAMP)", owner, version);
            live.changed(List.of(owner));
        }
    }
}
