package one.lab5.kleidi.tenant.api

import one.lab5.kleidi.tenant.api.models.TenantRent
import one.lab5.kleidi.tenant.api.models.TenantWireDeclaration
import one.lab5.kleidi.tenant.core.RentMonthStatus
import one.lab5.kleidi.tenant.core.RentProofLine
import one.lab5.kleidi.tenant.core.WireDeclarationStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RentDecodingTest {
    @Test
    fun decodesTheRentViewNewestMonthFirst() {
        val rent = KleidiJson.decodeFromString(TenantRent.serializer(), RENT_JSON)

        assertEquals("l1", rent.leaseId)
        assertEquals("EUR", rent.currencyCode)
        assertEquals(800.0, rent.monthlyRent)
        assertEquals(5, rent.paymentDayOfMonth)
        assertEquals(listOf("2026-10", "2026-09", "2026-08"), rent.months.map { it.period })

        val october = rent.newestMonth!!
        assertEquals(RentMonthStatus.Due, october.monthStatus)
        assertEquals(Instant.parse("2026-10-05T00:00:00Z"), october.dueDate)
        assertTrue(october.canDeclare)
        assertNull(october.declaration)
        assertFalse(october.showsRejection)
        assertEquals(RentProofLine.None, october.proofLine)

        val september = rent.months[1]
        assertEquals(RentMonthStatus.Declared, september.monthStatus)
        assertEquals(WireDeclarationStatus.Pending, september.declaration?.declarationStatus)
        assertEquals(Instant.parse("2026-09-03T00:00:00Z"), september.declaration?.transferDate)
        assertEquals(Instant.parse("2026-09-04T08:30:00Z"), september.declaration?.declaredAt)
        assertFalse(september.canDeclare)
        assertEquals(RentProofLine.ViewProof, september.proofLine)

        val august = rent.months[2]
        assertEquals(RentMonthStatus.Paid, august.monthStatus)
        assertTrue(august.confirmedByWire)
        assertEquals(Instant.parse("2026-08-04T00:00:00Z"), august.paidAt)
        assertEquals(RentProofLine.DeclaredAndViewProof, august.proofLine)

        assertTrue(rent.canDeclare)
        assertEquals(listOf("2026-10"), rent.declarableMonths.map { it.period })
    }

    @Test
    fun aRejectedDeclarationOnAnOwedMonthShowsTheNote() {
        val json = RENT_JSON
            .replace("\"status\": \"declared\"", "\"status\": \"overdue\"")
            .replace("\"status\": \"pending\"", "\"status\": \"rejected\"")
            .replace("\"rejectionNote\": null", "\"rejectionNote\": \"Wrong IBAN\"")
        val month = KleidiJson.decodeFromString(TenantRent.serializer(), json).months[1]

        assertEquals(RentMonthStatus.Overdue, month.monthStatus)
        assertTrue(month.showsRejection)
        assertEquals("Wrong IBAN", month.declaration?.rejectionNote)
        assertEquals(RentProofLine.DeclaredAndViewProof, month.proofLine)
    }

    @Test
    fun theCurrentMonthIsFoundByItsPeriodNotByPosition() {
        val rent = KleidiJson.decodeFromString(TenantRent.serializer(), RENT_JSON)
        val newest = rent.newestMonth!!
        assertEquals(newest, rent.currentMonth(Instant.parse("${newest.period}-10T08:00:00Z")))
        // A lease's last weeks: this month has no row, the newest one is an earlier month.
        assertNull(rent.currentMonth(Instant.parse("2031-01-10T08:00:00Z")))
    }

    @Test
    fun unknownStatusesStillDecode() {
        val json = RENT_JSON.replace("\"status\": \"due\"", "\"status\": \"waived\"").replace("\"status\": \"pending\"", "\"status\": \"withdrawn\"")
        val rent = KleidiJson.decodeFromString(TenantRent.serializer(), json)
        assertEquals("waived", rent.months[0].status)
        assertNull(rent.months[0].monthStatus.labelKey)
        assertEquals("withdrawn", rent.months[1].declaration?.status)
        assertFalse(rent.months[1].showsRejection)
        // Unknown declaration status on a declared month: the row still offers the proof.
        assertEquals(RentProofLine.ViewProof, rent.months[1].proofLine)
    }

    @Test
    fun decodesADeclaration() {
        val declaration = KleidiJson.decodeFromString(TenantWireDeclaration.serializer(), DECLARATION_JSON)
        assertEquals("d1", declaration.id)
        assertEquals(listOf("2026-09", "2026-10"), declaration.periods)
        assertEquals(WireDeclarationStatus.Confirmed, declaration.declarationStatus)
        assertEquals(1600.0, declaration.declaredAmount)
        assertEquals("EUR", declaration.currencyCode)
        assertEquals("Rent Sept-Oct", declaration.reference)
        assertNull(declaration.rejectionNote)
        assertEquals(Instant.parse("2026-10-07T09:00:00Z"), declaration.confirmedAt)
        assertEquals(Instant.parse("2026-10-06T12:00:00Z"), declaration.createdAt)
    }
}

val RENT_JSON = """
{
  "leaseId": "l1",
  "currencyCode": "EUR",
  "monthlyRent": 800,
  "paymentDayOfMonth": 5,
  "months": [
    { "period": "2026-10", "dueDate": "2026-10-05T00:00:00.000Z", "expectedAmount": 800, "status": "due",
      "paidAt": null, "confirmedByWire": false, "declaration": null, "canDeclare": true },
    { "period": "2026-09", "dueDate": "2026-09-05T00:00:00.000Z", "expectedAmount": 800, "status": "declared",
      "paidAt": null, "confirmedByWire": false,
      "declaration": { "id": "d2", "status": "pending", "declaredAmount": 800, "transferDate": "2026-09-03T00:00:00.000Z",
        "declaredAt": "2026-09-04T08:30:00.000Z", "rejectionNote": null, "confirmedAt": null },
      "canDeclare": false },
    { "period": "2026-08", "dueDate": "2026-08-05T00:00:00.000Z", "expectedAmount": 800, "status": "paid",
      "paidAt": "2026-08-04T00:00:00.000Z", "confirmedByWire": true,
      "declaration": { "id": "d1", "status": "confirmed", "declaredAmount": 800, "transferDate": "2026-08-04T00:00:00.000Z",
        "declaredAt": "2026-08-04T15:00:00.000Z", "rejectionNote": null, "confirmedAt": "2026-08-05T10:00:00.000Z" },
      "canDeclare": false, "futureField": 1 }
  ]
}
""".trimIndent()

val DECLARATION_JSON = """
{ "id": "d1", "leaseId": "l1", "periods": ["2026-09", "2026-10"], "status": "confirmed", "declaredAmount": 1600,
  "currencyCode": "EUR", "transferDate": "2026-10-05T00:00:00.000Z", "reference": "Rent Sept-Oct", "rejectionNote": null,
  "confirmedAt": "2026-10-07T09:00:00.000Z", "createdAt": "2026-10-06T12:00:00.000Z" }
""".trimIndent()
