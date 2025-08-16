package com.sun.transfer_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    @Column(name = "\"key\"", length = 200) // Quoted to avoid reserved word conflict
    private String key;

    @OneToOne(optional = false)
    private Transfer transfer;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
