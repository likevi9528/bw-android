package com.x8bit.bitwarden.data.auth.manager

import com.x8bit.bitwarden.data.auth.datasource.disk.AuthDiskSource
import com.x8bit.bitwarden.data.auth.datasource.disk.model.AccountJson
import com.x8bit.bitwarden.data.auth.datasource.disk.model.UserStateJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.KdfTypeJson
import com.x8bit.bitwarden.data.platform.base.FakeDispatcherManager
import com.x8bit.bitwarden.data.platform.datasource.disk.PushDiskSource
import com.x8bit.bitwarden.data.platform.datasource.disk.SettingsDiskSource
import com.x8bit.bitwarden.data.platform.repository.model.VaultTimeoutAction
import com.x8bit.bitwarden.data.tools.generator.datasource.disk.GeneratorDiskSource
import com.x8bit.bitwarden.data.tools.generator.datasource.disk.PasswordHistoryDiskSource
import com.x8bit.bitwarden.data.vault.datasource.disk.VaultDiskSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test

class UserLogoutManagerTest {
    private val authDiskSource: AuthDiskSource = mockk {
        every { userState = any() } just runs
        every { clearData(any()) } just runs
    }
    private val generatorDiskSource: GeneratorDiskSource = mockk {
        every { clearData(any()) } just runs
    }
    private val settingsDiskSource: SettingsDiskSource = mockk {
        every { clearData(any()) } just runs
        every { storeVaultTimeoutInMinutes(any(), any()) } just runs
        every { storeVaultTimeoutAction(any(), any()) } just runs
    }
    private val pushDiskSource: PushDiskSource = mockk {
        coEvery { clearData(any()) } just runs
    }
    private val passwordHistoryDiskSource: PasswordHistoryDiskSource = mockk {
        coEvery { clearPasswordHistories(any()) } just runs
    }
    private val vaultDiskSource: VaultDiskSource = mockk {
        coEvery { deleteVaultData(any()) } just runs
    }

    private val userLogoutManager: UserLogoutManager =
        UserLogoutManagerImpl(
            authDiskSource = authDiskSource,
            generatorDiskSource = generatorDiskSource,
            passwordHistoryDiskSource = passwordHistoryDiskSource,
            pushDiskSource = pushDiskSource,
            settingsDiskSource = settingsDiskSource,
            vaultDiskSource = vaultDiskSource,
            dispatcherManager = FakeDispatcherManager(),
        )

