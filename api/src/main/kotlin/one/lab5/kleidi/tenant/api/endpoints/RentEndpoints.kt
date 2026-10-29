// Rent months and bank-transfer declarations (tenant app phase 2): /api/tenant/leases/:id/rent,
// /api/tenant/leases/:id/wire-declarations, /api/tenant/wire-declarations/:id/proof.

package one.lab5.kleidi.tenant.api.endpoints

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import one.lab5.kleidi.tenant.api.ApiClient
import one.lab5.kleidi.tenant.api.ApiRequest
import one.lab5.kleidi.tenant.api.HttpMethod
import one.lab5.kleidi.tenant.api.KleidiJson
import one.lab5.kleidi.tenant.api.MultipartForm
import one.lab5.kleidi.tenant.api.models.TenantRent
import one.lab5.kleidi.tenant.api.models.TenantWireDeclaration
import one.lab5.kleidi.tenant.api.models.TenantWireDeclarationsResponse
import one.lab5.kleidi.tenant.core.WireDeclarationRules

/** The proof file as it goes up: already in a format the server accepts (photos re-encoded as JPEG by the app). */
class ProofUpload(val bytes: ByteArray, val fileName: String, val mimeType: String)

/** The text fields of `POST /tenant/leases/:id/wire-declarations`. */
class DeclareWireInput(
    /** `YYYY-MM` months the transfer covers. */
    val periods: List<String>,
    val declaredAmount: Double,
    /** `YYYY-MM-DD`. */
    val transferDate: String,
    val reference: String? = null,
)

/** The bytes of a proof, with the `Content-Type` the server sent (an image type or `application/pdf`). */
class ProofFile(val bytes: ByteArray, val mimeType: String) {
    val isPdf: Boolean get() = mimeType.substringBefore(';').trim().equals("application/pdf", ignoreCase = true)
}

/** `GET /tenant/leases/:id/rent` — the months of one own lease, newest first. */
suspend fun ApiClient.rent(leaseId: String): TenantRent =
    send(ApiRequest(HttpMethod.GET, "/tenant/leases/$leaseId/rent"), TenantRent.serializer())

/** `GET /tenant/leases/:id/wire-declarations` — the tenant's declarations on this lease, newest first. */
suspend fun ApiClient.wireDeclarations(leaseId: String): List<TenantWireDeclaration> =
    send(ApiRequest(HttpMethod.GET, "/tenant/leases/$leaseId/wire-declarations"), TenantWireDeclarationsResponse.serializer()).data

/**
 * `POST /tenant/leases/:id/wire-declarations` — "I paid by bank transfer". Multipart: the text
 * fields first (`periods` as a JSON array, `declaredAmount`, `transferDate`, `reference?`), then
 * the `proof` file. 201 → the declaration; 400 `{ error, field }`, 409 (month already paid /
 * declared), 413 (too large), 415 (type), 429 (5 per 10 minutes).
 */
suspend fun ApiClient.declareWire(leaseId: String, input: DeclareWireInput, proof: ProofUpload): TenantWireDeclaration {
    val form = buildDeclareWireForm(input, proof)
    return send(
        ApiRequest(HttpMethod.POST, "/tenant/leases/$leaseId/wire-declarations", body = form.body, contentType = form.contentType),
        TenantWireDeclaration.serializer(),
    )
}

/** The multipart body of [declareWire]; separate so its part order is unit-tested. */
fun buildDeclareWireForm(input: DeclareWireInput, proof: ProofUpload, form: MultipartForm = MultipartForm()): MultipartForm {
    form.addField(WireDeclarationRules.FIELD_PERIODS, KleidiJson.encodeToString(ListSerializer(String.serializer()), input.periods))
    form.addField(WireDeclarationRules.FIELD_DECLARED_AMOUNT, WireDeclarationRules.amountField(input.declaredAmount))
    form.addField(WireDeclarationRules.FIELD_TRANSFER_DATE, input.transferDate)
    input.reference?.trim()?.takeIf { it.isNotEmpty() }?.let { form.addField(WireDeclarationRules.FIELD_REFERENCE, it) }
    form.addFile(WireDeclarationRules.FIELD_PROOF, proof.fileName, proof.mimeType, proof.bytes)
    return form
}

/** `GET /tenant/wire-declarations/:id/proof` — the tenant's own proof file ("View proof"). */
suspend fun ApiClient.wireProof(declarationId: String): ProofFile {
    val response = sendData(ApiRequest(HttpMethod.GET, "/tenant/wire-declarations/$declarationId/proof"))
    val type = response.headers.entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value ?: "application/octet-stream"
    return ProofFile(response.body, type)
}
