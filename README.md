# SS13 - IDENTITY SERVICE: BCRYPT HASHING, JWT TOKEN & STATELESS AUTHENTICATION

Dự án triển khai toàn diện **Identity Service** trong hệ thống kiến trúc **Microservices** qua 3 bài học:
1. **Bài 1**: Tổ chức Identity Service, cấu hình Spring Security cơ bản, kết nối PostgreSQL và băm mật khẩu an toàn bằng **BCryptPasswordEncoder**.
2. **Bài 2**: Cấu trúc 3 phần của **JSON Web Token (JWT)**, sử dụng thư viện **JJWT** tạo Token định danh Stateless với claims `sub`, `role`.
3. **Bài 3**: Cơ chế xác thực không lưu trạng thái (**Stateless Authentication**), quy trình so khớp mật khẩu (**Password Matching** bằng `matches()`), và API đăng nhập `POST /api/auth/login`.

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

1. **Header (Đầu thẻ)**: Chứa loại token (`JWT`) và thuật toán băm chữ ký (`HS256` - HMAC SHA-256).
2. **Payload (Thân thẻ - Claims)**: Chứa thông tin định danh:
   - `sub` (Subject): `username` của người dùng.
   - `role` (Custom claim): Vai trò của người dùng (ví dụ: `ROLE_USER`, `ROLE_ADMIN`).
   - `iat` (Issued At) và `exp` (Expiration: `iat + 3600000ms = 1 giờ`).
3. **Signature (Chữ ký chống giả mạo)**: Ký bằng HMAC-SHA256 với secret 256-bit.

### 1.4. Cơ Chế Xác Thực Stateless & So Khớp Mật Khẩu (Bài 3)
- **Stateful (Truyền thống)**: Server tạo `HttpSession`, lưu thông tin đăng nhập trong RAM/DB Server, gửi cookie `JSESSIONID` về client. Trong hệ thống phân tán (Microservices với hàng chục replica), mô hình này đòi hỏi Session Replication phức tạp hoặc Sticky Sessions, làm nghẽn cổ chai và khó mở rộng theo chiều ngang (Horizontal Scaling).
- **Stateless (Hiện đại)**:
  - Cấu hình `SessionCreationPolicy.STATELESS`: Vô hiệu hóa hoàn toàn việc tạo `HttpSession` và `JSESSIONID`. Server không lưu bất kỳ trạng thái nào của phiên làm việc.
  - Mỗi request là độc lập, mang theo Token trong Header (`Authorization: Bearer <token>`).
- **Quy trình So Khớp Mật Khẩu (Password Matching)**:
  - **Tuyệt đối không dùng toán tử `==` hoặc `.equals()`**: Vì chuỗi mật khẩu trong DB đã được băm kèm Salt ngẫu nhiên, nên không thể băm lại mật khẩu người dùng nhập rồi so sánh bằng `.equals()`.
  - **Bắt buộc dùng `passwordEncoder.matches(rawPassword, encodedPassword)`**: BCrypt sẽ tự động trích xuất Salt từ chuỗi hash trong database, dùng Salt đó để băm `rawPassword` và đối soát an toàn.
- **Nguyên tắc bảo mật chống User Enumeration**:
  - Khi đăng nhập thất bại (kể cả không tìm thấy username hoặc sai mật khẩu), hệ thống **chỉ trả về một thông báo lỗi chung chung duy nhất: `Bad credentials` (HTTP 401)**.
  - Không bao giờ trả về "Username không tồn tại" hay "Sai mật khẩu", nhằm ngăn chặn tin tặc quét dò danh sách tài khoản hợp lệ trong hệ thống.

---

## 2. Cấu Trúc Dự Án

