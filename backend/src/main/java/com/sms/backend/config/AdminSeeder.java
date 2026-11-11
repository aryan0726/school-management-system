
package com.sms.backend.config;

import com.sms.backend.entity.Role;
import com.sms.backend.entity.User;
import com.sms.backend.repository.RoleRepository;
import com.sms.backend.repository.UserRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeeder {

    @Bean
    @ConditionalOnProperty(
            name = "app.bootstrap-admin.enabled",
            havingValue = "true"
    )
    CommandLineRunner createAdmin(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {
            String email = System.getenv("ADMIN_EMAIL");
            String password = System.getenv("ADMIN_PASSWORD");

            if (email == null || email.isBlank()
                    || password == null || password.isBlank()) {
                throw new IllegalStateException(
                        "ADMIN_EMAIL and ADMIN_PASSWORD are required"
                );
            }

            if (userRepository.existsByEmail(email)) {
                return;
            }

            Role role = roleRepository.findByName("ADMIN")
                    .orElseThrow(() ->
                            new IllegalStateException("ADMIN role not found"));

            User admin = new User();
            admin.setFullName("School Admin");
            admin.setEmail(email);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole(role);
            admin.setActive(true);

            userRepository.save(admin);
        };
    }
}
