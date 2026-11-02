package com.asphalt.app

import com.asphalt.app.data.PhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumbersTest {
    @Test fun normalizesLocalInternationalAndArabicDigits() {
        assertEquals("201012345678", PhoneNumbers.normalize("01012345678"))
        assertEquals("201012345678", PhoneNumbers.normalize("+20 101 234 5678"))
        assertEquals("201012345678", PhoneNumbers.normalize("00201012345678"))
        assertEquals("201012345678", PhoneNumbers.normalize("٠١٠١٢٣٤٥٦٧٨"))
    }

    @Test fun e164RejectsNonEgyptianOrShortNumbers() {
        assertEquals("+201012345678", PhoneNumbers.toE164("01012345678"))
        assertNull(PhoneNumbers.toE164("12345"))
        assertNull(PhoneNumbers.toE164("+14155550123"))
    }
}