```text
SS13/
├── .gitignore
├── README.md
├── jwt_io_result.png                       # Ảnh chụp màn hình kết quả tại jwt.io
├── test-identity-service.sh                # Script kiểm thử tự động cURL + psql + JWT + Login
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
        │   │   │   └── SecurityConfig.java           # SecurityFilterChain Stateless & BCrypt bean
        │   │   ├── controller/
        │   │   │   └── AuthController.java           # /register, /test-token, /login
        │   │   ├── dto/
        │   │   │   ├── ApiResponse.java              # Chuẩn hóa format phản hồi JSON
        │   │   │   ├── LoginRequest.java             # DTO đăng nhập (username, password)
        │   │   │   ├── LoginResponse.java            # DTO trả về Token (accessToken, Bearer, expiresIn)
        │   │   │   ├── RegisterRequest.java          # DTO đăng ký (@NotBlank, @Size)
        │   │   │   └── UserResponse.java             # DTO user (loại bỏ password)
        │   │   ├── entity/
        │   │   │   └── User.java                     # Entity User (id: UUID String, username, password, role)
        │   │   ├── exception/
        │   │   │   ├── GlobalExceptionHandler.java   # Xử lý BadCredentialsException (401), 400, 404, 409
        │   │   │   ├── UserNotFoundException.java
        │   │   │   └── UsernameAlreadyExistsException.java
        │   │   ├── repository/
        │   │   │   └── UserRepository.java           # Spring Data JPA Repository
        │   │   ├── service/
        │   │   │   ├── UserService.java
        │   │   │   └── impl/UserServiceImpl.java    # Logic BCrypt.matches() & sinh JWT
        │   │   └── util/
        │   │       └── JwtUtil.java                  # Tạo Token, ký HS256, trích xuất claims
        │   └── resources/
        │       └── application.yml                   # Cấu hình PostgreSQL identity_db & JWT secret
        └── test/
            └── java/com/example/identity/
                ├── controller/
                │   └── AuthControllerTest.java       # MockMvc integration tests (Register, Token, Login)
                ├── service/
                │   └── BCryptTest.java               # Unit test kiểm tra Salt BCrypt
                └── util/
                    └── JwtUtilTest.java              # Unit test cấu trúc 3 phần JWT & claims
```

---

