package id.walt.webwallet.usecase.event

import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.events.*
import id.walt.webwallet.usecase.issuer.IssuerNameResolutionUseCase
import id.walt.webwallet.utils.JsonUtils
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID

class EventUseCase(
    private val eventService: EventService,
    private val issuerNameResolutionUseCase: IssuerNameResolutionUseCase,
) {

    fun log(
        action: EventType.Action,
        originator: String,
        tenant: String,
        accountId: UUID,
        walletId: UUID,
        data: EventData,
        credentialId: String? = null,
        note: String? = null
    ) = log(
        Event(
            action = action,
            tenant = tenant,
            originator = originator,
            account = accountId,
            wallet = walletId,
            data = data,
            credentialId = credentialId,
            note = note,
        )
    )

    fun log(vararg event: Event) = eventService.add(event.toList())

    fun count(walletId: UUID, dataFilter: Map<String, List<String>>) = eventService.count(walletId, dataFilter)

    fun get(parameter: EventFilterParameter) = eventService.get(
        accountId = parameter.accountId,
        walletId = parameter.walletId,
        limit = parameter.logFilter.limit,
        offset = parameter.offset,
        sortOrder = parameter.logFilter.sortOrder ?: "asc",
        sortBy = parameter.logFilter.sortBy ?: "",
        dataFilter = parameter.logFilter.data
    )

    fun delete(id: Int) = eventService.delete(id)

    fun credentialEventData(
        credential: WalletCredential,
        subject: CredentialEventDataActor.Subject? = null,
        organization: CredentialEventDataActor.Organization? = null,
        type: String? = null,
    ) = CredentialEventData(
        ecosystem = EventDataNotAvailable,
        type = credential.parsedDocument?.let {
            JsonUtils.tryGetData(it, "type")?.jsonArray?.last()?.jsonPrimitive?.content
        } ?: EventDataNotAvailable,
        format = type ?: EventDataNotAvailable,
        proofType = EventDataNotAvailable,
        protocol = "oid4vp",
        credentialId = credential.id,
        //TODO: these calls are made multiple times (e.g. see notifications in [SilentClaimStrategy]
        logo = WalletCredential.getManifestLogo(credential.parsedManifest),
        subject = subject,
        organization = organization,
        //end TODO
    )

    fun subjectData(credential: WalletCredential) = CredentialEventDataActor.Subject(
        subjectId = credential.parsedDocument?.let {
            JsonUtils.tryGetData(it, "credentialSubject.id")?.jsonPrimitive?.content
        } ?: EventDataNotAvailable,
        subjectKeyType = EventDataNotAvailable,
    )

    suspend fun issuerData(credential: WalletCredential) =
        WalletCredential.parseIssuerDid(credential.parsedDocument, credential.parsedManifest).let {
            CredentialEventDataActor.Organization.Issuer(
                did = it ?: EventDataNotAvailable,
                name = WalletCredential.getManifestIssuerName(credential.parsedManifest) ?: it?.let {
                    issuerNameResolutionUseCase.resolve(credential.wallet, it)
                } ?: EventDataNotAvailable,
                keyId = EventDataNotAvailable,
                keyType = EventDataNotAvailable,
            )
        }

    fun verifierData() = CredentialEventDataActor.Organization.Verifier(
        did = EventDataNotAvailable,
        name = EventDataNotAvailable,
        policies = emptyList(),
    )

    fun didEventData(did: String, document: DidDocument) = didEventData(did, document.toString())

    fun didEventData(did: String, document: String) = DidEventData(
        did = did, document = document
    )

    suspend fun keyEventData(key: Key, kmsType: String) = keyEventData(
        id = key.getKeyId(), algorithm = key.keyType.name, kmsType = kmsType
    )

    fun keyEventData(id: String, algorithm: String, kmsType: String) = KeyEventData(
        id = id, algorithm = algorithm, keyManagementService = kmsType
    )

    data class EventFilterParameter(
        val accountId: UUID,
        val walletId: UUID,
        val offset: Long,
        val logFilter: EventLogFilter,
    )
}