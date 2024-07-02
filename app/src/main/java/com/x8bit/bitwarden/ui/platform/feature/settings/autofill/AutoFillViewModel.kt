package com.x8bit.bitwarden.ui.platform.feature.settings.autofill

import android.os.Build
import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.x8bit.bitwarden.data.platform.repository.SettingsRepository
import com.x8bit.bitwarden.data.platform.repository.model.UriMatchType
import com.x8bit.bitwarden.data.platform.util.isBuildVersionBelow
import com.x8bit.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.ui.platform.base.util.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

private const val KEY_STATE = "state"

/**
 * View model for the auto-fill screen.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class AutoFillViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
) : BaseViewModel<AutoFillState, AutoFillEvent, AutoFillAction>(
    initialState = savedStateHandle[KEY_STATE]
        ?: AutoFillState(
            isAskToAddLoginEnabled = !settingsRepository.isAutofillSavePromptDisabled,
            isAutoFillServicesEnabled = settingsRepository.isAutofillEnabledStateFlow.value,
            isCopyTotpAutomaticallyEnabled = !settingsRepository.isAutoCopyTotpDisabled,
            isUseInlineAutoFillEnabled = settingsRepository.isInlineAutofillEnabled,
            showPasskeyManagementRow = isBuildVersionBelow(
                version = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            ).not(),
            defaultUriMatchType = settingsRepository.defaultUriMatchType,
        ),
) {

    init {
        stateFlow
            .onEach { savedStateHandle[KEY_STATE] = it }
            .launchIn(viewModelScope)

        settingsRepository
            .isAutofillEnabledStateFlow
            .map {
                AutoFillAction.Internal.AutofillEnabledUpdateReceive(isAutofillEnabled = it)
            }
            .onEach(::sendAction)
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: AutoFillAction): Unit = when (action) {
        is AutoFillAction.AskToAddLoginClick -> handleAskToAddLoginClick(action)
        is AutoFillAction.AutoFillServicesClick -> handleAutoFillServicesClick(action)
        AutoFillAction.BackClick -> handleBackClick()
        is AutoFillAction.CopyTotpAutomaticallyClick -> handleCopyTotpAutomaticallyClick(action)
        is AutoFillAction.DefaultUriMatchTypeSelect -> handleDefaultUriMatchTypeSelect(action)
        AutoFillAction.BlockAutoFillClick -> handleBlockAutoFillClick()
        is AutoFillAction.UseInlineAutofillClick -> handleUseInlineAutofillClick(action)
        AutoFillAction.PasskeyManagementClick -> handlePasskeyManagementClick()
        is AutoFillAction.Internal.AutofillEnabledUpdateReceive -> {
            handleAutofillEnabledUpdateReceive(action)
        }
    }

    private fun handleAskToAddLoginClick(action: AutoFillAction.AskToAddLoginClick) {
        settingsRepository.isAutofillSavePromptDisabled = !action.isEnabled
        mutableStateFlow.update { it.copy(isAskToAddLoginEnabled = action.isEnabled) }
    }

    private fun handleAutoFillServicesClick(action: AutoFillAction.AutoFillServicesClick) {
        if (action.isEnabled) {
            sendEvent(AutoFillEvent.NavigateToAutofillSettings)
        } else {
            settingsRepository.disableAutofill()
        }
    }

    private fun handleBackClick() {
        sendEvent(AutoFillEvent.NavigateBack)
    }

    private fun handleCopyTotpAutomaticallyClick(
        action: AutoFillAction.CopyTotpAutomaticallyClick,
    ) {
        settingsRepository.isAutoCopyTotpDisabled = !action.isEnabled
        mutableStateFlow.update { it.copy(isCopyTotpAutomaticallyEnabled = action.isEnabled) }
    }

    private fun handleUseInlineAutofillClick(action: AutoFillAction.UseInlineAutofillClick) {
        settingsRepository.isInlineAutofillEnabled = action.isEnabled
        mutableStateFlow.update { it.copy(isUseInlineAutoFillEnabled = action.isEnabled) }
    }

    private fun handlePasskeyManagementClick() {
        sendEvent(AutoFillEvent.NavigateToSettings)
    }

    private fun handleDefaultUriMatchTypeSelect(action: AutoFillAction.DefaultUriMatchTypeSelect) {
        settingsRepository.defaultUriMatchType = action.defaultUriMatchType
        mutableStateFlow.update {
            it.copy(defaultUriMatchType = action.defaultUriMatchType)
        }
    }

    private fun handleAutofillEnabledUpdateReceive(
        action: AutoFillAction.Internal.AutofillEnabledUpdateReceive,
    ) {
        mutableStateFlow.update {
            it.copy(isAutoFillServicesEnabled = action.isAutofillEnabled)
        }
    }

    private fun handleBlockAutoFillClick() {
        sendEvent(AutoFillEvent.NavigateToBlockAutoFill)
    }
}

/**
 * Models state for the Auto-fill screen.
 */
