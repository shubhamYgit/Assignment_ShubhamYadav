package org.shubh.assignment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bots")
public class Bot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "persona_description", nullable = false, length = 2000)
    private String personaDescription;

    protected Bot() {
    }

    public Bot(String name, String personaDescription) {
        this.name = name;
        this.personaDescription = personaDescription;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPersonaDescription() {
        return personaDescription;
    }
}
