package com.inditex.suppliers.infrastructure.persistence.query;

import com.inditex.suppliers.application.dto.PotentialSupplierPage;
import com.inditex.suppliers.application.dto.PotentialSupplierView;
import com.inditex.suppliers.application.port.out.PotentialSupplierQuery.Filter;
import com.inditex.suppliers.application.port.out.PotentialSupplierQuery.Pagination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the potential-suppliers SQL query against a real PostgreSQL,
 * including the +25% bonus for the two lowest unique turnovers per country.
 *
 * Reproduces the README example:
 *   ES: 200k (s1), 200k (s2), 200k (s3), 210k (s4), 250k (s5)
 *   → s1..s4 receive the bonus, s5 does not.
 */
@Testcontainers
@SpringBootTest(classes = PotentialSupplierJdbcQueryIT.TestApp.class)
@org.junit.jupiter.api.condition.EnabledIf("dockerAvailable")
class PotentialSupplierJdbcQueryIT {

    @SuppressWarnings("unused")
    static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }


    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.flyway.locations", () -> "classpath:db/migration");
        // We only want the schema migration; skip the seed for this isolated test.
        r.add("spring.flyway.target", () -> "1");
    }

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    PotentialSupplierJdbcQuery query;

    @BeforeEach
    void wipeAndSeed() {
        query = new PotentialSupplierJdbcQuery(jdbc,
                new com.inditex.suppliers.application.util.CursorCodec("test-key".getBytes()));
        jdbc.getJdbcTemplate().execute("TRUNCATE suppliers");
        // README example, in country ES
        insert(1, "s1", "ES", 200_000, 'A', "ACTIVE");
        insert(2, "s2", "ES", 200_000, 'A', "ACTIVE");
        insert(3, "s3", "ES", 200_000, 'A', "ACTIVE");
        insert(4, "s4", "ES", 210_000, 'A', "ACTIVE");
        insert(5, "s5", "ES", 250_000, 'A', "ACTIVE");
        // Another country, irrelevant
        insert(6, "p1", "PT", 1_000_000, 'A', "ACTIVE");
        // Disqualified must be excluded
        insert(7, "d1", "ES", 5_000_000, 'A', "DISQUALIFIED");
    }

    private void insert(long duns, String name, String country, long turnover, char rating, String status) {
        jdbc.getJdbcTemplate().update(
                "INSERT INTO suppliers (duns, name, country, annual_turnover, sustainability_rating, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                100_000_000L + duns, name, country, turnover, String.valueOf(rating), status);
    }

    @Test
    void applies_bonus_to_the_two_lowest_unique_turnovers_per_country() {
        PotentialSupplierPage page = query.findPotential(250L, 10, 0);

        // s5 has no bonus → 250_000 * 0.1 * 1.0 = 25_000
        // s4 has bonus    → 210_000 * 0.1 * 1.0 * 1.25 = 26_250
        // s1..s3 have bonus → 200_000 * 0.1 * 1.0 * 1.25 = 25_000 each
        // PT s6           → 1_000_000 * 0.1 * 1.0 * 1.25 (only one turnover in PT, rank 1) = 125_000

        // Exclude disqualified
        assertThat(page.data()).noneMatch(v -> v.duns() == 100_000_007L);
        assertThat(page.total()).isEqualTo(6L);

        // Score map
        var byDuns = page.data().stream()
                .collect(java.util.stream.Collectors.toMap(PotentialSupplierView::duns, PotentialSupplierView::score));

        assertThat(byDuns.get(100_000_006L)).isEqualTo(125_000.0);
        assertThat(byDuns.get(100_000_004L)).isEqualTo(26_250.0);
        assertThat(byDuns.get(100_000_001L)).isEqualTo(25_000.0);
        assertThat(byDuns.get(100_000_002L)).isEqualTo(25_000.0);
        assertThat(byDuns.get(100_000_003L)).isEqualTo(25_000.0);
        assertThat(byDuns.get(100_000_005L)).isEqualTo(25_000.0);

        // Ordered by score desc
        assertThat(page.data().get(0).duns()).isEqualTo(100_000_006L);
        assertThat(page.data().get(1).duns()).isEqualTo(100_000_004L);
    }

    @Test
    void filters_by_rate() {
        PotentialSupplierPage page = query.findPotential(220_000L, 10, 0);
        assertThat(page.data()).extracting(PotentialSupplierView::duns)
                .containsExactlyInAnyOrder(100_000_005L, 100_000_006L);
    }

    @Test
    void paginates() {
        PotentialSupplierPage page = query.findPotential(250L, 2, 0);
        assertThat(page.data()).hasSize(2);
        assertThat(page.total()).isEqualTo(6L);
        assertThat(page.limit()).isEqualTo(2);
        assertThat(page.offset()).isZero();

        PotentialSupplierPage empty = query.findPotential(250L, 10, 100);
        assertThat(empty.data()).isEmpty();
        assertThat(empty.total()).isEqualTo(6L);
    }

    @Test
    void filters_by_country_server_side() {
        var pagination = new Pagination.Offset(0);
        var filter = new Filter(250L, 10, pagination, "PT", null);
        PotentialSupplierPage page = query.findPotential(filter);
        assertThat(page.data()).extracting(PotentialSupplierView::duns)
                .containsExactly(100_000_006L); // only the PT supplier
        assertThat(page.total()).isEqualTo(1L);
    }

    @Test
    void filters_by_max_rating_server_side() {
        // Add a C-rated supplier so the filter can discriminate.
        insert(8, "c1", "ES", 400_000, 'C', "ACTIVE");

        PotentialSupplierPage allRatings = query.findPotential(250L, 50, 0);
        PotentialSupplierPage onlyAB = query.findPotential(new Filter(
                250L, 50, new Pagination.Offset(0), null,
                com.inditex.suppliers.domain.vo.SustainabilityRating.B));

        assertThat(allRatings.total()).isEqualTo(7L);
        assertThat(onlyAB.total()).isEqualTo(6L);
        assertThat(onlyAB.data()).extracting(PotentialSupplierView::sustainabilityRating)
                .allMatch(r -> r == com.inditex.suppliers.domain.vo.SustainabilityRating.A
                            || r == com.inditex.suppliers.domain.vo.SustainabilityRating.B);
    }

    @Test
    void keyset_pagination_traverses_full_dataset_without_offset() {
        var codec = new com.inditex.suppliers.application.util.CursorCodec("test-key".getBytes());

        PotentialSupplierPage page1 = query.findPotential(new Filter(
                250L, 3, new Pagination.Keyset(null), null, null));
        assertThat(page1.data()).hasSize(3);
        assertThat(page1.offset()).isZero();
        assertThat(page1.total()).isNull(); // intentionally absent in keyset mode
        assertThat(page1.nextCursor()).isNotBlank();

        // Decode the cursor and continue paginating.
        var cursor = codec.decode(page1.nextCursor());
        PotentialSupplierPage page2 = query.findPotential(new Filter(
                250L, 3, new Pagination.Keyset(cursor), null, null));
        assertThat(page2.data()).hasSize(3);

        // No overlap between consecutive keyset pages.
        var page1Duns = page1.data().stream().map(PotentialSupplierView::duns).toList();
        var page2Duns = page2.data().stream().map(PotentialSupplierView::duns).toList();
        assertThat(page1Duns).doesNotContainAnyElementsOf(page2Duns);
    }

    @Test
    void keyset_last_page_has_null_nextCursor() {
        var filter = new Filter(250L, 100, new Pagination.Keyset(null), null, null);
        PotentialSupplierPage page = query.findPotential(filter);
        assertThat(page.data()).hasSize(6);
        assertThat(page.nextCursor()).isNull(); // exhausted
    }

    @org.springframework.boot.SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class,
            org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration.class
    })
    @ComponentScan(
            basePackages = "com.inditex.suppliers.infrastructure.persistence.query",
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = PotentialSupplierJdbcQuery.class))
    static class TestApp {}
}
