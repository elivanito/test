package com.inditex.suppliers.infrastructure.client.country;

/** Wire representation matching the country-service OpenAPI schema. */
public record CountryResponse(String name, boolean isBanned) {}
