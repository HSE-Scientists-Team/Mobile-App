package org.matchlab.academic_net.feature.profile.ui.my

import kotlin.test.Test
import kotlin.test.assertEquals

class ProofFilesTest {
    @Test
    fun theTypeIsTheOneOfTheExtensionWhateverTheCase() {
        assertEquals("application/pdf", proofContentType("Диплом.PDF"))
        assertEquals("image/jpeg", proofContentType("scan.jpg"))
        assertEquals("image/jpeg", proofContentType("scan.JPEG"))
        assertEquals("image/png", proofContentType("a.b.png"))
    }

    @Test
    fun aFileOfAnotherKindOrWithoutAnExtensionIsAPlainStream() {
        assertEquals("application/octet-stream", proofContentType("notes.txt"))
        assertEquals("application/octet-stream", proofContentType("noextension"))
        assertEquals("application/octet-stream", proofContentType(""))
    }

    @Test
    fun theOfferedExtensionsAreTheTypesTheStorageIsKnownToTake() {
        assertEquals(listOf("pdf", "jpg", "jpeg", "png"), ProofFileExtensions)
    }
}
