package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class AppearanceTest {
    private fun d(s:String)=LocalDate.parse(s)
    @Test fun holidaysRunFromAWeekBeforeToAWeekAfter() {
        assertNull(seasonOn(d("2026-10-24")))
        assertEquals(Season.HALLOWEEN,seasonOn(d("2026-10-25")));assertEquals(Season.HALLOWEEN,seasonOn(d("2026-10-31")));assertEquals(Season.HALLOWEEN,seasonOn(d("2026-11-07")))
        assertNull(seasonOn(d("2026-11-08")));assertNull(seasonOn(d("2026-12-25")))
        assertEquals(Season.NEW_YEAR,seasonOn(d("2026-12-26")));assertEquals(Season.NEW_YEAR,seasonOn(d("2027-01-01")));assertEquals(Season.NEW_YEAR,seasonOn(d("2027-01-08")))
        assertNull(seasonOn(d("2027-01-09")));assertNull(seasonOn(d("2027-06-15")))
        // One New Year period spans two calendar years but has one key and one end.
        assertEquals("newyear-2027",seasonKey(Season.NEW_YEAR,d("2026-12-30")));assertEquals("newyear-2027",seasonKey(Season.NEW_YEAR,d("2027-01-05")))
        assertEquals(d("2027-01-08"),seasonEnd(Season.NEW_YEAR,d("2026-12-28")));assertEquals(d("2026-11-07"),seasonEnd(Season.HALLOWEEN,d("2026-10-26")))
    }
    @Test fun holidayThemeComesByItselfAndAChoiceTurnsItOffUntilTheNextHoliday() {
        val a=Appearance(palette="ocean")
        assertEquals("ocean",a.effectivePalette(d("2026-10-24")));assertEquals("halloween",a.effectivePalette(d("2026-10-28")))
        // Choosing another colour during Halloween wins for the rest of Halloween…
        val b=a.choose("berry",d("2026-10-28"))
        assertEquals("berry",b.effectivePalette(d("2026-10-28")));assertEquals("berry",b.effectivePalette(d("2026-11-07")));assertNull(b.activeSeason(d("2026-11-01")))
        // …but New Year switches on again by itself, and so does next year's Halloween.
        assertEquals("newyear",b.effectivePalette(d("2026-12-27")));assertEquals("halloween",b.effectivePalette(d("2027-10-30")))
        // Picking the holiday's own theme brings it back.
        assertEquals("halloween",b.choose("halloween",d("2026-10-29")).effectivePalette(d("2026-10-29")))
        // With holiday themes off only the chosen palette counts; any palette can still be picked by hand.
        val off=a.copy(seasonal=false)
        assertEquals("ocean",off.effectivePalette(d("2026-10-28")));assertEquals("newyear",off.choose("newyear",d("2026-06-01")).effectivePalette(d("2026-06-01")))
        // A holiday theme tried in June brings its decorations; a plain palette has none.
        assertEquals(Season.NEW_YEAR,off.choose("newyear",d("2026-06-01")).decorationSeason(d("2026-06-01")));assertNull(off.decorationSeason(d("2026-10-28")))
    }
    @Test fun hslGivesTheExpectedColours() {
        assertEquals(0xFFFF0000.toInt(),hsl(0f,1f,0.5f));assertEquals(0xFF00FF00.toInt(),hsl(120f,1f,0.5f));assertEquals(0xFF0000FF.toInt(),hsl(240f,1f,0.5f))
        assertEquals(0xFFFFFFFF.toInt(),hsl(77f,0f,1f));assertEquals(21.0,contrast(0xFF000000.toInt(),0xFFFFFFFF.toInt()),0.01)
    }
    @Test fun everyPaletteAndHueKeepsTextReadableInLightAndDark() {
        val ids=PALETTES.map{it.id}.filter{it!="mint"}+listOf("halloween","newyear")
        val schemes=ids.flatMap{id->listOf(false,true).map{"$id/$it" to paletteScheme(id,0f,it)!!}}+
            (0 until 360 step 15).flatMap{h->listOf(false,true).map{"custom $h/$it" to paletteScheme("custom",h.toFloat(),it)!!}}
        for((name,s) in schemes){
            assertTrue("$name primary",contrast(s.primary,s.onPrimary)>=4.5)
            assertTrue("$name primary container",contrast(s.primaryContainer,s.onPrimaryContainer)>=4.5)
            assertTrue("$name secondary container",contrast(s.secondaryContainer,s.onSecondaryContainer)>=4.5)
            assertTrue("$name tertiary container",contrast(s.tertiaryContainer,s.onTertiaryContainer)>=4.5)
            assertTrue("$name text",contrast(s.surface,s.onSurface)>=7&&contrast(s.background,s.onBackground)>=7)
            assertTrue("$name secondary text",contrast(s.surfaceVariant,s.onSurfaceVariant)>=4.5&&contrast(s.surface,s.onSurfaceVariant)>=4.5)
            // Buttons, icons and field borders stand out from the background.
            assertTrue("$name accents",contrast(s.background,s.primary)>=3&&contrast(s.background,s.outline)>=3&&contrast(s.surface,s.tertiary)>=3)
        }
        assertNull(paletteScheme("mint",0f,false))
    }
}
