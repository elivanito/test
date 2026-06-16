package com.inditex.suppliers.application.port.out;

import com.inditex.suppliers.domain.model.Candidate;
import com.inditex.suppliers.domain.vo.Duns;

import java.util.Optional;

public interface CandidateRepository {

    /** Returns the active (pending) candidacy for the given DUNS, if any. */
    Optional<Candidate> findActiveByDuns(Duns duns);

    /** Returns the candidacy for the given DUNS regardless of state. */
    Optional<Candidate> findByDuns(Duns duns);

    /** True if a pending candidacy exists for the given DUNS. */
    boolean existsActiveByDuns(Duns duns);

    /** Persist (insert or update) the candidate aggregate. */
    void save(Candidate candidate);

    /** Remove the candidacy for the given DUNS (used on acceptance). */
    void deleteByDuns(Duns duns);
}
