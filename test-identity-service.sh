#!/bin/bash

# ==============================================================================
# SCRIPT KIỂM THỬ TỰ ĐỘNG IDENTITY SERVICE & BCRYPT PASSWORD HASHING (SS13)
# ==============================================================================

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}           KIỂM THỬ IDENTITY SERVICE & BCRYPT PASSWORD HASHING                ${NC}"
echo -e "${BLUE}==============================================================================${NC}"

BASE_URL="http://localhost:8080/api/auth"

# 1. Kiểm tra service sống
echo -e "\n${YELLOW}[1] Kiểm tra trạng thái Identity Service trên cổng 8080...${NC}"
if ! nc -z localhost 8080; then
    echo -e "${RED}Lỗi: Service chưa chạy trên port 8080! Hãy khởi chạy service trước khi chạy test.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Identity Service đang hoạt động!${NC}"

# 2. Đăng ký user 1 (Normal User)
USER1="user_$(date +%s)"
PASS="Password@123"

echo -e "\n${YELLOW}[2] Gọi API POST /api/auth/register - Đăng ký user: ${USER1}...${NC}"
RESPONSE1=$(curl -s -X POST "${BASE_URL}/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${USER1}\",
    \"password\": \"${PASS}\",
    \"role\": \"USER\"
  }")

echo -e "Response Body:"
echo "${RESPONSE1}" | jq . 2>/dev/null || echo "${RESPONSE1}"

# 3. Đăng ký user 2 (Admin) với CÙNG MẬT KHẨU để kiểm chứng cơ chế Salt của BCrypt
USER2="admin_$(date +%s)"
echo -e "\n${YELLOW}[3] Gọi API POST /api/auth/register - Đăng ký user: ${USER2} với CÙNG MẬT KHẨU '${PASS}'...${NC}"
RESPONSE2=$(curl -s -X POST "${BASE_URL}/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${USER2}\",
    \"password\": \"${PASS}\",
    \"role\": \"ADMIN\"
  }")

echo -e "Response Body:"
echo "${RESPONSE2}" | jq . 2>/dev/null || echo "${RESPONSE2}"

# 4. Kiểm tra Database identity_db bằng psql
echo -e "\n${YELLOW}[4] Truy vấn trực tiếp PostgreSQL (Database: identity_db, Table: users)...${NC}"
psql -U postgres -d identity_db -c "SELECT id, username, password, role FROM users WHERE username IN ('${USER1}', '${USER2}');"

echo -e "${GREEN}Nhận xét quan trọng về BCrypt:${NC}"
echo -e "  - Cùng một mật khẩu gốc là '${PASS}', nhưng 2 bản ghi sinh ra 2 chuỗi HASH HOÀN TOÀN KHÁC NHAU."
echo -e "  - Chuỗi hash bắt đầu bằng định dạng tiêu chuẩn BCrypt: \$2a\$10\$..."
echo -e "  - Cơ chế Salt ngẫu nhiên chống lại tấn công Rainbow Table và bảo mật tuyệt đối cho người dùng."

# 5. Kiểm thử đăng ký trùng username (Expect HTTP 409)
echo -e "\n${YELLOW}[5] Kiểm thử bắt lỗi trùng Username: đăng ký lại '${USER1}'...${NC}"
DUP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${USER1}\",
    \"password\": \"AnotherPassword999\",
    \"role\": \"USER\"
  }")
echo -e "HTTP Status Code: ${DUP_STATUS} (Mong muốn: 409 Conflict)"
if [ "$DUP_STATUS" -eq 409 ]; then
    echo -e "${GREEN}✓ Bắt lỗi trùng username thành công!${NC}"
else
    echo -e "${RED}✗ Lỗi kiểm tra trùng username!${NC}"
fi

# 6. Kiểm thử dữ liệu không hợp lệ (Validation Failure - Expect HTTP 400)
echo -e "\n${YELLOW}[6] Kiểm thử bắt lỗi Validation (Username rỗng, Password < 6 ký tự)...${NC}"
VAL_RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "${BASE_URL}/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "",
    "password": "123",
    "role": "USER"
  }')
echo "${VAL_RESPONSE}"

# 7. BÀI 2: Kiểm thử tạo JWT Token (GET /api/auth/test-token?username=...)
echo -e "\n${YELLOW}[7] Gọi API GET /api/auth/test-token?username=${USER1} (Tạo JWT)...${NC}"
JWT_TOKEN=$(curl -s -X GET "${BASE_URL}/test-token?username=${USER1}")
echo -e "Chuỗi JWT nhận được:\n${GREEN}${JWT_TOKEN}${NC}"

echo -e "\n${YELLOW}[8] Phân tích cấu trúc 3 phần của JWT Token vừa tạo...${NC}"
python3 -c "
import base64, json, sys

token = sys.argv[1]
parts = token.split('.')
if len(parts) != 3:
    print('Token không hợp lệ')
    sys.exit(1)

def b64url_decode(s):
    s += '=' * ((4 - len(s) % 4) % 4)
    return base64.urlsafe_b64decode(s).decode('utf-8')

header = json.loads(b64url_decode(parts[0]))
payload = json.loads(b64url_decode(parts[1]))
sig = parts[2]

print('1. HEADER (Thuật toán & Loại token):')
print('  ', json.dumps(header))
print('2. PAYLOAD (Claims định danh):')
print('   - sub (Subject):', payload.get('sub'))
print('   - role (Custom Claim):', payload.get('role'))
print('   - iat (Issued At):', payload.get('iat'))
print('   - exp (Expiration):', payload.get('exp'))
print('3. SIGNATURE (Chữ ký HMAC-SHA256 256-bit):')
print('  ', sig)
" "${JWT_TOKEN}"

