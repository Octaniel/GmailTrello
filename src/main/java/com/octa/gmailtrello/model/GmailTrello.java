package com.octa.gmailtrello.model;

import lombok.Data;
import lombok.Getter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "gmailTrello")
public class GmailTrello {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void antesSalvar(){
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
}
