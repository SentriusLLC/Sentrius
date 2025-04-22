package io.sentrius.sso.core.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import io.sentrius.sso.core.model.WorkHours;
import io.sentrius.sso.core.repository.WorkHoursRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkHoursService {

    private final WorkHoursRepository workHoursRepository;

    public boolean isUserWithinWorkHours(Long userId) {
        List<WorkHours> workHoursList = workHoursRepository.findWorkHoursByTime(userId);

        int today = LocalDate.now().getDayOfWeek().getValue() % 7; // Ensures 0 = Sunday, 6 = Saturday

        return workHoursList.stream().anyMatch(w -> w.getDayOfWeek() == today);
    }

    public List<WorkHours> getWorkHoursForUser(Long userId) {
        return workHoursRepository.findByUserId(userId);
    }
}