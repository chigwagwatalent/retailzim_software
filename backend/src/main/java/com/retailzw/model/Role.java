package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import com.retailzw.enums.UserRole;

@Entity
@Table(name = "roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private UserRole name;

    @Column(length = 255)
    private String description;

    @Column(name = "is_system_role")
    @Builder.Default
    private Boolean isSystemRole = false;

}

