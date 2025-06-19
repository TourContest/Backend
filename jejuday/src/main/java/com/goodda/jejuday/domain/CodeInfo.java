package com.goodda.jejuday.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "CodeInfo")
@Getter
@Setter
public class CodeInfo {
    @EmbeddedId
    private CodeInfoId id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "description", length = 255)
    private String description;

    @Embeddable
    @Getter @Setter
    public static class CodeInfoId implements Serializable {
        @Column(name = "code_type", nullable = false, length = 50)
        private String codeType;
        
        @Column(name = "code", nullable = false, length = 50)
        private String code;

        // equals(), hashCode() 구현
    }
}