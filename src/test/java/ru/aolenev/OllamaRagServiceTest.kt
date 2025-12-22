package ru.aolenev

import ru.aolenev.services.OllamaRagService
import kotlin.test.Test
import kotlin.test.assertEquals

class OllamaRagServiceTest {

    @Test
    fun `split by chunks`() {
        val text = "asdf ghjk qwe rty uio plm nbv cxz"
        val chunks = OllamaRagService().splitByChunks(text, chunkSize = 3, overlap = 1)
        println(chunks)
        assertEquals(4, chunks.size)
        assertEquals(chunks[3].split(" ").size, 2)
    }
}