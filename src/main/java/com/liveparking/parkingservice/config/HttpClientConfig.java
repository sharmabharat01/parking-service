package com.liveparking.parkingservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({
        ParkingApiProperties.class,
        LiveApiProperties.class,
        AvailabilityProperties.class
})
public class HttpClientConfig {

    @Bean
    public RestClient restClient(
            ParkingApiProperties properties) {

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean
    public RestClient liveApiRestClient(
            LiveApiProperties properties) {

        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofMillis(
                                        properties.connectTimeoutMs()
                                )
                        )
                        .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(
                Duration.ofMillis(
                        properties.readTimeoutMs()
                )
        );

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}