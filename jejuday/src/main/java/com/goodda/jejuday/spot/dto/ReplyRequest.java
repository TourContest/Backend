package com.goodda.jejuday.spot.dto;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
public class ReplyRequest {
    @NotBlank
    private String text;
}