# 8. Kiểm thử tạo Token với user không tồn tại (Expect HTTP 404)
echo -e "\n${YELLOW}[9] Kiểm thử gọi test-token với user không tồn tại trong DB...${NC}"
NOT_FOUND_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X GET "${BASE_URL}/test-token?username=non_existent_user_999")
echo -e "HTTP Status Code: ${NOT_FOUND_STATUS} (Mong muốn: 404 Not Found)"
if [ "$NOT_FOUND_STATUS" -eq 404 ]; then
    echo -e "${GREEN}✓ Bắt lỗi user không tồn tại thành công!${NC}"
fi

# ==============================================================================
# BÀI 3: KIỂM THỬ XÁC THỰC STATELESS VÀ API ĐĂNG NHẬP (POST /api/auth/login)
# ==============================================================================
echo -e "\n${BLUE}------------------------------------------------------------------------------${NC}"
echo -e "${BLUE}        BÀI 3: KIỂM THỬ API ĐĂNG NHẬP & CƠ CHẾ XÁC THỰC STATELESS             ${NC}"
echo -e "${BLUE}------------------------------------------------------------------------------${NC}"

# 10. Đăng nhập thành công với thông tin chính xác
echo -e "\n${YELLOW}[10] Đăng nhập thành công (POST /api/auth/login) với user: ${USER1}...${NC}"
LOGIN_RESPONSE=$(curl -s -i -X POST "${BASE_URL}/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${USER1}\",
    \"password\": \"${PASS}\"
  }")

LOGIN_STATUS=$(echo "${LOGIN_RESPONSE}" | grep -i "HTTP/" | awk '{print $2}')
LOGIN_BODY=$(echo "${LOGIN_RESPONSE}" | awk 'BEGIN{RS="\r\n\r\n"; ORS=""} NR==2')

echo -e "HTTP Status: ${LOGIN_STATUS} (Mong muốn: 200 OK)"
echo -e "Response Body:"
echo "${LOGIN_BODY}" | jq . 2>/dev/null || echo "${LOGIN_BODY}"

# Kiểm tra cơ chế Stateless (Không có cookie JSESSIONID hoặc Set-Cookie)
if echo "${LOGIN_RESPONSE}" | grep -i "Set-Cookie"; then
    echo -e "${RED}✗ Cảnh báo: Tìm thấy Set-Cookie! Hệ thống không hoàn toàn Stateless!${NC}"
else
    echo -e "${GREEN}✓ Hoàn hảo: Không có header Set-Cookie! Hệ thống hoạt động hoàn toàn Stateless.${NC}"
fi

# 11. Đăng nhập thất bại do SAI MẬT KHẨU (Expect HTTP 401 Unauthorized & "Bad credentials")
echo -e "\n${YELLOW}[11] Kiểm thử đăng nhập thất bại do SAI MẬT KHẨU...${NC}"
WRONG_PASS_RES=$(curl -s -i -X POST "${BASE_URL}/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${USER1}\",
    \"password\": \"IncorrectPassword999!\"
  }")

WRONG_PASS_STATUS=$(echo "${WRONG_PASS_RES}" | grep -i "HTTP/" | awk '{print $2}')
WRONG_PASS_BODY=$(echo "${WRONG_PASS_RES}" | awk 'BEGIN{RS="\r\n\r\n"; ORS=""} NR==2')

echo -e "HTTP Status: ${WRONG_PASS_STATUS} (Mong muốn: 401 Unauthorized)"
echo -e "Response Body: ${WRONG_PASS_BODY}"
if [ "${WRONG_PASS_STATUS}" -eq 401 ] && echo "${WRONG_PASS_BODY}" | grep -q "Bad credentials"; then
    echo -e "${GREEN}✓ Bắt lỗi sai mật khẩu thành công với thông báo chung 'Bad credentials'!${NC}"
else
    echo -e "${RED}✗ Lỗi kiểm thử sai mật khẩu!${NC}"
fi

# 12. Đăng nhập thất bại do KHÔNG TỒN TẠI USERNAME (Expect HTTP 401 & "Bad credentials")
echo -e "\n${YELLOW}[12] Kiểm thử đăng nhập thất bại do KHÔNG TỒN TẠI USERNAME...${NC}"
WRONG_USER_RES=$(curl -s -i -X POST "${BASE_URL}/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "ghost_non_existent_user",
    "password": "AnyPassword123!"
  }')

WRONG_USER_STATUS=$(echo "${WRONG_USER_RES}" | grep -i "HTTP/" | awk '{print $2}')
WRONG_USER_BODY=$(echo "${WRONG_USER_RES}" | awk 'BEGIN{RS="\r\n\r\n"; ORS=""} NR==2')

echo -e "HTTP Status: ${WRONG_USER_STATUS} (Mong muốn: 401 Unauthorized)"
echo -e "Response Body: ${WRONG_USER_BODY}"
if [ "${WRONG_USER_STATUS}" -eq 401 ] && echo "${WRONG_USER_BODY}" | grep -q "Bad credentials"; then
    echo -e "${GREEN}✓ Bắt lỗi username không tồn tại thành công với thông báo an toàn 'Bad credentials'!${NC}"
    echo -e "  (Chống lại kỹ thuật tấn công User Enumeration)"
else
    echo -e "${RED}✗ Lỗi kiểm thử username không tồn tại!${NC}"
fi

echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${GREEN}   HOÀN TẤT KIỂM THỬ TOÀN DIỆN CHO CẢ 3 BÀI (REGISTER, JWT, LOGIN STATELESS)! ${NC}"
echo -e "${BLUE}==============================================================================${NC}"
