package de.frauas.girokonto.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Ein gespeicherter Empfänger im Adressbuch eines Nutzers (/F220/). */
@Entity
@Table(name = "contact")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "contact_user_id", nullable = false)
    private Long contactUserId;

    private String nickname;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Contact() {
    }

    public Contact(Long ownerId, Long contactUserId, String nickname, Instant createdAt) {
        this.ownerId = ownerId;
        this.contactUserId = contactUserId;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public Long getContactUserId() { return contactUserId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Instant getCreatedAt() { return createdAt; }
}
