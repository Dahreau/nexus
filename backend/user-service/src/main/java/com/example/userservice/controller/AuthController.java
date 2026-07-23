package com.example.userservice.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.userservice.model.Role;
import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.security.JwtUtil;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        // int error = "error"; // Error code for testing purposes
        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");
        String roleStr = body.get("role");
        Role role = "SELLER".equalsIgnoreCase(roleStr) ? Role.SELLER : Role.CLIENT;

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already in use"));
        }
        String hashed = passwordEncoder.encode(password);
        User u = new User(name, email, hashed, role);
        userRepository.save(u);
        String token = JwtUtil.generateToken(u.getId(), u.getRole().name(), u.getName());
        return ResponseEntity.ok(Map.of("token", token, "userId", u.getId()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        var opt = userRepository.findByEmail(email);
        if (opt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        User u = opt.get();
        if (!passwordEncoder.matches(password, u.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        String token = JwtUtil.generateToken(u.getId(), u.getRole().name(), u.getName());
        return ResponseEntity.ok(Map.of("token", token, "userId", u.getId()));
    }
}