## 3. Cấu Hình SecurityFilterChain & Stateless (`SecurityConfig.java`)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Tắt CSRF vì đây là REST API Stateless
                .csrf(AbstractHttpConfigurer::disable)
                // 2. Cho phép permitAll cho các endpoint auth (/register, /login, /test-token)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                // 3. Vô hiệu hóa tạo Session và JSESSIONID (Stateless)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }
}
```

---

## 4. Logic Xác Thực Tại Tầng Service (`UserServiceImpl.java`)

```java
@Override
@Transactional(readOnly = true)
public LoginResponse login(LoginRequest request) {
    String trimmedUsername = request.getUsername().trim();

    // 1. Tìm User trong Database thông qua UserRepository
    User user = userRepository.findByUsername(trimmedUsername)
            .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

    // 2. Sử dụng BCryptPasswordEncoder.matches để so khớp mật khẩu plain text với mật khẩu đã băm
    // Tuyệt đối không dùng == hoặc equals()
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        throw new BadCredentialsException("Bad credentials");
    }

    // 3. Nếu thành công, dùng JwtUtil để sinh chuỗi JWT Token
    String token = jwtUtil.generateToken(user);

    // 4. Trả về LoginResponse chứa token, tokenType, thời gian hết hạn và thông tin người dùng
    return new LoginResponse(
            token,
            "Bearer",
            jwtUtil.getExpiration(),
            user.getUsername(),
            user.getRole()
    );
}
```

---

## 5. Kết Quả Kiểm Thử Thực Tế

### 5.1. Bài 1: Đăng ký người dùng (`POST /api/auth/register`)
- **Mật khẩu gốc**: `Password123!`
- **Database lưu trữ**: Chuỗi hash `$2a$10$dVG6Ta/emYA/mYwmSuyR/OFx7M3rd.yNhoq8J4UdhS1dN05WVmnda` (không lưu plain text).
- **Phản hồi**: Trả về `UserResponse` chỉ có `id`, `username`, `role` (loại bỏ hoàn toàn trường password).

### 5.2. Bài 2: Tạo Token kiểm thử (`GET /api/auth/test-token?username=john_doe`)
- **Phản hồi**: Chuỗi JWT gồm đúng 3 phần:
  ```text
  eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9VU0VSIiwic3ViIjoiam9obl9kb2UiLCJpYXQiOjE3OTA3NDA1NzgsImV4cCI6MTc5MDc0NDE3OH0.I6pSaUl16BDIRFPUG43rD2oGuR3RhpNhClJ6AYtUrXg
  ```
- **Xác thực tại [jwt.io](https://jwt.io)**:

![Kết quả kiểm tra JWT Token trên jwt.io](jwt_io_result.png)

### 5.3. Bài 3: Đăng nhập hệ thống (`POST /api/auth/login`)

#### Trường hợp 1: Thông tin đăng nhập chính xác (HTTP 200 OK)
**Request:**
```http
POST /api/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "username": "demo_user",
  "password": "Password123!"
}
```
**Response (HTTP 200 OK):**
```json
{
  "success": true,
  "message": "Đăng nhập thành công!",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiUk9MRV9VU0VSIiwic3ViIjoiZGVtb191c2VyIiwiaWF0IjoxNzkwNzQxMTE3LCJleHAiOjE3OTA3NDQ3MTd9.zNGyDDh5ru4IuGaCDFTOZCyvNcoId4fci9D79OsbJQM",
    "tokenType": "Bearer",
    "expiresIn": 3600000,
    "username": "demo_user",
    "role": "ROLE_USER"
  },
  "timestamp": "2026-09-30T11:05:17.975447"
}
```
> **Kiểm chứng Stateless**: Headers trả về **hoàn toàn không có header `Set-Cookie`** (không tạo `JSESSIONID`), server không lưu phiên làm việc.

#### Trường hợp 2: Sai mật khẩu (HTTP 401 Unauthorized)
**Request:**
```http
POST /api/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "username": "demo_user",
  "password": "WrongPassword999"
}
```
**Response (HTTP 401 Unauthorized):**
```json
{
  "success": false,
  "message": "Bad credentials",
  "data": null,
  "timestamp": "2026-09-30T11:05:23.260025"
}
```

#### Trường hợp 3: Tên đăng nhập không tồn tại (HTTP 401 Unauthorized)
**Request:**
```http
POST /api/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "username": "non_existent_ghost",
  "password": "Password123!"
}
```
**Response (HTTP 401 Unauthorized):**
```json
{
  "success": false,
  "message": "Bad credentials",
  "data": null,
  "timestamp": "2026-09-30T11:05:27.814475"
}
```
> **Nhận xét**: Cả hai trường hợp lỗi đều trả về cùng mã `401 Unauthorized` và thông báo `"Bad credentials"`, đảm bảo an toàn tuyệt đối trước các kỹ thuật tấn công User Enumeration.

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

### 6.3. Chạy Script kiểm thử tự động toàn diện 12 kịch bản (Bash)
Tại thư mục gốc `SS13`:
```bash
./test-identity-service.sh
```
Script sẽ tự động:
1. Kiểm tra trạng thái cổng `8080`.
2. Tạo User với role `ROLE_USER`.
3. Tạo Admin với role `ROLE_ADMIN` cùng mật khẩu để chứng minh cơ chế Salt ngẫu nhiên của BCrypt.
4. Truy vấn trực tiếp database `identity_db` để kiểm chứng cột `password` ở dạng `$2a$10$...`.
5. Kiểm thử trùng lặp username (`409 Conflict`).
6. Kiểm thử dữ liệu không hợp lệ (`400 Bad Request`).
7. Gọi `GET /api/auth/test-token?username={name}` để tạo JWT Token.
8. Tách và giải mã 3 phần Header, Payload (`sub`, `role`, `iat`, `exp`), và Signature.
9. Kiểm thử `404 Not Found` khi user không tồn tại trong database.
10. Đăng nhập thành công (`200 OK`), kiểm chứng Access Token và tính Stateless (không `Set-Cookie`).
11. Đăng nhập thất bại do sai mật khẩu (`401 Unauthorized` + `"Bad credentials"`).
12. Đăng nhập thất bại do username không tồn tại (`401 Unauthorized` + `"Bad credentials"`).
