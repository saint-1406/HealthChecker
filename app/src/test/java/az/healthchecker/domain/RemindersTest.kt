package az.healthchecker.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RemindersTest {
    private val wed=LocalDate.parse("2026-11-18") // a Wednesday
    private val vitD=Medication("med:d","Витамин D",times=listOf("21:00","08:00"),timesPerDay=2)
    private val course=Medication("med:c","Курс",times=listOf("12:00"),startDate="2026-11-20")
    private fun at(s:String)=LocalDateTime.parse("2026-11-18T$s")

    @Test fun slotsOfADayAreSortedAndFollowTheSwitches() {
        val s=ReminderSettings(medications=true,water=true,waterFrom="09:00",waterTo="15:00",waterEveryHours=3,weigh=true,training=true,trainingDays=setOf(3),log=true)
        val slots=s.slotsOn(wed,listOf(vitD,course))
        assertEquals(listOf("08:00","08:00","09:00","12:00","15:00","18:00","21:00","21:00"),slots.map{it.at.toLocalTime().toString()})
        // Doses are numbered by time, not by the order they were typed in.
        assertEquals(0,slots.first{it.kind==ReminderKind.MEDICATION}.doseIndex)
        assertEquals(1,slots.last{it.kind==ReminderKind.MEDICATION}.doseIndex)
        // The course has not started yet; Thursday is not a training day.
        assertTrue(slots.none{it.medicationId=="med:c"})
        assertTrue(s.slotsOn(wed.plusDays(1),emptyList()).none{it.kind==ReminderKind.TRAINING})
        assertTrue(ReminderSettings().slotsOn(wed,listOf(vitD)).isEmpty())
    }

    @Test fun nextSlotLooksAheadToTheNextDay() {
        val s=ReminderSettings(weigh=true,weighAt="08:00")
        assertEquals(at("08:00").plusDays(1),s.nextSlot(emptyList(),at("08:00"))!!.at)
        assertEquals(at("08:00"),s.nextSlot(emptyList(),at("07:59"))!!.at)
        assertNull(ReminderSettings().nextSlot(emptyList(),at("07:00")))
    }

    @Test fun dueSlotsSkipStaleOnesAfterTheClockJumped() {
        val s=ReminderSettings(medications=true)
        assertEquals(1,s.dueSlots(listOf(vitD),at("07:00"),at("08:05")).size)
        // The phone was off from 07:00 to 10:00: an 08:00 reminder two hours late is not shown...
        assertTrue(s.dueSlots(listOf(vitD),at("07:00"),at("10:00")).isEmpty())
        // ...but one Android delivered an hour late while dozing still is.
        assertEquals(1,s.dueSlots(listOf(vitD),at("07:00"),at("09:00")).size)
        // Across midnight.
        assertEquals(listOf("21:00"),s.dueSlots(listOf(vitD),at("20:50"),at("21:10")).map{it.at.toLocalTime().toString()})
    }

    @Test fun remindersThatAreNoLongerNeededAreDropped() {
        val dose=ReminderSlot(ReminderKind.MEDICATION,at("21:00"),"med:d",1)
        val one=MedicationIntake(java.util.UUID.randomUUID().toString(),"med:d","Витамин D",1.0,"2026-11-18T05:00:00Z","UTC","2026-11-18")
        assertTrue(dose.stillNeeded(UserSnapshot(medicationIntakes=listOf(one))))
        assertFalse(dose.stillNeeded(UserSnapshot(medicationIntakes=listOf(one,one.copy(id=java.util.UUID.randomUUID().toString())))))
        val weigh=ReminderSlot(ReminderKind.WEIGH,at("08:00"))
        assertFalse(weigh.stillNeeded(UserSnapshot(weights=listOf(WeightEntry("2026-11-18",80.0,"2026-11-18T04:00:00Z","UTC")))))
        assertFalse(ReminderSlot(ReminderKind.LOG,at("21:00")).stillNeeded(UserSnapshot(completedDays=setOf("2026-11-18"))))
        val water=ReminderSlot(ReminderKind.WATER,at("15:00"))
        val glass=makeEntry(java.util.UUID.randomUUID().toString(),WATER_FOOD,Quantity(2000.0,Measure.ML),"2026-11-18",null,java.time.Instant.parse("2026-11-18T09:00:00Z"),java.time.ZoneId.of("UTC"))
        assertTrue(water.stillNeeded(UserSnapshot(entries=listOf(glass))))
        assertFalse(water.stillNeeded(UserSnapshot(entries=listOf(glass),goals=listOf(DailyGoals("2026-11-01",waterMl=2000.0)))))
    }

    @Test fun badTimesAreRefused() {
        assertThrows(IllegalArgumentException::class.java){validateReminders(ReminderSettings(waterFrom="22:00",waterTo="09:00"))}
        assertThrows(IllegalArgumentException::class.java){validateReminders(ReminderSettings(weighAt="8 утра"))}
        validateReminders(ReminderSettings())
    }
}
