package com.inditex.suppliers.infrastructure.rest;

import com.inditex.suppliers.application.port.in.AcceptCandidateUseCase;
import com.inditex.suppliers.application.port.in.CreateCandidateUseCase;
import com.inditex.suppliers.application.port.in.GetCandidateUseCase;
import com.inditex.suppliers.application.port.in.RefuseCandidateUseCase;
import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.infrastructure.rest.dto.CandidateAcceptDto;
import com.inditex.suppliers.infrastructure.rest.dto.CandidateDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/candidates")
@Validated
public class CandidatesController {

    private final CreateCandidateUseCase create;
    private final GetCandidateUseCase get;
    private final AcceptCandidateUseCase accept;
    private final RefuseCandidateUseCase refuse;

    public CandidatesController(CreateCandidateUseCase create,
                                GetCandidateUseCase get,
                                AcceptCandidateUseCase accept,
                                RefuseCandidateUseCase refuse) {
        this.create = create;
        this.get = get;
        this.accept = accept;
        this.refuse = refuse;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateDto create(@Valid @RequestBody CandidateDto body) {
        Candidate created = create.create(new CreateCandidateUseCase.Command(
                body.duns(), body.name(), body.country(), body.annualTurnover()
        ));
        return RestMapper.toDto(created);
    }

    @GetMapping("/{duns}")
    public CandidateDto get(@PathVariable @Min(100_000_000L) @Max(999_999_999L) long duns) {
        return RestMapper.toDto(get.get(duns));
    }

    @PostMapping("/{duns}/accept")
    public ResponseEntity<Void> accept(@PathVariable @Min(100_000_000L) @Max(999_999_999L) long duns,
                                       @Valid @RequestBody CandidateAcceptDto body) {
        accept.accept(duns, body.sustainabilityRating());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{duns}/refuse")
    public ResponseEntity<Void> refuse(@PathVariable @Min(100_000_000L) @Max(999_999_999L) long duns) {
        refuse.refuse(duns);
        return ResponseEntity.noContent().build();
    }
}
