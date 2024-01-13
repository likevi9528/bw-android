package com.x8bit.bitwarden.data.autofill.builder

import android.view.autofill.AutofillId
import com.x8bit.bitwarden.data.autofill.model.AutofillPartition
import com.x8bit.bitwarden.data.autofill.model.AutofillRequest
import com.x8bit.bitwarden.data.autofill.model.AutofillView
import com.x8bit.bitwarden.data.autofill.model.FilledData
import com.x8bit.bitwarden.data.autofill.model.FilledItem
import com.x8bit.bitwarden.data.autofill.model.FilledPartition
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FilledDataBuilderTest {
    private lateinit var filledDataBuilder: FilledDataBuilder

    private val autofillId: AutofillId = mockk()
    private val autofillViewData = AutofillView.Data(
        autofillId = autofillId,
        idPackage = null,
        isFocused = false,
        webDomain = null,
        webScheme = null,
    )

    @BeforeEach
    fun setup() {
        filledDataBuilder = FilledDataBuilderImpl()
    }

    @Test
    fun `build should return filled data and ignored AutofillIds when Login`() = runTest {
        // Setup
        val autofillView = AutofillView.Login.Username(
            data = autofillViewData,
        )
        val autofillPartition = AutofillPartition.Login(
            views = listOf(autofillView),
        )
        val ignoreAutofillIds: List<AutofillId> = mockk()
        val autofillRequest = AutofillRequest.Fillable(
            ignoreAutofillIds = ignoreAutofillIds,
            partition = autofillPartition,
            uri = URI,
        )
        val filledItem = FilledItem(
            autofillId = autofillId,
        )
        val filledPartition = FilledPartition(
            filledItems = listOf(
                filledItem,
            ),
        )
        val expected = FilledData(
            filledPartitions = listOf(
                filledPartition,
            ),
            ignoreAutofillIds = ignoreAutofillIds,
        )

        // Test
        val actual = filledDataBuilder.build(
            autofillRequest = autofillRequest,
        )

        // Verify
        assertEquals(expected, actual)
    }

    @Test
    fun `build should return filled data and ignored AutofillIds when Card`() = runTest {
        // Setup
        val autofillView = AutofillView.Card.Number(
            data = autofillViewData,
        )
        val autofillPartition = AutofillPartition.Card(
            views = listOf(autofillView),
        )
        val ignoreAutofillIds: List<AutofillId> = mockk()
        val autofillRequest = AutofillRequest.Fillable(
            ignoreAutofillIds = ignoreAutofillIds,
            partition = autofillPartition,
            uri = URI,
        )
        val filledItem = FilledItem(
            autofillId = autofillId,
        )
        val filledPartition = FilledPartition(
            filledItems = listOf(
                filledItem,
            ),
        )
        val expected = FilledData(
            filledPartitions = listOf(
                filledPartition,
            ),
            ignoreAutofillIds = ignoreAutofillIds,
        )

        // Test
        val actual = filledDataBuilder.build(
            autofillRequest = autofillRequest,
        )

        // Verify
        assertEquals(expected, actual)
    }

    companion object {
        private const val URI: String = "androidapp://com.x8bit.bitwarden"
    }
}