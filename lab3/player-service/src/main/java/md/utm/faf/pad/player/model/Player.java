package md.utm.faf.pad.player.model;

import java.time.Instant;

/**
 * A registered player. This is the "row" we keep in storage.
 * The level is NOT stored: it is always derived from the XP (see {@link Progression}),
 * so the two values can never get out of sync.
 */
public class Player {

    private final String id;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final Instant createdAt;

    private int xp = 0;
    private String university = "FAF";
    private String title = "Freshman";
    private String avatar = "default";
    private boolean online = false;

    public Player(String id, String username, String email, String passwordHash) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public int getLevel() {
        return Progression.levelFor(xp);
    }

    // --- getters / setters ---------------------------------------------------

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }

    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }

    public String getUniversity() { return university; }
    public void setUniversity(String university) { this.university = university; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
}
