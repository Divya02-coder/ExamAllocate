package com.examallocate.service;

import com.examallocate.entity.*;
import com.examallocate.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsDashboardService {
    private final RegistrationRepository registrationRepository;
    private final SeatAllocationRepository seatAllocationRepository;
    private final ExamSessionRepository examSessionRepository;
    private final GrievanceRepository grievanceRepository;
    private final SpecialNeedsRequestRepository specialNeedsRequestRepository;
    private final RoomRepository roomRepository;
    private final ExamCenterRepository examCenterRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        List<Registration> registrations = registrationRepository.findAll();
        List<SeatAllocation> allocations = seatAllocationRepository.findAll();
        List<Grievance> grievances = grievanceRepository.findAll();
        List<SpecialNeedsRequest> accommodations = specialNeedsRequestRepository.findAll();
        List<ExamSession> sessions = examSessionRepository.findAll();

        Set<Long> allocatedRegIds = allocations.stream()
            .filter(a -> a.getRegistration() != null)
            .map(a -> a.getRegistration().getRegId()).collect(Collectors.toSet());

        Map<String, List<Registration>> byCourse = registrations.stream()
            .filter(r -> r.getSession() != null)
            .collect(Collectors.groupingBy(r -> safe(r.getSession().getExamName()), TreeMap::new, Collectors.toList()));

        List<Map<String, Object>> courses = new ArrayList<>();
        for (Map.Entry<String, List<Registration>> entry : byCourse.entrySet()) {
            List<Registration> courseRegs = entry.getValue();
            long seated = courseRegs.stream().filter(r -> allocatedRegIds.contains(r.getRegId())).count();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("course", entry.getKey());
            c.put("registrations", courseRegs.size());
            c.put("allocated", seated);
            c.put("pendingAllocation", courseRegs.size() - seated);
            c.put("percentage", registrations.isEmpty() ? 0.0 : round2(courseRegs.size() * 100.0 / registrations.size()));
            courses.add(c);
        }

        Map<String, Long> cityCounts = registrations.stream()
            .map(r -> r.getStudent() == null ? "Not specified" : safe(r.getStudent().getCity()))
            .collect(Collectors.groupingBy(s -> s.isBlank() ? "Not specified" : s, TreeMap::new, Collectors.counting()));
        List<Map<String, Object>> cities = cityCounts.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>(); m.put("city", e.getKey()); m.put("registrations", e.getValue()); return m;
        }).collect(Collectors.toList());

        Map<String, Long> grievanceCounts = grievances.stream()
            .collect(Collectors.groupingBy(g -> normalized(g.getStatus()), TreeMap::new, Collectors.counting()));
        Map<String, Long> accommodationCounts = accommodations.stream()
            .collect(Collectors.groupingBy(a -> normalized(a.getApprovedStatus()), TreeMap::new, Collectors.counting()));

        long capacity = roomRepository.findAll().stream().map(Room::getCapacity).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRegistrations", registrations.size());
        summary.put("totalCourses", sessions.stream().map(ExamSession::getExamName).filter(Objects::nonNull).distinct().count());
        summary.put("totalSessions", sessions.size());
        summary.put("totalAllocated", allocatedRegIds.size());
        summary.put("pendingAllocation", Math.max(0, registrations.size() - allocatedRegIds.size()));
        summary.put("totalGrievances", grievances.size());
        summary.put("openGrievances", grievances.stream().filter(g -> !isTerminal(g.getStatus())).count());
        summary.put("pendingAccommodations", accommodations.stream().filter(a -> "PENDING".equalsIgnoreCase(safe(a.getApprovedStatus()))).count());
        summary.put("totalRoomCapacity", capacity);
        summary.put("totalCenters", examCenterRepository.count());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("courses", courses);
        result.put("cities", cities);
        result.put("grievancesByStatus", toRows(grievanceCounts, "status"));
        result.put("accommodationsByStatus", toRows(accommodationCounts, "status"));
        result.put("generatedAt", java.time.OffsetDateTime.now().toString());
        return result;
    }

    private List<Map<String, Object>> toRows(Map<String, Long> values, String key) {
        return values.entrySet().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>(); row.put(key, e.getKey()); row.put("count", e.getValue()); return row;
        }).collect(Collectors.toList());
    }
    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static String normalized(String s) { return safe(s).isBlank() ? "UNKNOWN" : safe(s).toUpperCase(Locale.ROOT); }
    private static boolean isTerminal(String s) { return "RESOLVED".equalsIgnoreCase(safe(s)) || "REJECTED".equalsIgnoreCase(safe(s)); }
    private static double round2(double d) { return Math.round(d * 100.0) / 100.0; }
}
