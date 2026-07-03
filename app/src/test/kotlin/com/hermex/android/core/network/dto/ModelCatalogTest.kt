package com.hermex.android.core.network.dto

import com.hermex.android.core.network.HermexJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun `parses groups with models, keeping provider_id and falling back name to provider_id`() {
        val response = HermexJson.decodeFromString<ModelsResponse>(
            """{
                "groups":[
                    {"provider_id":"openai","name":"OpenAI","models":[{"id":"gpt-5.5","name":"GPT-5.5"}]},
                    {"provider_id":"anthropic","models":[{"id":"claude-sonnet-4-6"}]}
                ],
                "default_model":"gpt-5.5"
            }""",
        )
        val groups = ModelCatalogParser.parseGroups(response)
        assertEquals(2, groups.size)
        assertEquals("OpenAI", groups[0].name)
        assertEquals("gpt-5.5", groups[0].models.first().id)
        assertEquals("GPT-5.5", groups[0].models.first().displayName)
        // second group has no "name" -- falls back to provider_id
        assertEquals("anthropic", groups[1].name)
        // model with no "name" -- displayName falls back to its id
        assertEquals("claude-sonnet-4-6", groups[1].models.first().displayName)
    }

    @Test
    fun `a group with no models is dropped entirely`() {
        val response = HermexJson.decodeFromString<ModelsResponse>(
            """{"groups":[{"provider_id":"empty-provider","models":[]},{"provider_id":"real","models":[{"id":"m1"}]}]}""",
        )
        val groups = ModelCatalogParser.parseGroups(response)
        assertEquals(1, groups.size)
        assertEquals("real", groups.first().providerId)
    }

    @Test
    fun `a model option with a blank or missing id is skipped`() {
        val response = HermexJson.decodeFromString<ModelsResponse>(
            """{"groups":[{"provider_id":"p","models":[{"id":"  "},{"name":"no id"},{"id":"m1"}]}]}""",
        )
        val groups = ModelCatalogParser.parseGroups(response)
        assertEquals(1, groups.single().models.size)
        assertEquals("m1", groups.single().models.first().id)
    }

    @Test
    fun `displayName falls back from name to label to id`() {
        val response = HermexJson.decodeFromString<ModelsResponse>(
            """{"groups":[{"provider_id":"p","models":[
                {"id":"a","name":"A Name","label":"A Label"},
                {"id":"b","label":"B Label"},
                {"id":"c"}
            ]}]}""",
        )
        val models = ModelCatalogParser.parseGroups(response).single().models
        assertEquals("A Name", models[0].displayName)
        assertEquals("B Label", models[1].displayName)
        assertEquals("c", models[2].displayName)
    }

    @Test
    fun `a model option's provider_id overrides the parent group's provider_id`() {
        val response = HermexJson.decodeFromString<ModelsResponse>(
            """{"groups":[{"provider_id":"group-provider","models":[{"id":"m1","provider_id":"override"}]}]}""",
        )
        assertEquals("override", ModelCatalogParser.parseGroups(response).single().models.single().providerId)
    }

    @Test
    fun `a group with no provider_id falls back to a name-index id`() {
        val response = HermexJson.decodeFromString<ModelsResponse>(
            """{"groups":[{"name":"Custom","models":[{"id":"m1"}]}]}""",
        )
        assertEquals("Custom-0", ModelCatalogParser.parseGroups(response).single().id)
    }

    @Test
    fun `missing groups decodes to an empty list without crashing`() {
        assertTrue(ModelCatalogParser.parseGroups(HermexJson.decodeFromString<ModelsResponse>("""{}""")).isEmpty())
    }

    @Test
    fun `mergingLiveModels replaces only the matching provider's models`() {
        val response = HermexJson.decodeFromString<ModelsResponse>(
            """{"groups":[
                {"provider_id":"openai","models":[{"id":"stale-1"}]},
                {"provider_id":"anthropic","models":[{"id":"claude-1"}]}
            ]}""",
        )
        val groups = ModelCatalogParser.parseGroups(response)
        val live = HermexJson.decodeFromString<ModelsLiveResponse>(
            """{"provider":"openai","models":[{"id":"gpt-5.5"},{"id":"gpt-5.5-mini"}]}""",
        )
        val merged = groups.mergingLiveModels(live)
        assertEquals(listOf("gpt-5.5", "gpt-5.5-mini"), merged.first { it.providerId == "openai" }.models.map { it.id })
        // untouched -- anthropic's group isn't the live response's provider
        assertEquals(listOf("claude-1"), merged.first { it.providerId == "anthropic" }.models.map { it.id })
    }

    @Test
    fun `mergingLiveModels leaves groups unchanged when the provider matches nothing or the live list is empty`() {
        val response = HermexJson.decodeFromString<ModelsResponse>(
            """{"groups":[{"provider_id":"openai","models":[{"id":"m1"}]}]}""",
        )
        val groups = ModelCatalogParser.parseGroups(response)

        val noMatch = groups.mergingLiveModels(
            HermexJson.decodeFromString<ModelsLiveResponse>("""{"provider":"unknown","models":[{"id":"x"}]}"""),
        )
        assertEquals(groups, noMatch)

        val emptyLive = groups.mergingLiveModels(
            HermexJson.decodeFromString<ModelsLiveResponse>("""{"provider":"openai","models":[]}"""),
        )
        assertEquals(groups, emptyLive)
    }

    @Test
    fun `mergingLiveModels with no provider returns the groups unchanged`() {
        val response = HermexJson.decodeFromString<ModelsResponse>(
            """{"groups":[{"provider_id":"openai","models":[{"id":"m1"}]}]}""",
        )
        val groups = ModelCatalogParser.parseGroups(response)
        assertNull(HermexJson.decodeFromString<ModelsLiveResponse>("""{}""").provider)
        assertEquals(groups, groups.mergingLiveModels(HermexJson.decodeFromString<ModelsLiveResponse>("""{}""")))
    }
}
