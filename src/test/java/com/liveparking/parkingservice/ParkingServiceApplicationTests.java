package com.liveparking.parkingservice;

import com.liveparking.parkingservice.integration.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "parking.availability.scheduler-enabled=false",
        "spring.task.scheduling.enabled=false"
})
class ParkingServiceApplicationTests extends PostgresIntegrationTest {

    @Test
    void contextLoads() {
    }
}