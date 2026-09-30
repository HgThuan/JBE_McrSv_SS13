#!/bin/bash

# ==============================================================================
# SCRIPT KIỂM THỬ TỰ ĐỘNG SPRING CLOUD API GATEWAY (SS13 - BÀI 4)
# ==============================================================================

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

GATEWAY_URL="http://localhost:8888"
IDENTITY_URL="http://localhost:8080"

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}          KIỂM THỬ SPRING CLOUD API GATEWAY (PORT 8888) - BÀI 4               ${NC}"
echo -e "${BLUE}==============================================================================${NC}"

# 1. Kiểm tra trạng thái cổng
echo -e "\n${YELLOW}[1] Kiểm tra trạng thái dịch vụ...${NC}"
if ! nc -z localhost 8080; then
    echo -e "${RED}Lỗi: Identity Service chưa chạy trên port 8080!${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Identity Service đang chạy trên port 8080!${NC}"

if ! nc -z localhost 8888; then
    echo -e "${RED}Lỗi: Gateway Service chưa chạy trên port 8888!${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Gateway Service đang chạy trên port 8888!${NC}"

# 2. Đăng ký User qua Gateway (POST http://localhost:8888/identity/api/auth/register)
GW_USER="gw_user_$(date +%s)"
GW_PASS="GatewayPass@123"

echo -e "\n${YELLOW}[2] Đăng ký User qua Gateway (POST ${GATEWAY_URL}/identity/api/auth/register)...${NC}"
echo -e "    Filter StripPrefix=1 sẽ cắt bỏ /identity -> forward đến ${IDENTITY_URL}/api/auth/register"
REG_RES=$(curl -s -i -X POST "${GATEWAY_URL}/identity/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${GW_USER}\",
    \"password\": \"${GW_PASS}\",
    \"role\": \"USER\"
  }")

REG_STATUS=$(echo "${REG_RES}" | grep -i "HTTP/" | awk '{print $2}')
REG_BODY=$(echo "${REG_RES}" | awk 'BEGIN{RS="\r\n\r\n"; ORS=""} NR==2')
echo -e "HTTP Status: ${REG_STATUS} (Mong muốn: 201 Created)"
echo -e "Response Body: ${REG_BODY}"

if [ "${REG_STATUS}" -eq 201 ]; then
    echo -e "${GREEN}✓ Đăng ký tài khoản qua Gateway thành công!${NC}"
else
    echo -e "${RED}✗ Lỗi đăng ký qua Gateway!${NC}"
fi

# 3. Đăng nhập qua Gateway (POST http://localhost:8888/identity/api/auth/login)
echo -e "\n${YELLOW}[3] Đăng nhập qua Gateway (POST ${GATEWAY_URL}/identity/api/auth/login)...${NC}"
LOGIN_RES=$(curl -s -i -X POST "${GATEWAY_URL}/identity/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${GW_USER}\",
    \"password\": \"${GW_PASS}\"
  }")

LOGIN_STATUS=$(echo "${LOGIN_RES}" | grep -i "HTTP/" | awk '{print $2}')
LOGIN_BODY=$(echo "${LOGIN_RES}" | awk 'BEGIN{RS="\r\n\r\n"; ORS=""} NR==2')
echo -e "HTTP Status: ${LOGIN_STATUS} (Mong muốn: 200 OK)"
echo -e "Response Body:"
echo "${LOGIN_BODY}" | jq . 2>/dev/null || echo "${LOGIN_BODY}"

if [ "${LOGIN_STATUS}" -eq 200 ]; then
    echo -e "${GREEN}✓ Nhận Access Token JWT thành công qua Gateway!${NC}"
else
    echo -e "${RED}✗ Lỗi đăng nhập qua Gateway!${NC}"
fi

# 4. Kiểm tra điều hướng đa dịch vụ (Mô phỏng Product Service qua Gateway)
echo -e "\n${YELLOW}[4] Kiểm tra định tuyến đa dịch vụ (GET ${GATEWAY_URL}/product/api/products)...${NC}"
echo -e "    Filter StripPrefix=1 sẽ cắt bỏ /product -> forward đến ${IDENTITY_URL}/api/products"
PROD_RES=$(curl -s -i -X GET "${GATEWAY_URL}/product/api/products")
PROD_STATUS=$(echo "${PROD_RES}" | grep -i "HTTP/" | awk '{print $2}')
PROD_BODY=$(echo "${PROD_RES}" | awk 'BEGIN{RS="\r\n\r\n"; ORS=""} NR==2')
echo -e "HTTP Status: ${PROD_STATUS} (Mong muốn: 200 OK)"
echo -e "Response Body:"
echo "${PROD_BODY}" | jq . 2>/dev/null || echo "${PROD_BODY}"

if [ "${PROD_STATUS}" -eq 200 ]; then
    echo -e "${GREEN}✓ Định tuyến đa dịch vụ (Product Service) qua Gateway thành công!${NC}"
else
    echo -e "${RED}✗ Lỗi định tuyến đa dịch vụ!${NC}"
fi

# 5. Kiểm thử gọi sai tiền tố (Expect 404 Not Found từ Gateway)
echo -e "\n${YELLOW}[5] Kiểm thử gọi sai tiền tố (GET ${GATEWAY_URL}/wrong-path/something)...${NC}"
WRONG_RES=$(curl -s -i -X GET "${GATEWAY_URL}/wrong-path/something")
WRONG_STATUS=$(echo "${WRONG_RES}" | grep -i "HTTP/" | awk '{print $2}')
WRONG_BODY=$(echo "${WRONG_RES}" | awk 'BEGIN{RS="\r\n\r\n"; ORS=""} NR==2')
echo -e "HTTP Status: ${WRONG_STATUS} (Mong muốn: 404 Not Found)"
echo -e "Response Body: ${WRONG_BODY}"

if [ "${WRONG_STATUS}" -eq 404 ]; then
    echo -e "${GREEN}✓ Gateway chặn và trả về 404 Not Found chính xác khi gọi sai route!${NC}"
else
    echo -e "${RED}✗ Lỗi kiểm thử route không tồn tại!${NC}"
fi

echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${GREEN}      HOÀN TẤT KIỂM THỬ TOÀN DIỆN CHO SPRING CLOUD API GATEWAY!              ${NC}"
echo -e "${BLUE}==============================================================================${NC}"
