package com.nagram.usbbridge.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DuplicateDecisionTest {
    @Test
    fun keepNewest_selectsOlderFileForDeletion() {
        val decision = decideDuplicate(DemoDuplicateFiles, DuplicateKeepRule.NEWEST)

        assertEquals("usb-copy", decision.kept.id)
        assertEquals("phone-original", decision.selectedForDeletion.id)
    }

    @Test
    fun keepOldest_selectsNewerFileForDeletion() {
        val decision = decideDuplicate(DemoDuplicateFiles, DuplicateKeepRule.OLDEST)

        assertEquals("phone-original", decision.kept.id)
        assertEquals("usb-copy", decision.selectedForDeletion.id)
    }

    @Test
    fun comparison_rejectsAnythingOtherThanTwoFiles() {
        assertThrows(IllegalArgumentException::class.java) {
            decideDuplicate(DemoDuplicateFiles.take(1), DuplicateKeepRule.NEWEST)
        }
    }
}
