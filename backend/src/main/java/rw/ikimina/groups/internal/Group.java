package rw.ikimina.groups.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A savings group (ikimina). Rows are created only by the {@code create_group()} database
 * function (see V5), so this entity is never inserted through JPA.
 */
@Entity
@Table(name = "groups")
public class Group {

    @Id
    private Long id;

    @Column(name = "public_id", nullable = false, insertable = false, updatable = false)
    private UUID publicId;

    @Column(nullable = false)
    private String name;

    @Column(name = "registration_number")
    private String registrationNumber;

    private String phone;
    private String email;
    private String province;
    private String district;
    private String sector;
    private String cell;
    private String village;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_by", nullable = false, insertable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Group() {
    }

    /** Group profile fields an officer may edit. Null leaves a field unchanged; "" clears it. */
    record Profile(String name, String registrationNumber, String phone, String email, String province,
                   String district, String sector, String cell, String village) {
    }

    Profile profile() {
        return new Profile(name, registrationNumber, phone, email, province, district, sector, cell, village);
    }

    void updateProfile(Profile changes) {
        name = changes.name() == null ? name : changes.name();
        registrationNumber = merge(registrationNumber, changes.registrationNumber());
        phone = merge(phone, changes.phone());
        email = merge(email, changes.email());
        province = merge(province, changes.province());
        district = merge(district, changes.district());
        sector = merge(sector, changes.sector());
        cell = merge(cell, changes.cell());
        village = merge(village, changes.village());
    }

    private static String merge(String current, String change) {
        if (change == null) {
            return current;
        }
        return change.isBlank() ? null : change.trim();
    }

    Long getId() {
        return id;
    }

    UUID getPublicId() {
        return publicId;
    }

    String getName() {
        return name;
    }

    String getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
