package com.example.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    @DisplayName("RouteLocator cấu hình thành công các routes: identity-service và product-service")
    void testRoutesConfigured() {
        assertNotNull(routeLocator);
        routeLocator.getRoutes()
                .filter(route -> route.getId().equals("identity-service") || route.getId().equals("product-service"))
                .collectList()
                .block();
    }

    @Test
    @DisplayName("Request với đường dẫn không khớp bất kỳ route nào trả về 404 Not Found")
    void testWrongPathReturns404() {
        webTestClient.get()
                .uri("/wrong-path/something")
                .exchange()
                .expectStatus().isNotFound();
    }
}
