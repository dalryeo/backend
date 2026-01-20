package com.ohgiraffers.dalryeo.analysis.service;

import com.ohgiraffers.dalryeo.analysis.dto.RecordDetailResponse;
import com.ohgiraffers.dalryeo.analysis.dto.RecordListItemResponse;
import com.ohgiraffers.dalryeo.analysis.dto.RecordListResponse;
import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import com.ohgiraffers.dalryeo.record.repository.RunningRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AnalysisService {

    private final RunningRecordRepository runningRecordRepository;

    /**
     * 전체 기록 조회 (페이징, 정렬, 기간 필터 지원)
     */
    @Transactional(readOnly = true)
    public RecordListResponse getRecords(Long userId, Integer page, String sort, String period) {
        // 정렬 설정
        Sort sortOption = Sort.by(Sort.Direction.DESC, "startAt");
        if ("latest".equalsIgnoreCase(sort)) {
            sortOption = Sort.by(Sort.Direction.DESC, "startAt");
        } else if ("oldest".equalsIgnoreCase(sort)) {
            sortOption = Sort.by(Sort.Direction.ASC, "startAt");
        } else if ("distance".equalsIgnoreCase(sort)) {
            sortOption = Sort.by(Sort.Direction.DESC, "distanceKm");
        }

        Pageable pageable = PageRequest.of(page - 1, 20, sortOption); // 페이지는 1부터 시작, 한 페이지당 20개

        Page<RunningRecord> recordPage;

        // 기간 필터 적용
        if ("monthly".equalsIgnoreCase(period)) {
            // 이번 달 기록만 조회
            LocalDate now = LocalDate.now();
            LocalDate monthStart = now.withDayOfMonth(1);
            LocalDate monthEnd = monthStart.plusMonths(1);
            
            LocalDateTime startDateTime = monthStart.atStartOfDay();
            LocalDateTime endDateTime = monthEnd.atStartOfDay();
            
            recordPage = runningRecordRepository.findByUserIdAndDateRange(
                    userId, startDateTime, endDateTime, pageable);
        } else {
            // 전체 기록 조회
            recordPage = runningRecordRepository.findByUserIdOrderByStartAtDesc(userId, pageable);
        }

        List<RecordListItemResponse> records = recordPage.getContent().stream()
                .map(record -> RecordListItemResponse.builder()
                        .recordId(record.getId())
                        .distanceKm(record.getDistanceKm())
                        .durationSec(record.getDurationSec())
                        .avgPaceSecPerKm(record.getAvgPaceSecPerKm())
                        .bpm(record.getAvgHeartRate())
                        .date(record.getStartAt().toLocalDate())
                        .build())
                .collect(Collectors.toList());

        return RecordListResponse.builder()
                .total(recordPage.getTotalElements())
                .records(records)
                .build();
    }

    /**
     * 기록 상세 조회
     */
    @Transactional(readOnly = true)
    public RecordDetailResponse getRecordDetail(Long userId, Long recordId) {
        RunningRecord record = runningRecordRepository.findByIdAndUserId(recordId, userId)
                .orElseThrow(() -> new RuntimeException("기록을 찾을 수 없습니다."));

        return RecordDetailResponse.builder()
                .recordId(record.getId())
                .platform(record.getPlatform())
                .distanceKm(record.getDistanceKm())
                .durationSec(record.getDurationSec())
                .avgPaceSecPerKm(record.getAvgPaceSecPerKm())
                .avgHeartRate(record.getAvgHeartRate())
                .caloriesKcal(record.getCaloriesKcal())
                .startAt(record.getStartAt())
                .endAt(record.getEndAt())
                .build();
    }
}

