package com.ohgiraffers.dalryeo.record.repository;

import com.ohgiraffers.dalryeo.record.entity.RunningRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RunningRecordRepository extends JpaRepository<RunningRecord, Long> {
    
    Page<RunningRecord> findByUserIdOrderByStartAtDesc(Long userId, Pageable pageable);
    
    @Query("SELECT r FROM RunningRecord r WHERE r.userId = :userId " +
           "AND r.startAt >= :startDate AND r.startAt < :endDate " +
           "ORDER BY r.startAt DESC")
    Page<RunningRecord> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
    
    Optional<RunningRecord> findByIdAndUserId(Long id, Long userId);
    
    @Query("SELECT r FROM RunningRecord r WHERE r.userId = :userId " +
           "AND r.startAt >= :startDate AND r.startAt < :endDate " +
           "ORDER BY r.startAt DESC")
    List<RunningRecord> findByUserIdAndWeekRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT COUNT(r) > 0 FROM RunningRecord r WHERE r.userId = :userId " +
           "AND r.startAt >= :startDate AND r.startAt < :endDate")
    boolean existsByUserIdAndWeekRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT r FROM RunningRecord r WHERE r.userId = :userId " +
           "AND DATE(r.startAt) = :date " +
           "ORDER BY r.startAt DESC")
    List<RunningRecord> findByUserIdAndDate(
            @Param("userId") Long userId,
            @Param("date") LocalDate date
    );

    @Query("SELECT r FROM RunningRecord r WHERE r.startAt >= :startDate AND r.startAt < :endDate " +
           "ORDER BY r.startAt DESC")
    List<RunningRecord> findByWeekRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    void deleteByUserId(Long userId);
}

