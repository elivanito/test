package com.inditex.suppliers.application.port.out;

import com.inditex.suppliers.domain.vo.CountryCode;

public interface CountryGateway {

    /**
     * @return true if the given country is on the non-approved list.
     * @throws com.inditex.suppliers.domain.exception.CountryUnknownException
     *         if the country is unknown to the upstream service.
     */
    boolean isBanned(CountryCode country);
}
