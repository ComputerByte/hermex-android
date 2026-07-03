package com.hermex.android.core.network.dto

/** Parsed, display-ready view of a [ModelsResponse] group -- mirrors iOS's `ModelCatalogGroup`. */
data class ModelCatalogGroup(
    val id: String,
    val name: String,
    val providerId: String?,
    val models: List<ModelCatalogOption>,
    val extraModels: List<ModelCatalogOption> = emptyList(),
)

/** Mirrors iOS's `ModelCatalogOption`. */
data class ModelCatalogOption(
    val id: String,
    val displayName: String,
    val providerId: String?,
)

/** Parses the untyped-on-iOS `groups`/`models` shape into [ModelCatalogGroup]/[ModelCatalogOption],
 * matching iOS's `ModelCatalogParser` field-by-field: a group with no models is dropped entirely,
 * a model's `id` is required (skipped if blank), and `displayName` falls back name -> label -> id. */
object ModelCatalogParser {
    fun parseGroups(response: ModelsResponse): List<ModelCatalogGroup> {
        val groupValues = response.groups.orEmpty()
        return groupValues.mapIndexedNotNull { index, group ->
            val providerId = group.providerId?.normalizedOrNull()
            val name = group.name?.normalizedOrNull() ?: providerId ?: "Models"
            val models = parseModelOptions(group.models, providerId)
            val extraModels = parseModelOptions(group.extraModels, providerId)
            if (models.isEmpty()) return@mapIndexedNotNull null
            ModelCatalogGroup(
                id = providerId ?: "$name-$index",
                name = name,
                providerId = providerId,
                models = models,
                extraModels = extraModels,
            )
        }
    }

    fun parseModelOptions(items: List<ModelOptionDto>?, providerId: String?): List<ModelCatalogOption> {
        return items.orEmpty().mapNotNull { item ->
            val id = item.id?.normalizedOrNull() ?: return@mapNotNull null
            val displayName = item.name?.normalizedOrNull() ?: item.label?.normalizedOrNull() ?: id
            val optionProviderId = item.providerId?.normalizedOrNull() ?: providerId
            ModelCatalogOption(id = id, displayName = displayName, providerId = optionProviderId)
        }
    }
}

/** Replaces the matching provider group's models with the live list -- live is authoritative for
 * that provider, covering both additions and removals. Returns the list unchanged when the
 * provider matches no group or the live list is empty, so an odd live response can never blank
 * out the picker. Mirrors iOS's `Array<ModelCatalogGroup>.mergingLiveModels`. */
fun List<ModelCatalogGroup>.mergingLiveModels(response: ModelsLiveResponse): List<ModelCatalogGroup> {
    val provider = response.provider?.normalizedOrNull() ?: return this
    val liveModels = ModelCatalogParser.parseModelOptions(response.models, provider)
    if (liveModels.isEmpty()) return this
    return map { group -> if (group.providerId == provider) group.copy(models = liveModels) else group }
}

private fun String.normalizedOrNull(): String? = trim().takeIf { it.isNotEmpty() }
