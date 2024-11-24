package io.dataguardians.sso.core.data.specification;

import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.hostgroup.HostGroup;
import lombok.NonNull;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class HostGroupSpecification {

    public static Specification<HostGroup> findByUserIdAndOptionalFilters(@NonNull Long userId,
                                                                          String enclaveName) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Join the users to filter host groups by user ID
            Join<HostGroup, User> userJoin = root.join("users", JoinType.INNER);
            predicates.add(criteriaBuilder.equal(userJoin.get("id"), userId));

            // Optionally filter by enclave name if provided
            if (enclaveName != null && !enclaveName.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + enclaveName.toLowerCase() + "%"));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<HostGroup> findByOptionalFilters(
                                                                          String enclaveName) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Optionally filter by enclave name if provided
            if (enclaveName != null && !enclaveName.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + enclaveName.toLowerCase() + "%"));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
