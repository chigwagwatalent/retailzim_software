package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "units_of_measure")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitOfMeasure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String abbreviation;

    @Column(name = "is_decimal")
    @Builder.Default
    private Boolean isDecimal = false;
}

