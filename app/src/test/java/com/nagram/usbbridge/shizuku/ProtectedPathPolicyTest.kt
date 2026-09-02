package com.nagram.usbbridge.shizuku

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedPathPolicyTest {
    @Test
    fun `allows files below Android data and obb roots`() {
        assertTrue(ProtectedPathPolicy.isAllowed(File("/storage/emulated/0/Android/data/com.example.app/files/movie.mp4")))
        assertTrue(ProtectedPathPolicy.isAllowed(File("/storage/emulated/0/Android/obb/com.example.app/main.10.com.example.app.obb")))
    }

    @Test
    fun `rejects paths outside protected shared-storage roots`() {
        assertFalse(ProtectedPathPolicy.isAllowed(File("/storage/emulated/0/Download/movie.mp4")))
        assertFalse(ProtectedPathPolicy.isAllowed(File("/data/user/0/com.example.app/files/movie.mp4")))
    }
}
