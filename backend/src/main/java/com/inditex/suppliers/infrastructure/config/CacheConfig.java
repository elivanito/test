package com.inditex.suppliers.infrastructure.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring's caching infrastructure (Caffeine, configured in
 * {@code application.yml}).
 *
 * <p>Kept in a dedicated {@code @Configuration} rather than on the main
 * application class so that web slice tests ({@code @WebMvcTest}) — which do not
 * component-scan this package nor auto-configure a {@code CacheManager} — are not
 * forced to provide one. The full application still enables caching via the
 * {@code @SpringBootApplication} component scan.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
