# SS13 - IDENTITY SERVICE, BCRYPT PASSWORD HASHING & STATELESS JWT TOKEN

Dự án triển khai **Identity Service** trong hệ thống kiến trúc **Microservices** sử dụng **Spring Boot 3 (Java 21, Gradle)**, **Spring Security 6**, **Spring Data JPA**, **PostgreSQL**, và thư viện **JJWT (Java JSON Web Token)**.

- **GitHub Repository**: [https://github.com/HgThuan/JBE_McrSv_SS13](https://github.com/HgThuan/JBE_McrSv_SS13)

---

## 1. Mục Tiêu & Kiến Thức Cốt Lõi

### 1.1. Vai trò của Identity Service trong Microservices
Trong kiến trúc Microservices, **Identity Service** đóng vai trò là *"Phòng quản lý nhân khẩu và căn cước công dân"*:
- Là nơi duy nhất trực tiếp quản lý bảng thông tin tài khoản, thông tin đăng nhập và danh tính (credentials).
- Các dịch vụ nghiệp vụ khác (Order Service, Product Service, Payment Service...) không cần và không được phép lưu trữ mật khẩu của người dùng, mà sẽ xác thực thông qua Identity Service bằng Token định danh Stateless (JWT).

### 1.2. Tầm quan trọng của việc băm mật khẩu (Hashing) và BCrypt (Bài 1)
- **Tuyệt đối không lưu mật khẩu dạng Plain Text**: Nếu cơ sở dữ liệu bị lộ (SQL Injection, rò rỉ dữ liệu, backup leak), hacker sẽ chiếm toàn bộ tài khoản người dùng ngay lập tức.
- **Tại sao không dùng MD5 hay SHA-256 đơn thuần?**
  - Các hàm băm như MD5, SHA-1, SHA-256 được thiết kế để tính toán cực nhanh cho việc kiểm tra toàn vẹn file. Do đó, hacker có thể dùng GPU/ASIC để bẻ khóa hàng tỷ mật khẩu mỗi giây bằng phương pháp Brute Force hoặc tra cứu từ điển (**Rainbow Table**).
- **Cơ chế vượt trội của BCrypt (`BCryptPasswordEncoder`):**
  - **Salt tự động và ngẫu nhiên (128-bit)**: Mỗi khi mã hóa, BCrypt tự động sinh một chuỗi muối (Salt) ngẫu nhiên ghép vào mật khẩu trước khi băm. Vì vậy, **hai người dùng có cùng mật khẩu sẽ có hai chuỗi hash hoàn toàn khác nhau trong DB**, vô hiệu hóa hoàn toàn kỹ thuật tấn công bằng Rainbow Table.
  - **Work Factor (Cost Factor)**: Mặc định là `10` ($2^{10} = 1024$ vòng lặp băm liên tục). Thuật toán này cố tình làm chậm quá trình băm (vài chục milliseconds), đủ nhanh cho người dùng đăng nhập bình thường nhưng ngăn chặn kẻ tấn công brute-force hàng triệu mật khẩu.
  - **Cấu trúc chuỗi băm BCrypt**:
    ```text
    $2a$10$iBwQmp9Z9qJKoZTuPsAeW.JX39MqvjwoJRghlT3LQLWDBM7R0BFHi
    \__/ \_/ \____________________/\____________________________/
      |    |            |                         |
    Chuẩn Chi phí   128-bit Salt               184-bit Hash
    BCrypt ($2a)    (22 ký tự)                 (31 ký tự)
    ```

### 1.3. Cấu trúc 3 phần của JWT Token định danh Stateless (Bài 2)
Sau khi người dùng đăng ký/đăng nhập, Identity Service cấp một "tấm thẻ căn cước" **JSON Web Token (JWT)** gồm đúng 3 phần phân cách bởi dấu chấm (`.`):

```text
Header.Payload.Signature
```

1. **Header (Đầu thẻ)**:
   - Chứa loại token (`JWT`) và thuật toán băm chữ ký (ví dụ: `HS256` - HMAC SHA-256).
   ```json
   {
     "alg": "HS256"
   }
   ```
2. **Payload (Thân thẻ - Claims)**:
   - Chứa thông tin định danh người dùng:
     - `sub` (Subject): `username` của người dùng (từ dữ liệu Bài 1).
     - `role` (Custom claim): Vai trò của người dùng (ví dụ: `ROLE_USER`, `ROLE_ADMIN`).
     - `iat` (Issued At): Thời điểm cấp token (timestamp).
     - `exp` (Expiration): Thời điểm hết hạn token (`iat + 3600000ms = 1 giờ`).
   ```json
   {
     "role": "ROLE_USER",
     "sub": "john_doe",
     "iat": 1790740578,
     "exp": 1790744178
   }
   ```
3. **Signature (Chữ ký chống giả mạo)**:
   - Được tạo ra bằng cách lấy:
     $$\text{HMACSHA256}(\text{base64UrlEncode}(\text{Header}) + "." + \text{base64UrlEncode}(\text{Payload}), \text{secret})$$
   - Nếu bất kỳ ai cố tình chỉnh sửa username hay role trong Payload, chữ ký sẽ không còn khớp với secret 256-bit của server, request sẽ lập tức bị từ chối.
   - Nhờ đó, các microservice khác chỉ cần giải mã và xác thực chữ ký của Token bằng secret chung mà không cần truy vấn lại cơ sở dữ liệu (Stateless).

---

## 2. Cấu Trúc Dự Án

```text
SS13/
├── .gitignore
├── README.md
├── jwt_io_result.png                       # Ảnh chụp màn hình kết quả tại jwt.io
├── test-identity-service.sh                # Script kiểm thử tự động cURL + psql + JWT decode
└── identity-service/                       # Dự án Spring Boot (Java 21, Gradle)
    ├── build.gradle                        # Dependencies JJWT, Spring Security, JPA, PostgreSQL
    ├── settings.gradle
    ├── gradlew
    ├── gradlew.bat
    ├── gradle/wrapper/
    │   ├── gradle-wrapper.jar
    │   └── gradle-wrapper.properties
    └── src/
        ├── main/
        │   ├── java/com/example/identity/
        │   │   ├── IdentityServiceApplication.java
        │   │   ├── config/
        │   │   │   └── SecurityConfig.java           # SecurityFilterChain & BCrypt bean
        │   │   ├── controller/
        │   │   │   └── AuthController.java           # POST /register & GET /test-token
        │   │   ├── dto/
        │   │   │   ├── ApiResponse.java              # Chuẩn hóa format phản hồi JSON
        │   │   │   ├── RegisterRequest.java          # Input DTO (@NotBlank, @Size)
        │   │   │   └── UserResponse.java             # Output DTO (loại bỏ password)
        │   │   ├── entity/
        │   │   │   └── User.java                     # Entity User (id: UUID String, username, password, role)
        │   │   ├── exception/
        │   │   │   ├── GlobalExceptionHandler.java   # Xử lý 400, 404, 409
        │   │   │   ├── UserNotFoundException.java
        │   │   │   └── UsernameAlreadyExistsException.java
        │   │   ├── repository/
        │   │   │   └── UserRepository.java           # Spring Data JPA Repository
        │   │   ├── service/
        │   │   │   ├── UserService.java
        │   │   │   └── impl/UserServiceImpl.java    # Logic băm BCrypt & sinh JWT token
        │   │   └── util/
        │   │       └── JwtUtil.java                  # Tạo Token, ký HS256, trích xuất claims
        │   └── resources/
        │       └── application.yml                   # Cấu hình PostgreSQL identity_db & JWT secret
        └── test/
            └── java/com/example/identity/
                ├── controller/
                │   └── AuthControllerTest.java       # MockMvc integration tests
                ├── service/
                │   └── BCryptTest.java               # Unit test kiểm tra Salt BCrypt
                └── util/
                    └── JwtUtilTest.java              # Unit test cấu trúc 3 phần JWT & claims
```

---

## 3. Cài Đặt & Thư Viện

### 3.1. Thư viện phụ thuộc (`build.gradle`)
```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.3'
    id 'io.spring.dependency-management' version '1.1.7'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    runtimeOnly 'org.postgresql:postgresql'

    // JJWT (Java JWT) - Bài 2
    implementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

### 3.2. Cấu hình Cơ sở dữ liệu & JWT (`application.yml`)
```yaml
server:
  port: 8080

spring:
  application:
    name: identity-service
  datasource:
    url: jdbc:postgresql://localhost:5432/identity_db
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true

# Cấu hình JWT cho Identity Service
jwt:
  # Chuỗi bí mật 256-bit (HS256 yêu cầu khóa tối thiểu 256 bits = 32 bytes)
  secret: "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
  jwt-secret: "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
  # Thời gian hết hạn của Token: 3600000 ms = 60 phút = 1 giờ
  expiration: 3600000
  jwt-expiration: 3600000

logging:
  level:
    root: INFO
    com.example.identity: DEBUG
    org.springframework.security: DEBUG
```

---

## 4. Chi Tiết Thực Thi Lớp `JwtUtil`

```java
@Component
public class JwtUtil {

    private final String secret;
    private final long expiration;

    public JwtUtil(
            @Value("${jwt.secret:${jwt.jwt-secret}}") String secret,
            @Value("${jwt.expiration:${jwt.jwt-expiration:3600000}}") long expiration) {
        this.secret = secret;
        this.expiration = expiration;
    }

    private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole()); // Custom claim role

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername()) // Claim sub
                .setIssuedAt(now)              // Claim iat
                .setExpiration(expiryDate)     // Claim exp
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }
}
```

---

## 5. Kết Quả Thực Nghiệm & Ảnh Chụp jwt.io

### 5.1. Bài 1: Đăng ký tài khoản (`POST /api/auth/register`)
**Request:**
```http
POST /api/auth/register HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "username": "john_doe",
  "password": "Password123!",
  "role": "USER"
}
```

**Response (HTTP 201 Created):**
```json
{
  "success": true,
  "message": "Đăng ký tài khoản thành công!",
  "data": {
    "id": "c27f6985-1e0e-44ac-be3b-508a2463274f",
    "username": "john_doe",
    "role": "ROLE_USER"
  },
  "timestamp": "2026-09-30T10:56:15.083648"
}
```

**Truy vấn Database PostgreSQL (`identity_db`):**
```text
                  id                  |     username     |                           password                           |    role    