@Parcelize
data class AutoFillState(
    val isAskToAddLoginEnabled: Boolean,
    val isAutoFillServicesEnabled: Boolean,
    val isCopyTotpAutomaticallyEnabled: Boolean,
    val isUseInlineAutoFillEnabled: Boolean,
    val showPasskeyManagementRow: Boolean,
    val defaultUriMatchType: UriMatchType,
) : Parcelable {

    /**
     * Whether or not the toggle controlling the [isUseInlineAutoFillEnabled] value can be
     * interacted with.
     */
    val canInteractWithInlineAutofillToggle: Boolean
        get() = isAutoFillServicesEnabled
}

/**
 * Models events for the auto-fill screen.
 */
sealed class AutoFillEvent {
    /**
     * Navigate back.
     */
    data object NavigateBack : AutoFillEvent()

    /**
     * Navigates to the system autofill settings selection screen.
     */
    data object NavigateToAutofillSettings : AutoFillEvent()

    /**
     * Navigate to block auto fill screen.
     */
    data object NavigateToBlockAutoFill : AutoFillEvent()

    /**
     * Navigate to device settings.
     */
    data object NavigateToSettings : AutoFillEvent()

    /**
     * Displays a toast with the given [Text].
     */
    data class ShowToast(
        val text: Text,
    ) : AutoFillEvent()
}

/**
 * Models actions for the auto-fill screen.
 */
sealed class AutoFillAction {
    /**
     * User clicked ask to add login button.
     */
    data class AskToAddLoginClick(
        val isEnabled: Boolean,
    ) : AutoFillAction()

    /**
     * User clicked auto-fill services button.
     */
    data class AutoFillServicesClick(
        val isEnabled: Boolean,
    ) : AutoFillAction()

    /**
     * User clicked back button.
     */
    data object BackClick : AutoFillAction()

    /**
     * User clicked copy TOTP automatically button.
     */
    data class CopyTotpAutomaticallyClick(
        val isEnabled: Boolean,
    ) : AutoFillAction()

    /**
     * User selected a [UriMatchType].
     */
    data class DefaultUriMatchTypeSelect(
        val defaultUriMatchType: UriMatchType,
    ) : AutoFillAction()

    /**
     * User clicked block auto fill button.
     */
    data object BlockAutoFillClick : AutoFillAction()

    /**
     * User clicked use inline autofill button.
     */
    data class UseInlineAutofillClick(
        val isEnabled: Boolean,
    ) : AutoFillAction()

    /**
     * User clicked passkey management button.
     */
    data object PasskeyManagementClick : AutoFillAction()

    /**
     * Internal actions.
     */
    sealed class Internal : AutoFillAction() {

        /**
         * An update for changes in the [isAutofillEnabled] value.
         */
        data class AutofillEnabledUpdateReceive(
            val isAutofillEnabled: Boolean,
        ) : Internal()
    }
}
