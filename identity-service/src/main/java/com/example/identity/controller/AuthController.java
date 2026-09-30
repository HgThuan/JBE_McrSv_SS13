package com.example.identity.controller;

import com.example.identity.dto.ApiResponse;
import com.example.identity.dto.LoginRequest;
import com.example.identity.dto.LoginResponse;
import com.example.identity.dto.RegisterRequest;
import com.example.identity.dto.UserResponse;
import com.example.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * API Đăng nhập người dùng (Bài 3)
     * POST /api/auth/login
     *
     * @param request Thông tin đăng nhập (username, password)
     * @return LoginResponse chứa Access Token hợp lệ (JWT)
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công!", response));
    }

    /**
     * API Đăng ký tài khoản người dùng
     * POST /api/auth/register
     *
     * @param request Thông tin đăng ký (username, password, role)
     * @return UserResponse chứa thông tin user (loại bỏ password)
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công!", response));
    }

    /**
     * API Tạo Token định danh cho User (Bài 2)
     * GET /api/auth/test-token?username={name}
     *
     * @param username Tên đăng nhập của người dùng cần tạo Token
     * @return Chuỗi JWT Token
     */
    @GetMapping(value = "/test-token", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> testToken(@RequestParam("username") String username) {
        String token = userService.generateTokenForUser(username);
        return ResponseEntity.ok(token);
    }
}