--------------------------------------+------------------+--------------------------------------------------------------+------------
 c27f6985-1e0e-44ac-be3b-508a2463274f | john_doe         | $2a$10$4spB6lJtKc57XFiDWplmmedLPjNG9pzddlHU3.lXsa7pI1TK/8pxS | ROLE_USER
```

### 5.2. Bài 2: Tạo Token định danh (`GET /api/auth/test-token?username=john_doe`)
**Request:**
```http
GET /api/auth/test-token?username=john_doe HTTP/1.1
Host: localhost:8080
```

**Response (HTTP 200 OK):**
```text
eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9VU0VSIiwic3ViIjoiam9obl9kb2UiLCJpYXQiOjE3OTA3NDA1NzgsImV4cCI6MTc5MDc0NDE3OH0.I6pSaUl16BDIRFPUG43rD2oGuR3RhpNhClJ6AYtUrXg
```

### 5.3. Kết quả xác thực tại trang web jwt.io

Ảnh chụp màn hình thực tế kết quả kiểm thử trên **[jwt.io](https://jwt.io)**:

![Kết quả kiểm tra JWT Token trên jwt.io](jwt_io_result.png)

- **Header**:
  ```json
  {
    "alg": "HS256"
  }
  ```
- **Payload**:
  ```json
  {
    "role": "ROLE_USER",
    "sub": "john_doe",
    "iat": 1790740578,
    "exp": 1790744178
  }
  ```
- **Thời gian sống của Token**: $1790744178 - 1790740578 = 3600 \text{ giây} = 1 \text{ giờ}$ (chuẩn theo cấu hình `jwt.expiration = 3600000ms`).

---

## 6. Hướng Dẫn Chạy & Kiểm Thử

### 6.1. Khởi chạy Service
```bash
cd identity-service
./gradlew bootRun
```

### 6.2. Chạy toàn bộ Unit & Integration Tests (Gradle)
```bash
cd identity-service
./gradlew test
```

### 6.3. Chạy Script kiểm thử tự động toàn diện (Bash)
Tại thư mục gốc `SS13`:
```bash
./test-identity-service.sh
```
Script sẽ tự động:
1. Kiểm tra trạng thái port `8080`.
2. Tạo User với role `ROLE_USER`.
3. Tạo Admin với role `ROLE_ADMIN` cùng mật khẩu để chứng minh cơ chế Salt ngẫu nhiên của BCrypt.
4. Truy vấn trực tiếp database `identity_db` để kiểm chứng cột `password` ở dạng `$2a$10$...`.
5. Kiểm thử trùng lặp username (`409 Conflict`).
6. Kiểm thử dữ liệu không hợp lệ (`400 Bad Request`).
7. Gọi `GET /api/auth/test-token?username={name}` để tạo JWT Token.
8. Tách và giải mã 3 phần Header, Payload (`sub`, `role`, `iat`, `exp`), và Signature.
9. Kiểm thử `404 Not Found` khi user không tồn tại trong database.
