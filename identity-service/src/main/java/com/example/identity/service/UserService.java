package com.example.identity.service;

import com.example.identity.dto.RegisterRequest;
import com.example.identity.dto.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse register(RegisterRequest request);

    com.example.identity.dto.LoginResponse login(com.example.identity.dto.LoginRequest request);

    String generateTokenForUser(String username);

    UserResponse getUserByUsername(String username);

    List<UserResponse> getAllUsers();
}