    @Suppress("MaxLineLength")
    @Test
    fun `logout for single account should clear data associated with the given user and null out the user state`() {
        val userId = USER_ID_1
        every { authDiskSource.userState } returns SINGLE_USER_STATE_1

        userLogoutManager.logout(userId = USER_ID_1)

        verify { authDiskSource.userState = null }
        assertDataCleared(userId = userId)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `logout for multiple accounts should clear data associated with the given user and change to the new active user`() {
        val userId = USER_ID_1
        every { authDiskSource.userState } returns MULTI_USER_STATE

        userLogoutManager.logout(userId = USER_ID_1)

        verify { authDiskSource.userState = SINGLE_USER_STATE_2 }
        assertDataCleared(userId = userId)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `logout for non-active accounts should clear data associated with the given user and leave the active user unchanged`() {
        val userId = USER_ID_2
        every { authDiskSource.userState } returns MULTI_USER_STATE

        userLogoutManager.logout(userId = USER_ID_2)

        verify { authDiskSource.userState = SINGLE_USER_STATE_1 }
        assertDataCleared(userId = userId)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `softLogout should clear most data associated with the given user and remove token data from the account in the user state`() {
        val userId = USER_ID_1
        val vaultTimeoutInMinutes = 360
        val vaultTimeoutAction = VaultTimeoutAction.LOCK
        every { authDiskSource.userState } returns SINGLE_USER_STATE_1
        every {
            settingsDiskSource.getVaultTimeoutInMinutes(userId = userId)
        } returns vaultTimeoutInMinutes
        every {
            settingsDiskSource.getVaultTimeoutAction(userId = userId)
        } returns vaultTimeoutAction

        userLogoutManager.softLogout(userId = userId)

        val updatedAccount = ACCOUNT_1
            .copy(
                tokens = AccountJson.Tokens(
                    accessToken = null,
                    refreshToken = null,
                ),
            )
        val updatedUserState = SINGLE_USER_STATE_1
            .copy(
                accounts = SINGLE_USER_STATE_1
                    .accounts
                    .toMutableMap().apply {
                        set(userId, updatedAccount)
                    },
            )
        verify { authDiskSource.userState = updatedUserState }
        assertDataCleared(userId = userId)

        verify {
            settingsDiskSource.storeVaultTimeoutInMinutes(
                userId = userId,
                vaultTimeoutInMinutes = vaultTimeoutInMinutes,
            )
        }
        verify {
            settingsDiskSource.storeVaultTimeoutAction(
                userId = userId,
                vaultTimeoutAction = vaultTimeoutAction,
            )
        }
    }

    private fun assertDataCleared(userId: String) {
        verify { authDiskSource.clearData(userId = userId) }
        verify { generatorDiskSource.clearData(userId = userId) }
        verify { pushDiskSource.clearData(userId = userId) }
        verify { settingsDiskSource.clearData(userId = userId) }
        coVerify { passwordHistoryDiskSource.clearPasswordHistories(userId = userId) }
        coVerify {
            vaultDiskSource.deleteVaultData(userId = userId)
        }
    }
}

private const val EMAIL_2 = "test2@bitwarden.com"
private const val ACCESS_TOKEN = "accessToken"
private const val ACCESS_TOKEN_2 = "accessToken2"
private const val REFRESH_TOKEN = "refreshToken"
private const val USER_ID_1 = "2a135b23-e1fb-42c9-bec3-573857bc8181"
private const val USER_ID_2 = "b9d32ec0-6497-4582-9798-b350f53bfa02"
private val ACCOUNT_1 = AccountJson(
    profile = AccountJson.Profile(
        userId = USER_ID_1,
        email = "test@bitwarden.com",
        isEmailVerified = true,
        name = "Bitwarden Tester",
        hasPremium = false,
        stamp = null,
        organizationId = null,
        avatarColorHex = null,
        forcePasswordResetReason = null,
        kdfType = KdfTypeJson.ARGON2_ID,
        kdfIterations = 600000,
        kdfMemory = 16,
        kdfParallelism = 4,
        userDecryptionOptions = null,
    ),
    tokens = AccountJson.Tokens(
        accessToken = ACCESS_TOKEN,
        refreshToken = REFRESH_TOKEN,
    ),
    settings = AccountJson.Settings(
        environmentUrlData = null,
    ),
)
private val ACCOUNT_2 = AccountJson(
    profile = AccountJson.Profile(
        userId = USER_ID_2,
        email = EMAIL_2,
        isEmailVerified = true,
        name = "Bitwarden Tester 2",
        hasPremium = false,
        stamp = null,
        organizationId = null,
        avatarColorHex = null,
        forcePasswordResetReason = null,
        kdfType = KdfTypeJson.PBKDF2_SHA256,
        kdfIterations = 400000,
        kdfMemory = null,
        kdfParallelism = null,
        userDecryptionOptions = null,
    ),
    tokens = AccountJson.Tokens(
        accessToken = ACCESS_TOKEN_2,
        refreshToken = "refreshToken",
    ),
    settings = AccountJson.Settings(
        environmentUrlData = null,
    ),
)
private val SINGLE_USER_STATE_1 = UserStateJson(
    activeUserId = USER_ID_1,
    accounts = mapOf(
        USER_ID_1 to ACCOUNT_1,
    ),
)
private val SINGLE_USER_STATE_2 = UserStateJson(
    activeUserId = USER_ID_2,
    accounts = mapOf(
        USER_ID_2 to ACCOUNT_2,
    ),
)
private val MULTI_USER_STATE = UserStateJson(
    activeUserId = USER_ID_1,
    accounts = mapOf(
        USER_ID_1 to ACCOUNT_1,
        USER_ID_2 to ACCOUNT_2,
    ),
)