package com.inditex.suppliers.infrastructure.client.country;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "country.service")
public class CountryServiceProperties {
    private String baseUrl = "http://localhost:8088";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}
