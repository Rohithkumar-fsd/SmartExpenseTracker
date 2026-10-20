package com.expensetracker.controller;

import com.expensetracker.model.UserProfile;
import com.expensetracker.repository.UserProfileRepository;
import com.expensetracker.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final UserProfileRepository userProfileRepository;

    public AuthController(AuthService authService, UserProfileRepository userProfileRepository) {
        this.authService = authService;
        this.userProfileRepository = userProfileRepository;
    }

    public static class SignupRequest {
        public String username;
        public String email;
        public String password;
        public String name;
        public String currency;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest req, HttpSession session) {
        try {
            if (req.username == null || req.username.trim().isEmpty() ||
                req.email == null || req.email.trim().isEmpty() ||
                req.password == null || req.password.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username, email, and password are required."));
            }

            UserProfile user = authService.signup(
                    req.username.trim(),
                    req.email.trim(),
                    req.password.trim(),
                    req.name != null ? req.name.trim() : req.username.trim(),
                    req.currency != null ? req.currency.trim() : "₹"
            );

            session.setAttribute("userId", user.getId());
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public static class LoginRequest {
        public String usernameOrEmail;
        public String password;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpSession session) {
        if (req.usernameOrEmail == null || req.password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please provide username/email and password."));
        }

        Optional<UserProfile> userOpt = authService.login(req.usernameOrEmail.trim(), req.password.trim());
        if (userOpt.isPresent()) {
            UserProfile user = userOpt.get();
            session.setAttribute("userId", user.getId());
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username/email or password!"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            // Check if there is any user in the system to provide fallback or prompt login
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        return userProfileRepository.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
