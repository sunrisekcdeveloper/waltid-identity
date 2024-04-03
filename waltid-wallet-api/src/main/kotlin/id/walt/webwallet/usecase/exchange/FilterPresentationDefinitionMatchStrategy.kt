package id.walt.webwallet.usecase.exchange

import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class FilterPresentationDefinitionMatchStrategy : PresentationDefinitionMatchStrategy {
    override fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition
    ): List<WalletCredential> = match(credentials, getFilters(presentationDefinition))

    private fun match(
        credentialList: List<WalletCredential>, filters: List<List<TypeFilter>>
    ) = credentialList.filter { credential ->
        filters.any { fields ->
            fields.all { typeFilter ->
                val credField = credential.parsedDocument!![typeFilter.path] ?: return@all false

                when (credField) {
                    is JsonPrimitive -> credField.jsonPrimitive.content == typeFilter.pattern
                    is JsonArray -> credField.jsonArray.last().jsonPrimitive.content == typeFilter.pattern

                    else -> false
                }
            }
        }
    }

    private fun getFilters(presentationDefinition: PresentationDefinition): List<List<TypeFilter>> {
        val filters = presentationDefinition.inputDescriptors.mapNotNull { inputDescriptor ->
            inputDescriptor.constraints?.fields?.filter { field -> field.path.any { path -> path.contains("type") } }
                ?.map {
                    val path = it.path.first().removePrefix("$.")
                    val filterType = it.filter?.get("type")?.jsonPrimitive?.content
                    val filterPattern =
                        it.filter?.get("pattern")?.jsonPrimitive?.content ?: throw IllegalArgumentException(
                            "No filter pattern in presentation definition constraint"
                        )

                    TypeFilter(path, filterType, filterPattern)
                }?.plus(inputDescriptor.schema?.map { schema -> TypeFilter("type", "string", schema.uri) } ?: listOf())
        }
        return filters
    }

    data class TypeFilter(val path: String, val type: String? = null, val pattern: String)
}