package com.tripdiary.user;

import java.util.UUID;
import jakarta.persistence.*;
import com.tripdiary.global.persistence.BaseTimeEntity;

@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 254, unique = true)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false, length = 40)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    protected User() {}

    public User(String email, String passwordHash, String nickname) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.status = UserStatus.ACTIVE;
        this.role = UserRole.USER;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public UserStatus getStatus() { return status; }
    public UserRole getRole() { return role; }
    public boolean isActive() { return status == UserStatus.ACTIVE; }
    public void changeNickname(String nickname) { this.nickname = nickname; }
}
