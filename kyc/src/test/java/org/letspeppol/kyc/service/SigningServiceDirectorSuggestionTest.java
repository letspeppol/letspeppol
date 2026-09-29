package org.letspeppol.kyc.service;

import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.kbo.Director;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SigningServiceDirectorSuggestionTest {

    private static final X500Name EID_NAME = new X500Name("GIVENNAME=Jan,SURNAME=Peeters");

    @Test
    void suggestsTheOnlyDirectorMatchingTheEid() {
        assertThat(SigningService.findMatchingDirectorId(EID_NAME, List.of(
                director(1L, "Piet Janssens"), director(2L, "Jan Peeters"))))
                .isEqualTo(2L);
    }

    @Test
    void doesNotGuessWhenNoDirectorOrSeveralDirectorsMatch() {
        assertThat(SigningService.findMatchingDirectorId(EID_NAME, List.of(
                director(1L, "Piet Janssens")))).isNull();
        assertThat(SigningService.findMatchingDirectorId(EID_NAME, List.of(
                director(2L, "Jan Peeters"), director(3L, "Jan Peeters")))).isNull();
    }

    private static Director director(Long id, String name) {
        Director director = new Director();
        director.setId(id);
        director.setName(name);
        return director;
    }
}
