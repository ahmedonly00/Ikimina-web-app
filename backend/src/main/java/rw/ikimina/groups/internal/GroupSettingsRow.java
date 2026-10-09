package rw.ikimina.groups.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The stored form of a group's bylaws: JSON validated against {@code schemaVersion}. */
@Entity
@Table(name = "group_settings")
public class GroupSettingsRow {

    @Id
    @Column(name = "group_id")
    private Long groupId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String settings;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected GroupSettingsRow() {
    }

    GroupSettingsRow(long groupId, String settingsJson, int schemaVersion, Instant now) {
        this.groupId = groupId;
        this.settings = settingsJson;
        this.schemaVersion = schemaVersion;
        this.updatedAt = now;
    }

    void replace(String settingsJson, int newSchemaVersion, Instant now) {
        settings = settingsJson;
        schemaVersion = newSchemaVersion;
        updatedAt = now;
    }

    String getSettings() {
        return settings;
    }

    int getSchemaVersion() {
        return schemaVersion;
    }

    long getVersion() {
        return version;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
