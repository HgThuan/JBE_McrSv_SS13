# SS13 - MICROSERVICES SECURITY & SPRING CLOUD API GATEWAY

Dự án triển khai toàn diện hệ thống **Microservices** qua 4 bài học:
1. **Bài 1**: Tổ chức **Identity Service**, cấu hình Spring Security cơ bản, kết nối PostgreSQL và băm mật khẩu an toàn bằng **BCryptPasswordEncoder**.
2. **Bài 2**: Cấu trúc 3 phần của **JSON Web Token (JWT)**, sử dụng thư viện **JJWT** tạo Token định danh Stateless với claims `sub`, `role`.
3. **Bài 3**: Cơ chế xác thực không lưu trạng thái (**Stateless Authentication**), quy trình so khớp mật khẩu (**Password Matching** bằng `matches()`), và API đăng nhập `POST /api/auth/login`.
4. **Bài 4**: Xây dựng **Spring Cloud API Gateway** (Port 8888) làm điểm tiếp nhận duy nhất (**Single Entry Point**), cấu hình **Route Predicates** và Filter **StripPrefix=1** để điều hướng linh hoạt giữa **Identity Service** và **Product Service** (mô phỏng).

- **GitHub Repository**: [https://github.com/HgThuan/JBE_McrSv_SS13](https://github.com/HgThuan/JBE_McrSv_SS13)

---

## 1. Mục Tiêu & Kiến Thức Cốt Lõi

### 1.1. Vai trò của Identity Service trong Microservices (Bài 1)
Trong kiến trúc Microservices, **Identity Service** đóng vai trò là *"Phòng quản lý nhân khẩu và căn cước công dân"*:
- Là nơi duy nhất trực tiếp quản lý bảng thông tin tài khoản, thông tin đăng nhập và danh tính (credentials).
- Các dịch vụ nghiệp vụ khác (Order Service, Product Service, Payment Service...) không cần và không được phép lưu trữ mật khẩu của người dùng, mà sẽ xác thực thông qua Identity Service bằng Token định danh Stateless (JWT).

### 1.2. Tầm quan trọng của việc băm mật khẩu (Hashing) và BCrypt (Bài 1)
- **Tuyệt đối không lưu mật khẩu dạng Plain Text**: Nếu cơ sở dữ liệu bị lộ, hacker sẽ chiếm toàn bộ tài khoản người dùng ngay lập tức.
- **Tại sao không dùng MD5 hay SHA-256 đơn thuần?**
  - Các hàm băm như MD5, SHA-1, SHA-256 được thiết kế để tính toán cực nhanh cho việc kiểm tra toàn vẹn file. Do đó, hacker có thể dùng GPU/ASIC để bẻ khóa hàng tỷ mật khẩu mỗi giây bằng phương pháp Brute Force hoặc tra cứu từ điển (**Rainbow Table**).
- **Cơ chế vượt trội của BCrypt (`BCryptPasswordEncoder`):**
  - **Salt tự động và ngẫu nhiên (128-bit)**: Mỗi khi mã hóa, BCrypt tự động sinh một chuỗi muối (Salt) ngẫu nhiên ghép vào mật khẩu trước khi băm. Vì vậy, **hai người dùng có cùng mật khẩu sẽ có hai chuỗi hash hoàn toàn khác nhau trong DB**, vô hiệu hóa hoàn toàn kỹ thuật tấn công bằng Rainbow Table.
  - **Work Factor (Cost Factor)**: Mặc định là `10` ($2^{10} = 1024$ vòng lặp băm liên tục). Thuật toán này cố tình làm chậm quá trình băm, ngăn chặn brute-force hàng triệu mật khẩu.

### 1.3. Cấu trúc 3 phần của JWT Token định danh Stateless (Bài 2)
Sau khi người dùng đăng ký/đăng nhập, Identity Service cấp một "tấm thẻ căn cước" **JSON Web Token (JWT)** gồm đúng 3 phần phân cách bởi dấu chấm (`.`):

```text
Header.Payload.Signature
```

1. **Header (Đầu thẻ)**: Chứa loại token (`JWT`) và thuật toán băm chữ ký (`HS256` - HMAC SHA-256).
2. **Payload (Thân thẻ - Claims)**: Chứa thông tin định danh:
   - `sub` (Subject): `username` của người dùng.
   - `role` (Custom claim): Vai trò của người dùng (ví dụ: `ROLE_USER`, `ROLE_ADMIN`).
   - `iat` (Issued At) và `exp` (Expiration: `iat + 3600000ms = 1 giờ`).
3. **Signature (Chữ ký chống giả mạo)**: Ký bằng HMAC-SHA256 với secret 256-bit.

### 1.4. Cơ Chế Xác Thực Stateless & So Khớp Mật Khẩu (Bài 3)
- **Stateless Authentication**:
  - Cấu hình `SessionCreationPolicy.STATELESS`: Vô hiệu hóa hoàn toàn việc tạo `HttpSession` và `JSESSIONID`. Server không lưu bất kỳ trạng thái nào của phiên làm việc trong RAM/Database.
  - Mỗi request là độc lập, mang theo Token trong Header (`Authorization: Bearer <token>`).
- **Quy trình So Khớp Mật Khẩu (Password Matching)**:
  - **Tuyệt đối không dùng toán tử `==` hoặc `.equals()`**.
  - **Bắt buộc dùng `passwordEncoder.matches(rawPassword, encodedPassword)`**: BCrypt tự động trích xuất Salt từ chuỗi hash trong database, dùng Salt đó để băm `rawPassword` và đối soát an toàn.
- **Nguyên tắc bảo mật chống User Enumeration**:
  - Khi đăng nhập thất bại (kể cả không tìm thấy username hoặc sai mật khẩu), hệ thống **chỉ trả về một thông báo lỗi chung duy nhất: `Bad credentials` (HTTP 401)** nhằm ngăn chặn tin tặc quét dò danh sách tài khoản hợp lệ.

### 1.5. Vai trò của Spring Cloud API Gateway (Bài 4)
Trong hệ thống Microservices, client không nên kết nối trực tiếp đến từng dịch vụ con (IP/Port riêng lẻ) vì:
1. **Bảo mật (Security)**: Giấu các cổng nội bộ và địa chỉ IP thực của các microservices phía sau tường lửa.
2. **Điểm tiếp nhận duy nhất (Single Entry Point)**: Mọi yêu cầu từ Web/Mobile Client chỉ gửi đến một cổng duy nhất (`8888`). Gateway chịu trách nhiệm định tuyến (**Routing**) đến các dịch vụ tương ứng.
3. **Bộ lọc (Filters) & Tiền xử lý**:
   - `Path Predicate`: Bắt request theo mẫu URL (ví dụ: `/identity/**`, `/product/**`).
   - `Filter StripPrefix=1`: Cắt bỏ phần tiền tố định tuyến (`/identity` hoặc `/product`) trước khi chuyển tiếp yêu cầu đến service đích.
4. **Lưu ý kỹ thuật quan trọng**:
   - Spring Cloud Gateway được xây dựng trên nền tảng **Reactive WebFlux và Netty** (Non-blocking I/O).
   - **Tuyệt đối không bao gồm `spring-boot-starter-web`** trong `gateway-service` vì Spring MVC (Blocking Servlet/Tomcat) sẽ gây xung đột làm Gateway không thể khởi động.

---

## 2. Cấu Trúc Toàn Bộ Dự Án

```text
SS13/
├── .gitignore
├── README.md
├── jwt_io_result.png                       # Ảnh chụp màn hình kết quả tại jwt.io
├── test-identity-service.sh                # Script test tự động Bài 1, 2, 3 (cổng 8080)
├── test-gateway-service.sh                 # Script test tự động Gateway Bài 4 (cổng 8888)
│
├── identity-service/                       # DỊCH VỤ IDENTITY (Spring Boot 3, Port 8080)
│   ├── build.gradle                        # Dependencies JJWT, Security, JPA, PostgreSQL
│   ├── settings.gradle
│   ├── gradlew & gradlew.bat
│   └── src/
│       ├── main/
│       │   ├── java/com/example/identity/
│       │   │   ├── IdentityServiceApplication.java
│       │   │   ├── config/SecurityConfig.java        # SecurityFilterChain Stateless & BCrypt bean
│       │   │   ├── controller/
│       │   │   │   ├── AuthController.java        # /register, /login, /test-token
│       │   │   │   └── ProductController.java     # /api/products (mô phỏng Product Service)
│       │   │   ├── dto/                           # RegisterRequest, LoginRequest, UserResponse, LoginResponse
│       │   │   ├── entity/User.java               # Entity User (id: UUID String, username, password, role)
│       │   │   ├── exception/GlobalExceptionHandler.java # Bắt 401 Bad credentials, 400, 404, 409
│       │   │   ├── repository/UserRepository.java
│       │   │   ├── service/UserService.java & impl/UserServiceImpl.java
│       │   │   └── util/JwtUtil.java              # Tạo & giải mã JWT với HS256
│       │   └── resources/application.yml          # Cấu hình PostgreSQL identity_db & JWT secret
│       └── test/
│           └── java/com/example/identity/
│               ├── controller/AuthControllerTest.java
│               ├── service/BCryptTest.java
│               └── util/JwtUtilTest.java
│
└── gateway-service/                        # DỊCH VỤ API GATEWAY (Spring Cloud Gateway, Port 8888)
    ├── build.gradle                        # Spring Cloud Gateway, WebFlux (KHÔNG có starter-web)
    ├── settings.gradle
    ├── gradlew & gradlew.bat
    └── src/
        ├── main/
        │   ├── java/com/example/gateway/GatewayServiceApplication.java
        │   └── resources/application.yml          # Routes: /identity/** -> 8080, /product/** -> 8080
        └── test/
            └── java/com/example/gateway/GatewayRoutingTest.java # Kiểm thử route & 404
```

---

## 3. Cấu Hình Spring Cloud Gateway (`gateway-service/src/main/resources/application.yml`)

```yaml
server:
  port: 8888

spring:
  application:
    name: gateway-service
  cloud:
    gateway:
      routes:
        # Route 1: Điều hướng các yêu cầu /identity/** về Identity Service (port 8080)
        - id: identity-service
          uri: http://localhost:8080
          predicates:
            - Path=/identity/**
          filters:
            # Loại bỏ tiền tố /identity trước khi forward đến Identity Service
            # Ví dụ: /identity/api/auth/login -> /api/auth/login
            - StripPrefix=1

        # Route 2: Điều hướng các yêu cầu /product/** về Product Service (port 8080)
        - id: product-service
          uri: http://localhost:8080
          predicates:
            - Path=/product/**
          filters:
            # Loại bỏ tiền tố /product trước khi forward
            # Ví dụ: /product/api/products -> /api/products
            - StripPrefix=1

logging:
  level:
    root: INFO
    org.springframework.cloud.gateway: DEBUG
    reactor.netty: DEBUG
```

---

## 4. Kết Quả Kiểm Thử Thực Tế Qua Gateway (Cổng 8888)

### 4.1. Đăng ký tài khoản qua Gateway (`POST http://localhost:8888/identity/api/auth/register`)
- **Request URL**: `http://localhost:8888/identity/api/auth/register`
- **Cơ chế**: Gateway bắt tiền tố `/identity/**`, cắt bỏ `/identity`, chuyển tiếp yêu cầu đến `http://localhost:8080/api/auth/register`.
- **Response (HTTP 201 Created)**:
  ```json
  {
    "success": true,
    "message": "Đăng ký tài khoản thành công!",
    "data": {
      "id": "6c63fe07-c715-4039-b5b8-9bd54cdb9a46",
      "username": "gw_user",
      "role": "ROLE_USER"
    },
    "timestamp": "2026-09-30T11:10:31.867458"
  }
  ```

### 4.2. Đăng nhập qua Gateway (`POST http://localhost:8888/identity/api/auth/login`)
- **Request URL**: `http://localhost:8888/identity/api/auth/login`
- **Response (HTTP 200 OK)**:
  ```json
  {
    "success": true,
    "message": "Đăng nhập thành công!",
    "data": {
      "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9VU0VSIiwic3ViIjoiZ3dfdXNlciIsImlhdCI6MTc5MDc0MTQzMiwiZXhwIjoxNzkwNzQ1MDMyfQ.It6G0uSZd5ZgSb-uaF35kHA4mEYVJLbP83nvGeCF6Zo",
      "tokenType": "Bearer",
      "expiresIn": 3600000,
      "username": "gw_user",
      "role": "ROLE_USER"
    },
    "timestamp": "2026-09-30T11:10:32.020612"
  }
  ```
- **Xác thực tại [jwt.io](https://jwt.io)**:

![Kết quả kiểm tra JWT Token trên jwt.io](jwt_io_result.png)

### 4.3. Định tuyến đa dịch vụ: Gọi Product Service (`GET http://localhost:8888/product/api/products`)
- **Request URL**: `http://localhost:8888/product/api/products`
- **Cơ chế**: Gateway bắt tiền tố `/product/**`, cắt bỏ `/product`, chuyển tiếp đến `http://localhost:8080/api/products`.
- **Response (HTTP 200 OK)**:
  ```json
  {
    "success": true,
    "message": "Lấy danh sách sản phẩm mô phỏng thành công!",
    "data": [
      { "id": "PROD-001", "name": "MacBook Pro M3 Max", "price": 3499.0, "category": "Laptop" },
      { "id": "PROD-002", "name": "iPhone 16 Pro 256GB", "price": 1199.0, "category": "Smartphone" },
      { "id": "PROD-003", "name": "Sony WH-1000XM5", "price": 399.0, "category": "Audio" }
    ],
    "timestamp": "2026-09-30T11:10:32.049289"
  }
  ```

### 4.4. Kiểm thử gọi sai tiền tố (`GET http://localhost:8888/wrong-path/something`)
- **Request URL**: `http://localhost:8888/wrong-path/something`
- **Response (HTTP 404 Not Found)**:
  ```json
  {
    "timestamp": "2026-09-30T04:10:32.075+00:00",
    "path": "/wrong-path/something",
    "status": 404,
    "error": "Not Found",
    "requestId": "4bf58249-9"
  }
  ```
  *(Gateway tự động chặn và trả về 404 vì không có Route Predicate nào khớp)*.

---

## 5. Hướng Dẫn Khởi Chạy & Kiểm Thử Toàn Diện

### 5.1. Khởi chạy 2 Microservices
Mở 2 cửa sổ terminal:

1. **Terminal 1 - Khởi chạy Identity Service (cổng 8080):**
   ```bash
   cd identity-service
   ./gradlew bootRun
   ```
2. **Terminal 2 - Khởi chạy Gateway Service (cổng 8888):**
   ```bash
   cd gateway-service
   ./gradlew bootRun
   ```

### 5.2. Chạy toàn bộ Unit & Integration Tests (Gradle)
```bash
# Kiểm thử Identity Service
cd identity-service && ./gradlew test

# Kiểm thử Gateway Service
cd ../gateway-service && ./gradlew test
```

### 5.3. Chạy các Script kiểm thử tự động toàn diện (Bash)
Tại thư mục gốc `SS13`:

1. **Kiểm thử Identity Service trực tiếp (Bài 1, 2, 3 - cổng 8080):**
   ```bash
   ./test-identity-service.sh
   ```
2. **Kiểm thử API Gateway định tuyến đa dịch vụ (Bài 4 - cổng 8888):**
   ```bash
   ./test-gateway-service.sh
   ```
