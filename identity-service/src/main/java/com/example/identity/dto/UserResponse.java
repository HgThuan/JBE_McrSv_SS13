package com.example.identity.dto;

import com.example.identity.entity.User;

public class UserResponse {

    private String id;
    private String username;
    private String role;

    public UserResponse() {
    }

    public UserResponse(String id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public static UserResponse fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
