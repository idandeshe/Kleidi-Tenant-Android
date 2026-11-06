package one.lab5.kleidi.tenant.api

import one.lab5.kleidi.tenant.api.models.TenantLease
import one.lab5.kleidi.tenant.core.LeaseStatus
import one.lab5.kleidi.tenant.core.PaymentMethod
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LeaseDecodingTest {
    @Test
    fun theRentTimelineIsOptionalAndFindsTodaysRow() {
        val base = """{"id":"l","property":{"id":"p"},"status":"active","startDate":"2026-03-15T00:00:00.000Z","monthlyRent":1000"""
        val old = KleidiJson.decodeFromString(TenantLease.serializer(), "$base}")
        assertNull(old.rentTimeline)
        assertEquals(-1, old.currentRentIndex())

        val lease = KleidiJson.decodeFromString(
            TenantLease.serializer(),
            """$base,"rentTimeline":[{"effectiveFrom":"2026-03-15T00:00:00.000Z","monthlyRent":1000},
                {"effectiveFrom":"2027-03-15T00:00:00.000Z","monthlyRent":1050}]}""",
        )
        assertEquals(0, lease.currentRentIndex(Instant.parse("2027-02-28T10:00:00Z")))
        // The March row carries the new rent from 1 March.
        assertEquals(1, lease.currentRentIndex(Instant.parse("2027-03-01T10:00:00Z")))
    }

    @Test
    fun decodesTheTenantLeaseShapeAndIgnoresUnknownKeys() {
        val lease = KleidiJson.decodeFromString(TenantLease.serializer(), LEASE_JSON)

        assertEquals(LeaseStatus.Active, lease.leaseStatus)
        assertEquals(800.0, lease.monthlyRent)
        assertEquals(850.0, lease.upcomingRent?.monthlyRent)
        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), lease.startDate)
        assertEquals(PaymentMethod.BankTransfer, lease.payment)
        assertEquals(5, lease.paymentDayOfMonth)
        assertEquals("Ermou 12", lease.property.streetLine)
        assertEquals("10563 Athens", lease.property.cityLine)
        assertEquals(3, lease.property.floorLevel?.number)
    }

    @Test
    fun nullableFieldsMayBeNull() {
        val json = """{"id":"l2","property":{"id":"p2","street":null,"unitNumber":null,"floor":null,"postalCode":null,"cityName":null},
            "status":"upcoming","startDate":"2027-01-01T00:00:00.000Z","endDate":null,"isPastEnd":false,"monthlyRent":650,
            "upcomingRent":null,"currencyCode":null,"depositAmount":null,"paymentDayOfMonth":null,"paymentMethod":null,
            "noticePeriodMonths":null,"noticeDeadline":null}"""
        val lease = KleidiJson.decodeFromString(TenantLease.serializer(), json)

        assertEquals(LeaseStatus.Upcoming, lease.leaseStatus)
        assertNull(lease.endDate)
        assertNull(lease.payment)
        assertNull(lease.property.streetLine)
        assertNull(lease.property.cityLine)
        assertNull(lease.property.floorLevel)
        // A status this build does not know still decodes.
        assertEquals("paused", KleidiJson.decodeFromString(TenantLease.serializer(), json.replace("\"upcoming\"", "\"paused\"")).status)
    }
}
