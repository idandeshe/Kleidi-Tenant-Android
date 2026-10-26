// The signed-in tenant's endpoints: /api/tenant/me and /api/tenant/leases (docs/tenant-app.md).

package one.lab5.kleidi.tenant.api.endpoints

import one.lab5.kleidi.tenant.api.ApiClient
import one.lab5.kleidi.tenant.api.ApiRequest
import one.lab5.kleidi.tenant.api.HttpMethod
import one.lab5.kleidi.tenant.api.KleidiJson
import one.lab5.kleidi.tenant.api.models.Tenant
import one.lab5.kleidi.tenant.api.models.TenantLease
import one.lab5.kleidi.tenant.api.models.TenantLeasesResponse
import one.lab5.kleidi.tenant.api.models.UpdateMeRequest
import one.lab5.kleidi.tenant.core.AppLanguage

/** `GET /tenant/me` — the tenant's own profile. */
suspend fun ApiClient.me(): Tenant = send(ApiRequest(HttpMethod.GET, "/tenant/me"), Tenant.serializer())

/** `PATCH /tenant/me { language }` — the language of the app copy and notifications for this tenant. */
suspend fun ApiClient.updateLanguage(language: AppLanguage): Tenant {
    val body = KleidiJson.encodeToString(UpdateMeRequest.serializer(), UpdateMeRequest(language.code)).encodeToByteArray()
    return send(ApiRequest(HttpMethod.PATCH, "/tenant/me", body = body), Tenant.serializer())
}

/** `GET /tenant/leases` — the tenant's own leases, newest first. */
suspend fun ApiClient.leases(): List<TenantLease> =
    send(ApiRequest(HttpMethod.GET, "/tenant/leases"), TenantLeasesResponse.serializer()).data

/** `GET /tenant/leases/:id` — one own lease (anyone else's is a plain 404). */
suspend fun ApiClient.lease(id: String): TenantLease =
    send(ApiRequest(HttpMethod.GET, "/tenant/leases/$id"), TenantLease.serializer())
