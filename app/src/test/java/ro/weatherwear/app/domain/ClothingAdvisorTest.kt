package ro.weatherwear.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.weatherwear.app.data.model.DailyForecast
import ro.weatherwear.app.data.model.SuggestionSource
import ro.weatherwear.app.data.model.TripPurpose
import java.time.LocalDate

class ClothingAdvisorTest {

    private val advisor = ClothingAdvisor()

    private fun day(
        min: Double,
        max: Double,
        code: Int = 0,
        rain: Int = 0,
        wind: Double = 10.0,
        uv: Double = 2.0,
        date: LocalDate = LocalDate.of(2026, 11, 12),
    ) = DailyForecast(date, code, max, min, rain, 0.0, wind, uv)

    @Test
    fun `bands follow the felt temperature`() {
        assertEquals(ClothingAdvisor.Band.FREEZING, advisor.bandFor(-10.0))
        assertEquals(ClothingAdvisor.Band.COLD, advisor.bandFor(0.0))
        assertEquals(ClothingAdvisor.Band.COOL, advisor.bandFor(10.0))
        assertEquals(ClothingAdvisor.Band.MILD, advisor.bandFor(15.0))
        assertEquals(ClothingAdvisor.Band.WARM, advisor.bandFor(22.0))
        assertEquals(ClothingAdvisor.Band.HOT, advisor.bandFor(30.0))
    }

    @Test
    fun `cold day asks for a hat and gloves`() {
        val outfit = advisor.outfitFor(day(min = -2.0, max = 3.0))
        assertTrue(outfit.items.contains("Căciulă"))
        assertTrue(outfit.items.contains("Mănuși"))
        assertEquals(SuggestionSource.RULES, outfit.source)
    }

    @Test
    fun `rain probability over the threshold adds an umbrella`() {
        val outfit = advisor.outfitFor(day(min = 10.0, max = 16.0, rain = 70))
        assertTrue(outfit.items.contains("Umbrelă"))
    }

    @Test
    fun `strong wind replaces the umbrella with a hooded jacket`() {
        val outfit = advisor.outfitFor(day(min = 10.0, max = 16.0, code = 63, wind = 50.0))
        assertFalse(outfit.items.contains("Umbrelă"))
        assertTrue(outfit.items.contains("Geacă impermeabilă cu glugă"))
    }

    @Test
    fun `snow asks for waterproof shoes, not an umbrella`() {
        val outfit = advisor.outfitFor(day(min = -4.0, max = 0.0, code = 73, rain = 90))
        assertTrue(outfit.items.contains("Încălțăminte impermeabilă"))
        assertFalse(outfit.items.contains("Umbrelă"))
    }

    @Test
    fun `high uv adds sun protection`() {
        val outfit = advisor.outfitFor(day(min = 20.0, max = 31.0, uv = 8.0))
        assertTrue(outfit.items.contains("Ochelari de soare"))
        assertTrue(outfit.items.contains("Cremă cu SPF 30+"))
    }

    @Test
    fun `items are never repeated`() {
        val outfit = advisor.outfitFor(day(min = 18.0, max = 33.0, code = 95, rain = 80, wind = 40.0, uv = 9.0))
        assertEquals(outfit.items.size, outfit.items.distinct().size)
    }

    @Test
    fun `packing counts clothes per day up to a week`() {
        val short = advisor.packingFor(listOf(day(10.0, 18.0)), TripPurpose.CITY_BREAK, tripDays = 3)
        assertTrue(short.items.any { it.name == "3 perechi de șosete" })

        val long = advisor.packingFor(listOf(day(10.0, 18.0)), TripPurpose.CITY_BREAK, tripDays = 12)
        assertTrue(long.items.any { it.name == "7 perechi de șosete" })
    }

    @Test
    fun `mountain trip adds hiking gear`() {
        val list = advisor.packingFor(listOf(day(2.0, 12.0)), TripPurpose.MOUNTAIN, tripDays = 4)
        val names = list.items.map { it.name }
        assertTrue(names.contains("Bocanci de munte"))
        assertTrue(names.contains("Lanternă frontală"))
    }

    @Test
    fun `business trip adds office clothes and a laptop`() {
        val names = advisor.packingFor(listOf(day(8.0, 15.0)), TripPurpose.BUSINESS, 2).items.map { it.name }
        assertTrue(names.contains("Ținută office"))
        assertTrue(names.contains("Laptop și încărcător"))
    }

    @Test
    fun `no forecast still gives a general list`() {
        val list = advisor.packingFor(emptyList(), TripPurpose.LEISURE, tripDays = 5)
        assertTrue(list.items.isNotEmpty())
        assertTrue(list.summary.contains("Prognoza nu acoperă"))
        assertTrue(list.items.all { it.category in PackingCategory.ALL })
    }

    @Test
    fun `packing list has no duplicates and nothing packed yet`() {
        val list = advisor.packingFor(
            listOf(day(-8.0, 2.0, code = 73), day(-3.0, 5.0, code = 61, rain = 80)),
            TripPurpose.MOUNTAIN,
            tripDays = 2,
        )
        assertEquals(list.items.size, list.items.distinctBy { it.name }.size)
        assertTrue(list.items.none { it.packed })
    }
}
