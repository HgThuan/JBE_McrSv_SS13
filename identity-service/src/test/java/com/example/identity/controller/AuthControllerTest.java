package com.example.identity.controller;

import com.example.identity.dto.RegisterRequest;
import com.example.identity.entity.User;
import com.example.identity.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/auth/register thành công, trả về thông tin user không kèm password, DB lưu hash")
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest("johndoe", "Secret@123", "ADMIN");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value("johndoe"))
                .andExpect(jsonPath("$.data.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.data.password").doesNotExist()); // Đảm bảo không lộ password

        // Kiểm tra trong database
        User savedUser = userRepository.findByUsername("johndoe").orElse(null);
        assertNotNull(savedUser, "User phải được lưu trong DB");
        assertNotEquals("Secret@123", savedUser.getPassword(), "Mật khẩu không được lưu dạng plain text");
        assertTrue(savedUser.getPassword().startsWith("$2a$") || savedUser.getPassword().startsWith("$2b$"),
                "Mật khẩu phải được băm theo chuẩn BCrypt");
        assertTrue(passwordEncoder.matches("Secret@123", savedUser.getPassword()),
                "BCryptPasswordEncoder phải verify được mật khẩu gốc");
    }

    @Test
    @DisplayName("POST /api/auth/register thất bại khi trùng username (HTTP 409 Conflict)")
    void testRegisterDuplicateUsername() throws Exception {
        // Đăng ký user đầu tiên
        RegisterRequest request1 = new RegisterRequest("alice", "Password@123", "USER");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Đăng ký lại cùng username
        RegisterRequest request2 = new RegisterRequest("alice", "AnotherPassword@456", "USER");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Username đã được đăng ký")));
    }

    @Test
    @DisplayName("POST /api/auth/register thất bại khi dữ liệu đầu vào không hợp lệ (HTTP 400 Bad Request)")
    void testRegisterValidationFailure() throws Exception {
        // Username rỗng, Password ngắn hơn 6 ký tự
        RegisterRequest invalidRequest = new RegisterRequest("", "123", "USER");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.username").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    @DisplayName("GET /api/auth/test-token thành công, trả về JWT Token dài gồm 3 phần với sub và role chính xác")
    void testGetTestTokenSuccess() throws Exception {
        // Đăng ký user trước
        RegisterRequest request = new RegisterRequest("jwt_user", "Password@123", "ADMIN");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Gọi API test-token
        String token = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/test-token")
                        .param("username", "jwt_user"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertNotNull(token);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT trả về phải có đúng 3 phần phân cách bởi dấu chấm");

        // Giải mã payload để kiểm tra claims sub và role
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        assertTrue(payloadJson.contains("\"sub\":\"jwt_user\""), "Claim sub phải chứa username");
        assertTrue(payloadJson.contains("\"role\":\"ROLE_ADMIN\""), "Custom claim role phải chứa ROLE_ADMIN");
    }

    @Test
    @DisplayName("GET /api/auth/test-token thất bại khi username không tồn tại trong DB (HTTP 404 Not Found)")
    void testGetTestTokenUserNotFound() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/test-token")
                        .param("username", "non_existing_user"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("Không tìm thấy người dùng")));
    }
}
