package com.inditex.suppliers.infrastructure.client.country;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CountryServiceProperties.class)
public class CountryClientConfig {

    @Bean
    public RestClient countryRestClient(CountryServiceProperties props) {
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(clientHttpRequestFactory())
                .build();
    }

    private static ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        return factory;
    }
}
