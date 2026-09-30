package com.example.identity.service.impl;

import com.example.identity.dto.RegisterRequest;
import com.example.identity.dto.UserResponse;
import com.example.identity.entity.User;
import com.example.identity.exception.UsernameAlreadyExistsException;
import com.example.identity.repository.UserRepository;
import com.example.identity.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.example.identity.util.JwtUtil jwtUtil;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, com.example.identity.util.JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional(readOnly = true)
    public com.example.identity.dto.LoginResponse login(com.example.identity.dto.LoginRequest request) {
        String trimmedUsername = request.getUsername().trim();

        // 1. Tìm User trong Database thông qua UserRepository
        User user = userRepository.findByUsername(trimmedUsername)
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        // 2. Sử dụng BCryptPasswordEncoder.matches để so khớp mật khẩu plain text với mật khẩu đã băm
        // Tuyệt đối không dùng == hoặc equals()
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Bad credentials");
        }

        // 3. Nếu thành công, dùng JwtUtil để sinh chuỗi JWT Token
        String token = jwtUtil.generateToken(user);

        // 4. Trả về LoginResponse chứa token, tokenType, thời gian hết hạn và thông tin người dùng
        return new com.example.identity.dto.LoginResponse(
                token,
                "Bearer",
                jwtUtil.getExpiration(),
                user.getUsername(),
                user.getRole()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public String generateTokenForUser(String username) {
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new com.example.identity.exception.UserNotFoundException(
                        "Không tìm thấy người dùng với username: " + username));
        return jwtUtil.generateToken(user);
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String trimmedUsername = request.getUsername().trim();

        // 1. Kiểm tra tài khoản đã tồn tại chưa
        if (userRepository.existsByUsername(trimmedUsername)) {
            throw new UsernameAlreadyExistsException("Username đã được đăng ký: " + trimmedUsername);
        }

        // 2. Dùng BCryptPasswordEncoder để băm mật khẩu
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 3. Xử lý Role (mặc định là ROLE_USER nếu không truyền)
        String role = (request.getRole() == null || request.getRole().trim().isEmpty())
                ? "ROLE_USER"
                : request.getRole().trim().toUpperCase();
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        // 4. Lưu User vào cơ sở dữ liệu
        User user = new User(trimmedUsername, encodedPassword, role);
        User savedUser = userRepository.save(user);

        // 5. Trả về UserResponse không chứa password
        return UserResponse.fromEntity(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với username: " + username));
        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
