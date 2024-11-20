package io.sentrius.sso.core.services;


import io.sentrius.sso.core.repository.UserRepository;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.security.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository; // Assuming you have a UserRepository to retrieve user data

    private final UserService userService;

    @Autowired
    public CustomUserDetailsService(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.getUserWithDetails(username); // Replace with your actual query
        if (user == null) {
            log.info("User {} not found", username);
            throw new UsernameNotFoundException("User not found");
        }
        log.info("User {} found!!!", username);
        return new CustomUserDetails(user); // CustomUserDetails should implement UserDetails
    }
}
