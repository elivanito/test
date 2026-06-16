package com.inditex.suppliers.application.service;

import com.inditex.suppliers.application.port.out.CandidateRepository;
import com.inditex.suppliers.domain.exception.CandidateNotFoundException;
import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.domain.model.CandidateState;
import com.inditex.suppliers.domain.vo.AnnualTurnover;
import com.inditex.suppliers.domain.vo.CountryCode;
import com.inditex.suppliers.domain.vo.Duns;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetCandidateServiceTest {

    private CandidateRepository candidates;
    private GetCandidateService service;

    @BeforeEach
    void setup() {
        candidates = mock(CandidateRepository.class);
        service = new GetCandidateService(candidates);
    }

    @Test
    void returns_candidate_when_found() {
        Candidate expected = Candidate.apply(Duns.of(123_456_789L), "Acme",
                CountryCode.of("ES"), AnnualTurnover.of(1_500_000L));
        when(candidates.findActiveByDuns(any())).thenReturn(Optional.of(expected));

        Candidate result = service.get(123_456_789L);

        assertThat(result.duns()).isEqualTo(Duns.of(123_456_789L));
        assertThat(result.state()).isEqualTo(CandidateState.PENDING);
    }

    @Test
    void throws_when_not_found() {
        when(candidates.findActiveByDuns(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(123_456_789L))
                .isInstanceOf(CandidateNotFoundException.class);
    }
}
