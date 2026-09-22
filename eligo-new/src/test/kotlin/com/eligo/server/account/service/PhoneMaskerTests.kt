package com.eligo.server.account.service

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.assertj.core.api.Assertions.assertThat

class PhoneMaskerTests {

    private val masker = PhoneMasker()

    @ParameterizedTest
    @CsvSource(
        "1234567,12*4567",
        "12345678,123*5678",
        "13800121234,138****1234",
        "12345678901234567890,123*************7890"
    )
    fun masksWithoutOverlappingVisibleRegionsAndAlwaysHidesDigits(
        phone: String,
        expected: String
    ) {
        val masked = masker.mask(phone)

        assertThat(masked).isEqualTo(expected)
        assertThat(masked).hasSameSizeAs(phone)
        assertThat(masked).contains("*")
    }
}
