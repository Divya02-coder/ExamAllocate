package com.examallocate.service;

import com.examallocate.dto.*;
import com.examallocate.entity.*;
import com.examallocate.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final StudentRepository studentRepository;
    private final ExamSessionRepository sessionRepository;
    private final RegistrationRepository registrationRepository;
    private final SpecialNeedsRequestRepository specialNeedsRepository;

    /**
     * Registers a candidate and generates an application number.
     */
    @Transactional
    public ApplyResponse apply(ApplyRequest req) {

        System.out.println("\n========================================");
        System.out.println("[REGISTRATION] Initialization started");
        System.out.println("[REGISTRATION] Course: " + req.courseName());
        System.out.println("[REGISTRATION] Processing registration...");
        
        try {
            String course = req.courseName().trim();

            ExamSession session = sessionRepository
                    .findFirstByExamNameOrderByExamDateAscStartTimeAsc(course)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "The selected course is not available for exam registration"
                    ));

            System.out.println("[REGISTRATION] Exam session found.");

            String appNo = generateApplicationNo();

            System.out.println("[REGISTRATION] Application number generated.");

            Student student = new Student();
            student.setApplicationNo(appNo);
            student.setName(req.name().trim());
            student.setDob(req.dob());
            student.setGender(req.gender().trim());
            student.setCategory(req.category().trim());
            student.setPhone(req.phone().trim());
            student.setEmail(req.email().trim().toLowerCase(Locale.ROOT));
            student.setAddress(req.address().trim());
            student.setCity(req.city().trim());
            student.setState(req.state().trim());
            student.setPincode(req.pincode().trim());
            student.setSchoolName(req.schoolName().trim());
            student.setPreferredCity1(req.preferredCity1().trim());
            student.setPreferredCity2(
                    req.preferredCity2() == null
                            || req.preferredCity2().isBlank()
                            ? null
                            : req.preferredCity2().trim()
            );

            student = studentRepository.save(student);

            System.out.println("[REGISTRATION] Student saved to database.");
            System.out.println("[REGISTRATION] Student ID: "
                    + student.getStudentId());

            Registration reg = new Registration();
            reg.setStudent(student);
            reg.setSession(session);
            reg.setRegDate(LocalDateTime.now());

            reg = registrationRepository.save(reg);

            System.out.println("[REGISTRATION] Registration saved to database.");
            System.out.println("[REGISTRATION] Registration ID: "
                    + reg.getRegId());

            if (req.accommodationType() != null
                    && !req.accommodationType().isBlank()) {

                SpecialNeedsRequest sn = new SpecialNeedsRequest();
                sn.setStudent(student);
                sn.setAccommodationType(req.accommodationType().trim());
                sn.setApprovedStatus("PENDING");

                specialNeedsRepository.save(sn);

                System.out.println(
                        "[REGISTRATION] Accommodation request saved."
                );
            }

            System.out.println("----------------------------------------");
            System.out.println("[REGISTRATION] REGISTRATION SUCCESSFUL");
            System.out.println("[REGISTRATION] Application Number: " + appNo);
            System.out.println("[REGISTRATION] Registration ID: "
                    + reg.getRegId());
            System.out.println("[REGISTRATION] Course: " + session.getExamName());
            System.out.println("[REGISTRATION] Exam Date: " + session.getExamDate());
            System.out.println("[REGISTRATION] Shift: " + session.getShiftName());
            System.out.println("[REGISTRATION] Transaction processing completed.");
            System.out.println("========================================\n");

            return new ApplyResponse(
                    reg.getRegId(),
                    student.getStudentId(),
                    appNo,
                    session.getExamName(),
                    session.getExamDate(),
                    session.getShiftName(),
                    session.getStartTime(),
                    session.getEndTime(),
                    "Registration successful. Your application number has been generated by the portal."
            );

        } catch (RuntimeException e) {
            System.err.println("[REGISTRATION] REGISTRATION FAILED");
            System.err.println("[REGISTRATION] Error: " + e.getMessage());
            throw e;
        }
    }

    private String generateApplicationNo() {
        String app;

        do {
            app = "NPTEL" + Year.now().getValue() + "-"
                    + UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 8)
                            .toUpperCase(Locale.ROOT);

        } while (studentRepository.findByApplicationNo(app).isPresent());

        return app;
    }

    @Transactional(readOnly = true)
    public RegistrationLookupResponse lookup(
            String applicationNo,
            String email) {

        System.out.println("[LOOKUP] Registration lookup started.");

        Registration r = registrationRepository
                .findFirstByStudent_ApplicationNoAndStudent_EmailIgnoreCase(
                        applicationNo.trim(),
                        email.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No registration found for the application number and email"
                ));

        System.out.println("[LOOKUP] Registration found. ID: " + r.getRegId());

        ExamSession s = r.getSession();

        return new RegistrationLookupResponse(
                r.getRegId(),
                r.getStudent().getApplicationNo(),
                r.getStudent().getName(),
                s.getExamName(),
                s.getExamDate(),
                s.getShiftName(),
                s.getStartTime(),
                s.getEndTime()
        );
    }

    @Transactional(readOnly = true)
    public Registration requireOwned(
            Long regId,
            String applicationNo) {

        System.out.println("[REGISTRATION] Verifying registration ownership.");

        Registration r = registrationRepository
                .findById(regId)
                .orElse(null);

        if (r == null
                || applicationNo == null
                || !r.getStudent().getApplicationNo()
                        .equals(applicationNo.trim())) {

            System.err.println(
                    "[REGISTRATION] Ownership verification failed."
            );

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No registration matches that application number"
            );
        }

        System.out.println(
                "[REGISTRATION] Ownership verification successful."
        );

        return r;
    }
}