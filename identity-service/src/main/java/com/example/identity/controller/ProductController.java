package com.example.identity.controller;

import com.example.identity.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    /**
     * API Mô phỏng Product Service (Bài 4)
     * GET /api/products
     * Dùng để kiểm tra khả năng định tuyến đa dịch vụ của API Gateway
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMockProducts() {
        List<Map<String, Object>> products = List.of(
                Map.of("id", "PROD-001", "name", "MacBook Pro M3 Max", "price", 3499.0, "category", "Laptop"),
                Map.of("id", "PROD-002", "name", "iPhone 16 Pro 256GB", "price", 1199.0, "category", "Smartphone"),
                Map.of("id", "PROD-003", "name", "Sony WH-1000XM5", "price", 399.0, "category", "Audio")
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm mô phỏng thành công!", products));
    }
}
