package io.sentrius.sso.core.model;

import io.sentrius.sso.core.model.users.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "work_hours",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "dayOfWeek"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class WorkHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek; // 0 = Sunday, 6 = Saturday

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
