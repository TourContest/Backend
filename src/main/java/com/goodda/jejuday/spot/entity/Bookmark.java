package com.goodda.jejuday.spot.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookmark")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Bookmark {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id")
    private Spot spot;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Bookmark(User user, Spot spot) {
        this.user = user;
        this.spot = spot;
        this.createdAt = LocalDateTime.now();
    }
}