package ro.weatherwear.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test
import ro.weatherwear.app.domain.PackingCategory

class GeminiParserTest {

    @Test
    fun `parses an outfit answer`() {
        val outfit = GeminiParser.parseOutfit(
            """{"summary": "Răcoare și ploaie.", "items": ["Geacă", " Umbrelă ", ""], "tip": "Ia umbrela."}"""
        )
        assertEquals("Răcoare și ploaie.", outfit.summary)
        assertEquals(listOf("Geacă", "Umbrelă"), outfit.items)
        assertEquals("Ia umbrela.", outfit.tip)
    }

    @Test
    fun `accepts json wrapped in a markdown fence`() {
        val outfit = GeminiParser.parseOutfit("```json\n{\"items\": [\"Tricou\"]}\n```")
        assertEquals(listOf("Tricou"), outfit.items)
        assertEquals("", outfit.summary)
    }

    @Test(expected = InvalidAiResponseException::class)
    fun `rejects an outfit with no items`() {
        GeminiParser.parseOutfit("""{"summary": "x", "items": []}""")
    }

    @Test(expected = InvalidAiResponseException::class)
    fun `rejects text that is not json`() {
        GeminiParser.parseOutfit("Poartă o geacă.")
    }

    @Test
    fun `parses packing items and fixes unknown categories`() {
        val packing = GeminiParser.parsePacking(
            """{"summary": "s", "tip": "t", "items": [
                {"name": "3 tricouri", "category": "Haine"},
                {"name": "Pașaport", "category": "Acte"},
                {"name": "3 TRICOURI", "category": "Haine"},
                {"name": "", "category": "Haine"}
            ]}"""
        )
        assertEquals(2, packing.items.size)
        assertEquals(PackingCategory.CLOTHES, packing.items[0].category)
        assertEquals(PackingCategory.ACCESSORIES, packing.items[1].category)
    }

    @Test
    fun `reads the text of the first candidate`() {
        val response = GeminiResponse(
            listOf(GeminiCandidate(GeminiContent(parts = listOf(GeminiPart(null), GeminiPart("{}")))))
        )
        assertEquals("{}", GeminiParser.textOf(response))
        assertEquals(null, GeminiParser.textOf(GeminiResponse(null)))
    }
}
