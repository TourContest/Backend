package com.goodda.jejuday.spot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplyPageResponse {
    private List<ReplyResponse> content;
    private long totalElements;
    private boolean hasNext;
}