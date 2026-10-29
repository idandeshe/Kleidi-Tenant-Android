// The multipart declaration (part order, headers), the rent / history calls and the proof download.

package one.lab5.kleidi.tenant.api

import kotlinx.coroutines.test.runTest
import one.lab5.kleidi.tenant.api.endpoints.DeclareWireInput
import one.lab5.kleidi.tenant.api.endpoints.ProofUpload
import one.lab5.kleidi.tenant.api.endpoints.buildDeclareWireForm
import one.lab5.kleidi.tenant.api.endpoints.declareWire
import one.lab5.kleidi.tenant.api.endpoints.rent
import one.lab5.kleidi.tenant.api.endpoints.wireDeclarations
import one.lab5.kleidi.tenant.api.endpoints.wireProof
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RentEndpointsTest {
    private val input = DeclareWireInput(periods = listOf("2026-09", "2026-10"), declaredAmount = 1600.0, transferDate = "2026-10-05", reference = " Rent Sept-Oct ")
    private val proof = ProofUpload(byteArrayOf(1, 2, 3), "receipt.pdf", "application/pdf")

    @Test
    fun textPartsPrecedeTheProofFile() {
        val form = buildDeclareWireForm(input, proof, MultipartForm("b"))
        val body = form.body.decodeToString(throwOnInvalidSequence = false)

        assertEquals("multipart/form-data; boundary=b", form.contentType)
        val names = Regex("name=\"([a-zA-Z]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(listOf("periods", "declaredAmount", "transferDate", "reference", "proof"), names)
        assertTrue(body.contains("name=\"periods\"\r\n\r\n[\"2026-09\",\"2026-10\"]\r\n"))
        assertTrue(body.contains("name=\"declaredAmount\"\r\n\r\n1600\r\n"))
        assertTrue(body.contains("name=\"transferDate\"\r\n\r\n2026-10-05\r\n"))
        assertTrue(body.contains("name=\"reference\"\r\n\r\nRent Sept-Oct\r\n"))
        assertTrue(body.contains("name=\"proof\"; filename=\"receipt.pdf\"\r\nContent-Type: application/pdf\r\n\r\n\u0001\u0002\u0003\r\n--b--\r\n"))
    }

    @Test
    fun aBlankReferenceIsLeftOut() {
        val body = buildDeclareWireForm(DeclareWireInput(listOf("2026-10"), 812.5, "2026-10-05", reference = "  "), proof, MultipartForm("b")).body.decodeToString()
        assertTrue("reference" !in body)
        assertTrue(body.contains("name=\"declaredAmount\"\r\n\r\n812.5\r\n"))
    }

    @Test
    fun declareWirePostsMultipartWithBearer() = runTest {
        val transport = ScriptedTransport { 201 to DECLARATION_JSON }
        val client = signedInClient(transport)

        val created = client.declareWire("l1", input, proof)

        assertEquals("d1", created.id)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/tenant/leases/l1/wire-declarations", request.path)
        assertEquals("Bearer access-a", request.header("Authorization"))
        assertTrue(request.header("Content-Type")!!.startsWith("multipart/form-data; boundary=kleidi-"))
        assertEquals("tenant-android", request.header("x-client"))
    }

    @Test
    fun declareWireConflictsSurfaceAsHttpErrors() = runTest {
        val transport = ScriptedTransport { 409 to """{"error":"2026-10 is already recorded as paid"}""" }
        val error = assertFailsWith<ApiError.Http> { signedInClient(transport).declareWire("l1", input, proof) }
        assertEquals(409, error.status)
        assertNull(error.field)
    }

    @Test
    fun rentAndHistoryAreRead() = runTest {
        val transport = ScriptedTransport { request ->
            when (request.path) {
                "/api/tenant/leases/l1/rent" -> 200 to RENT_JSON
                "/api/tenant/leases/l1/wire-declarations" -> 200 to """{"data":[$DECLARATION_JSON]}"""
                else -> 404 to """{"error":"Not found"}"""
            }
        }
        val client = signedInClient(transport)
        assertEquals(3, client.rent("l1").months.size)
        assertEquals(listOf("d1"), client.wireDeclarations("l1").map { it.id })
    }

    @Test
    fun theProofComesBackWithItsContentType() = runTest {
        val transport = object : HttpTransport {
            override suspend fun send(request: HttpRequest): HttpResponse {
                assertEquals("/api/tenant/wire-declarations/d1/proof", request.url.removePrefix(BASE_URL))
                assertEquals("Bearer access-a", request.headers["Authorization"])
                return HttpResponse(200, byteArrayOf(0x25, 0x50, 0x44, 0x46), mapOf("content-type" to "application/pdf"))
            }
        }
        val proof = signedInClient(transport).wireProof("d1")
        assertContentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46), proof.bytes)
        assertEquals("application/pdf", proof.mimeType)
        assertTrue(proof.isPdf)
    }
}
