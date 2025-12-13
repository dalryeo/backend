package com.ohgiraffers.dalryeo.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordListResponse {
    private Long total;
    private List<RecordListItemResponse> records;
}

