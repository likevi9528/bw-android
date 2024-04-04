package com.x8bit.bitwarden.data.auth.repository

import app.cash.turbine.test
import com.bitwarden.core.AuthRequestMethod
import com.bitwarden.core.AuthRequestResponse
import com.bitwarden.core.InitUserCryptoMethod
import com.bitwarden.core.RegisterKeyResponse
import com.bitwarden.core.UpdatePasswordResponse
import com.bitwarden.crypto.HashPurpose
import com.bitwarden.crypto.Kdf
import com.bitwarden.crypto.RsaKeyPair
import com.x8bit.bitwarden.data.auth.datasource.disk.model.AccountJson
import com.x8bit.bitwarden.data.auth.datasource.disk.model.AccountTokensJson
import com.x8bit.bitwarden.data.auth.datasource.disk.model.EnvironmentUrlDataJson
import com.x8bit.bitwarden.data.auth.datasource.disk.model.ForcePasswordResetReason
import com.x8bit.bitwarden.data.auth.datasource.disk.model.UserStateJson
import com.x8bit.bitwarden.data.auth.datasource.disk.util.FakeAuthDiskSource
import com.x8bit.bitwarden.data.auth.datasource.network.model.GetTokenResponseJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.IdentityTokenAuthModel
import com.x8bit.bitwarden.data.auth.datasource.network.model.KdfTypeJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.OrganizationAutoEnrollStatusResponseJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.OrganizationDomainSsoDetailsResponseJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.OrganizationKeysResponseJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.PasswordHintResponseJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.PreLoginResponseJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.PrevalidateSsoResponseJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.RefreshTokenResponseJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.RegisterRequestJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.RegisterResponseJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.ResendEmailRequestJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.ResetPasswordRequestJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.SetPasswordRequestJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.TwoFactorAuthMethod
import com.x8bit.bitwarden.data.auth.datasource.network.model.TwoFactorDataModel
import com.x8bit.bitwarden.data.auth.datasource.network.model.UserDecryptionOptionsJson
import com.x8bit.bitwarden.data.auth.datasource.network.service.AccountsService
import com.x8bit.bitwarden.data.auth.datasource.network.service.DevicesService
import com.x8bit.bitwarden.data.auth.datasource.network.service.HaveIBeenPwnedService
import com.x8bit.bitwarden.data.auth.datasource.network.service.IdentityService
import com.x8bit.bitwarden.data.auth.datasource.network.service.OrganizationService
import com.x8bit.bitwarden.data.auth.datasource.sdk.AuthSdkSource
import com.x8bit.bitwarden.data.auth.datasource.sdk.model.PasswordStrength.LEVEL_0
import com.x8bit.bitwarden.data.auth.datasource.sdk.model.PasswordStrength.LEVEL_1
import com.x8bit.bitwarden.data.auth.datasource.sdk.model.PasswordStrength.LEVEL_2
import com.x8bit.bitwarden.data.auth.datasource.sdk.model.PasswordStrength.LEVEL_3
import com.x8bit.bitwarden.data.auth.datasource.sdk.model.PasswordStrength.LEVEL_4
import com.x8bit.bitwarden.data.auth.manager.AuthRequestManager
import com.x8bit.bitwarden.data.auth.manager.UserLogoutManager
import com.x8bit.bitwarden.data.auth.repository.model.AuthState
import com.x8bit.bitwarden.data.auth.repository.model.BreachCountResult
import com.x8bit.bitwarden.data.auth.repository.model.DeleteAccountResult
import com.x8bit.bitwarden.data.auth.repository.model.KnownDeviceResult
import com.x8bit.bitwarden.data.auth.repository.model.LoginResult
import com.x8bit.bitwarden.data.auth.repository.model.OrganizationDomainSsoDetailsResult
import com.x8bit.bitwarden.data.auth.repository.model.PasswordHintResult
import com.x8bit.bitwarden.data.auth.repository.model.PasswordStrengthResult
import com.x8bit.bitwarden.data.auth.repository.model.PrevalidateSsoResult
import com.x8bit.bitwarden.data.auth.repository.model.RegisterResult
import com.x8bit.bitwarden.data.auth.repository.model.ResendEmailResult
import com.x8bit.bitwarden.data.auth.repository.model.ResetPasswordResult
import com.x8bit.bitwarden.data.auth.repository.model.SetPasswordResult
import com.x8bit.bitwarden.data.auth.repository.model.SwitchAccountResult
import com.x8bit.bitwarden.data.auth.repository.model.UserOrganizations
import com.x8bit.bitwarden.data.auth.repository.model.ValidatePasswordResult
import com.x8bit.bitwarden.data.auth.repository.model.VaultUnlockType
import com.x8bit.bitwarden.data.auth.repository.util.CaptchaCallbackTokenResult
import com.x8bit.bitwarden.data.auth.repository.util.DuoCallbackTokenResult
import com.x8bit.bitwarden.data.auth.repository.util.SsoCallbackResult
import com.x8bit.bitwarden.data.auth.repository.util.toOrganizations
import com.x8bit.bitwarden.data.auth.repository.util.toSdkParams
import com.x8bit.bitwarden.data.auth.repository.util.toUserState
import com.x8bit.bitwarden.data.auth.repository.util.toUserStateJson
import com.x8bit.bitwarden.data.auth.util.YubiKeyResult
import com.x8bit.bitwarden.data.auth.util.toSdkParams
import com.x8bit.bitwarden.data.platform.base.FakeDispatcherManager
import com.x8bit.bitwarden.data.platform.manager.PolicyManager
import com.x8bit.bitwarden.data.platform.manager.PushManager
import com.x8bit.bitwarden.data.platform.manager.dispatcher.DispatcherManager
import com.x8bit.bitwarden.data.platform.manager.model.NotificationLogoutData
import com.x8bit.bitwarden.data.platform.repository.SettingsRepository
import com.x8bit.bitwarden.data.platform.repository.model.Environment
import com.x8bit.bitwarden.data.platform.repository.util.FakeEnvironmentRepository
import com.x8bit.bitwarden.data.platform.repository.util.bufferedMutableSharedFlow
import com.x8bit.bitwarden.data.platform.util.asFailure
import com.x8bit.bitwarden.data.platform.util.asSuccess
import com.x8bit.bitwarden.data.vault.datasource.network.model.PolicyTypeJson
import com.x8bit.bitwarden.data.vault.datasource.network.model.SyncResponseJson
import com.x8bit.bitwarden.data.vault.datasource.network.model.createMockOrganization
import com.x8bit.bitwarden.data.vault.datasource.network.model.createMockPolicy
import com.x8bit.bitwarden.data.vault.datasource.sdk.VaultSdkSource
import com.x8bit.bitwarden.data.vault.repository.VaultRepository
import com.x8bit.bitwarden.data.vault.repository.model.VaultUnlockData
import com.x8bit.bitwarden.data.vault.repository.model.VaultUnlockResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

@Suppress("LargeClass")
class AuthRepositoryTest {

    private val dispatcherManager: DispatcherManager = FakeDispatcherManager()
    private val accountsService: AccountsService = mockk()
    private val devicesService: DevicesService = mockk()
    private val identityService: IdentityService = mockk()
    private val haveIBeenPwnedService: HaveIBeenPwnedService = mockk()
    private val organizationService: OrganizationService = mockk()
    private val mutableVaultUnlockDataStateFlow = MutableStateFlow(VAULT_UNLOCK_DATA)
    private val vaultRepository: VaultRepository = mockk {
        every { vaultUnlockDataStateFlow } returns mutableVaultUnlockDataStateFlow
        every { deleteVaultData(any()) } just runs
    }
    private val fakeAuthDiskSource = FakeAuthDiskSource()
    private val fakeEnvironmentRepository =
        FakeEnvironmentRepository()
            .apply {
                environment = Environment.Us
            }
    private val settingsRepository: SettingsRepository = mockk {
        every { setDefaultsIfNecessary(any()) } just runs
    }
    private val authSdkSource = mockk<AuthSdkSource> {
        coEvery {
            getNewAuthRequest(
                email = EMAIL,
            )
        } returns AUTH_REQUEST_RESPONSE.asSuccess()
        coEvery {
            hashPassword(
                email = EMAIL,
                password = PASSWORD,
                kdf = PRE_LOGIN_SUCCESS.kdfParams.toSdkParams(),
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
        } returns PASSWORD_HASH.asSuccess()
        coEvery {
            hashPassword(
                email = EMAIL,
                password = PASSWORD,
                kdf = ACCOUNT_1.profile.toSdkParams(),
                purpose = HashPurpose.LOCAL_AUTHORIZATION,
            )
        } returns PASSWORD_HASH.asSuccess()
        coEvery {
            makeRegisterKeys(
                email = EMAIL,
                password = PASSWORD,
                kdf = Kdf.Pbkdf2(DEFAULT_KDF_ITERATIONS.toUInt()),
            )
        } returns RegisterKeyResponse(
            masterPasswordHash = PASSWORD_HASH,
            encryptedUserKey = ENCRYPTED_USER_KEY,
            keys = RsaKeyPair(
                public = PUBLIC_KEY,
                private = PRIVATE_KEY,
            ),
        )
            .asSuccess()
    }
    private val vaultSdkSource = mockk<VaultSdkSource> {
        coEvery {
            getAuthRequestKey(
                publicKey = PUBLIC_KEY,
                userId = USER_ID_1,
            )
        } returns "AsymmetricEncString".asSuccess()
    }
    private val authRequestManager: AuthRequestManager = mockk()
    private val userLogoutManager: UserLogoutManager = mockk {
        every { logout(any(), any()) } just runs
    }

    private val mutableLogoutFlow = bufferedMutableSharedFlow<NotificationLogoutData>()
    private val mutableSyncOrgKeysFlow = bufferedMutableSharedFlow<Unit>()
    private val mutableActivePolicyFlow = bufferedMutableSharedFlow<List<SyncResponseJson.Policy>>()
    private val pushManager: PushManager = mockk {
        every { logoutFlow } returns mutableLogoutFlow
        every { syncOrgKeysFlow } returns mutableSyncOrgKeysFlow
    }
    private val policyManager: PolicyManager = mockk {
        every {
            getActivePoliciesFlow(type = PolicyTypeJson.MASTER_PASSWORD)
        } returns mutableActivePolicyFlow
    }

    private var elapsedRealtimeMillis = 123456789L

    private val repository = AuthRepositoryImpl(
        accountsService = accountsService,
        devicesService = devicesService,
        identityService = identityService,
        haveIBeenPwnedService = haveIBeenPwnedService,
        organizationService = organizationService,
        authSdkSource = authSdkSource,
        vaultSdkSource = vaultSdkSource,
        authDiskSource = fakeAuthDiskSource,
        environmentRepository = fakeEnvironmentRepository,
        settingsRepository = settingsRepository,
        vaultRepository = vaultRepository,
        authRequestManager = authRequestManager,
        userLogoutManager = userLogoutManager,
        dispatcherManager = dispatcherManager,
        pushManager = pushManager,
        policyManager = policyManager,
        elapsedRealtimeMillisProvider = { elapsedRealtimeMillis },
    )

    @BeforeEach
    fun beforeEach() {
        mockkStatic(
            GetTokenResponseJson.Success::toUserState,
            RefreshTokenResponseJson::toUserStateJson,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(
            GetTokenResponseJson.Success::toUserState,
            RefreshTokenResponseJson::toUserStateJson,
        )
    }

    @Test
    fun `authStateFlow should react to user state changes and account token changes`() = runTest {
        repository.authStateFlow.test {
            assertEquals(AuthState.Unauthenticated, awaitItem())

            // Store the tokens, nothing happens yet since there is technically no active user yet
            fakeAuthDiskSource.storeAccountTokens(
                userId = USER_ID_1,
                accountTokens = ACCOUNT_TOKENS_1,
            )
            expectNoEvents()
            // Update the active user, we are now authenticated
            fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
            assertEquals(AuthState.Authenticated(ACCESS_TOKEN), awaitItem())

            // Adding a tokens for the non-active user does not update the state
            fakeAuthDiskSource.storeAccountTokens(
                userId = USER_ID_2,
                accountTokens = ACCOUNT_TOKENS_2,
            )
            expectNoEvents()
            // Adding a non-active user does not update the state
            fakeAuthDiskSource.userState = MULTI_USER_STATE
            expectNoEvents()

            // Changing the active users tokens causes an update
            val newAccessToken = "new_access_token"
            fakeAuthDiskSource.storeAccountTokens(
                userId = USER_ID_1,
                accountTokens = ACCOUNT_TOKENS_1.copy(accessToken = newAccessToken),
            )
            assertEquals(AuthState.Authenticated(newAccessToken), awaitItem())

            // Change the active user causes an update
            fakeAuthDiskSource.userState = MULTI_USER_STATE.copy(activeUserId = USER_ID_2)
            assertEquals(AuthState.Authenticated(ACCESS_TOKEN_2), awaitItem())

            // Clearing the tokens of the active state results in the Unauthenticated state
            fakeAuthDiskSource.storeAccountTokens(
                userId = USER_ID_2,
                accountTokens = null,
            )
            assertEquals(AuthState.Unauthenticated, awaitItem())
        }
    }

    @Test
    fun `userStateFlow should update according to changes in its underlying data sources`() {
        fakeAuthDiskSource.userState = null
        assertEquals(
            null,
            repository.userStateFlow.value,
        )

        mutableVaultUnlockDataStateFlow.value = VAULT_UNLOCK_DATA
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        assertEquals(
            SINGLE_USER_STATE_1.toUserState(
                vaultState = VAULT_UNLOCK_DATA,
                userOrganizationsList = emptyList(),
                hasPendingAccountAddition = false,
                isBiometricsEnabledProvider = { false },
                vaultUnlockTypeProvider = { VaultUnlockType.MASTER_PASSWORD },
                isLoggedInProvider = { false },
                isDeviceTrustedProvider = { false },
            ),
            repository.userStateFlow.value,
        )

        fakeAuthDiskSource.apply {
            storePinProtectedUserKey(
                userId = USER_ID_1,
                pinProtectedUserKey = "pinProtectedUseKey",
            )
            storePinProtectedUserKey(
                userId = USER_ID_2,
                pinProtectedUserKey = "pinProtectedUseKey",
            )
            userState = MULTI_USER_STATE
        }
        assertEquals(
            MULTI_USER_STATE.toUserState(
                vaultState = VAULT_UNLOCK_DATA,
                userOrganizationsList = emptyList(),
                hasPendingAccountAddition = false,
                isBiometricsEnabledProvider = { false },
                vaultUnlockTypeProvider = { VaultUnlockType.PIN },
                isLoggedInProvider = { false },
                isDeviceTrustedProvider = { false },
            ),
            repository.userStateFlow.value,
        )

        val emptyVaultState = emptyList<VaultUnlockData>()
        mutableVaultUnlockDataStateFlow.value = emptyVaultState
        assertEquals(
            MULTI_USER_STATE.toUserState(
                vaultState = emptyVaultState,
                userOrganizationsList = emptyList(),
                hasPendingAccountAddition = false,
                isBiometricsEnabledProvider = { false },
                vaultUnlockTypeProvider = { VaultUnlockType.PIN },
                isLoggedInProvider = { false },
                isDeviceTrustedProvider = { false },
            ),
            repository.userStateFlow.value,
        )

        fakeAuthDiskSource.apply {
            storePinProtectedUserKey(
                userId = USER_ID_1,
                pinProtectedUserKey = null,
            )
            storePinProtectedUserKey(
                userId = USER_ID_2,
                pinProtectedUserKey = null,
            )
            storeOrganizations(
                userId = USER_ID_1,
                organizations = ORGANIZATIONS,
            )
        }
        assertEquals(
            MULTI_USER_STATE.toUserState(
                vaultState = emptyVaultState,
                userOrganizationsList = USER_ORGANIZATIONS,
                hasPendingAccountAddition = false,
                isBiometricsEnabledProvider = { false },
                vaultUnlockTypeProvider = { VaultUnlockType.MASTER_PASSWORD },
                isLoggedInProvider = { false },
                isDeviceTrustedProvider = { false },
            ),
            repository.userStateFlow.value,
        )
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    @Suppress("MaxLineLength")
    fun `loading the policies should emit masterPasswordPolicyFlow if the password fails any checks`() =
        runTest {
            val successResponse = GET_TOKEN_RESPONSE_SUCCESS
            coEvery {
                accountsService.preLogin(email = EMAIL)
            } returns PRE_LOGIN_SUCCESS.asSuccess()
            coEvery {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.MasterPassword(
                        username = EMAIL,
                        password = PASSWORD_HASH,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            } returns successResponse.asSuccess()
            coEvery {
                vaultRepository.unlockVault(
                    userId = USER_ID_1,
                    email = EMAIL,
                    kdf = ACCOUNT_1.profile.toSdkParams(),
                    userKey = successResponse.key!!,
                    privateKey = successResponse.privateKey!!,
                    organizationKeys = null,
                    masterPassword = PASSWORD,
                )
            } returns VaultUnlockResult.Success
            coEvery { vaultRepository.syncIfNecessary() } just runs
            every {
                GET_TOKEN_RESPONSE_SUCCESS.toUserState(
                    previousUserState = null,
                    environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
                )
            } returns SINGLE_USER_STATE_1

            // Start the login flow so that all the necessary data is cached.
            val result = repository.login(email = EMAIL, password = PASSWORD, captchaToken = null)

            // Set policies that will fail the password.
            mutableActivePolicyFlow.emit(
                listOf(
                    createMockPolicy(
                        type = PolicyTypeJson.MASTER_PASSWORD,
                        isEnabled = true,
                        data = buildJsonObject {
                            put(key = "minLength", value = 100)
                            put(key = "minComplexity", value = null)
                            put(key = "requireUpper", value = null)
                            put(key = "requireLower", value = null)
                            put(key = "requireNumbers", value = null)
                            put(key = "requireSpecial", value = null)
                            put(key = "enforceOnLogin", value = true)
                        },
                    ),
                ),
            )

            // Verify the results.
            assertEquals(LoginResult.Success, result)
            assertEquals(AuthState.Authenticated(ACCESS_TOKEN), repository.authStateFlow.value)
            coVerify { accountsService.preLogin(email = EMAIL) }
            fakeAuthDiskSource.assertPrivateKey(
                userId = USER_ID_1,
                privateKey = "privateKey",
            )
            fakeAuthDiskSource.assertUserKey(
                userId = USER_ID_1,
                userKey = "key",
            )
            fakeAuthDiskSource.assertMasterPasswordHash(
                userId = USER_ID_1,
                passwordHash = PASSWORD_HASH,
            )
            coVerify {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.MasterPassword(
                        username = EMAIL,
                        password = PASSWORD_HASH,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
                vaultRepository.unlockVault(
                    userId = USER_ID_1,
                    email = EMAIL,
                    kdf = ACCOUNT_1.profile.toSdkParams(),
                    userKey = successResponse.key!!,
                    privateKey = successResponse.privateKey!!,
                    organizationKeys = null,
                    masterPassword = PASSWORD,
                )
                vaultRepository.syncIfNecessary()
            }
            assertEquals(
                UserStateJson(
                    activeUserId = USER_ID_1,
                    accounts = mapOf(
                        USER_ID_1 to ACCOUNT_1.copy(
                            profile = ACCOUNT_1.profile.copy(
                                forcePasswordResetReason = ForcePasswordResetReason.WEAK_MASTER_PASSWORD_ON_LOGIN,
                            ),
                        ),
                    ),
                ),
                fakeAuthDiskSource.userState,
            )
            verify { settingsRepository.setDefaultsIfNecessary(userId = USER_ID_1) }
        }

    @Test
    fun `rememberedEmailAddress should pull from and update AuthDiskSource`() {
        // AuthDiskSource and the repository start with the same value.
        assertNull(repository.rememberedEmailAddress)
        assertNull(fakeAuthDiskSource.rememberedEmailAddress)

        // Updating the repository updates AuthDiskSource
        repository.rememberedEmailAddress = "remembered@gmail.com"
        assertEquals("remembered@gmail.com", fakeAuthDiskSource.rememberedEmailAddress)

        // Updating AuthDiskSource updates the repository
        fakeAuthDiskSource.rememberedEmailAddress = null
        assertNull(repository.rememberedEmailAddress)
    }

    @Test
    fun `rememberedOrgIdentifier should pull from and update AuthDiskSource`() {
        // AuthDiskSource and the repository start with the same value.
        assertNull(repository.rememberedOrgIdentifier)
        assertNull(fakeAuthDiskSource.rememberedOrgIdentifier)

        // Updating the repository updates AuthDiskSource
        repository.rememberedOrgIdentifier = "Bitwarden"
        assertEquals("Bitwarden", fakeAuthDiskSource.rememberedOrgIdentifier)

        // Updating AuthDiskSource updates the repository
        fakeAuthDiskSource.rememberedOrgIdentifier = null
        assertNull(repository.rememberedOrgIdentifier)
    }

    @Test
    fun `shouldTrustDevice should directly access the authDiskSource`() {
        // AuthDiskSource and the repository start with the same default value.
        assertFalse(repository.shouldTrustDevice)
        assertFalse(fakeAuthDiskSource.shouldTrustDevice)

        // Updating the repository updates AuthDiskSource
        repository.shouldTrustDevice = true
        assertTrue(fakeAuthDiskSource.shouldTrustDevice)

        // Updating AuthDiskSource updates the repository
        fakeAuthDiskSource.shouldTrustDevice = false
        assertFalse(repository.shouldTrustDevice)
    }

    @Test
    fun `passwordResetReason should pull from the user's profile in AuthDiskSource`() = runTest {
        val updatedProfile = ACCOUNT_1.profile.copy(
            forcePasswordResetReason = ForcePasswordResetReason.WEAK_MASTER_PASSWORD_ON_LOGIN,
        )
        fakeAuthDiskSource.userState = UserStateJson(
            activeUserId = USER_ID_1,
            accounts = mapOf(
                USER_ID_1 to ACCOUNT_1.copy(
                    profile = updatedProfile,
                ),
            ),
        )
        assertEquals(
            ForcePasswordResetReason.WEAK_MASTER_PASSWORD_ON_LOGIN,
            repository.passwordResetReason,
        )
    }

    @Test
    fun `clear Pending Account Deletion should unblock userState updates`() = runTest {
        val masterPassword = "hello world"
        val hashedMasterPassword = "dlrow olleh"
        val originalUserState = SINGLE_USER_STATE_1.toUserState(
            vaultState = VAULT_UNLOCK_DATA,
            userOrganizationsList = emptyList(),
            hasPendingAccountAddition = false,
            isBiometricsEnabledProvider = { false },
            vaultUnlockTypeProvider = { VaultUnlockType.MASTER_PASSWORD },
            isLoggedInProvider = { false },
            isDeviceTrustedProvider = { false },
        )
        val finalUserState = SINGLE_USER_STATE_2.toUserState(
            vaultState = VAULT_UNLOCK_DATA,
            userOrganizationsList = emptyList(),
            hasPendingAccountAddition = false,
            isBiometricsEnabledProvider = { false },
            vaultUnlockTypeProvider = { VaultUnlockType.MASTER_PASSWORD },
            isLoggedInProvider = { false },
            isDeviceTrustedProvider = { false },
        )
        val kdf = SINGLE_USER_STATE_1.activeAccount.profile.toSdkParams()
        coEvery {
            authSdkSource.hashPassword(EMAIL, masterPassword, kdf, HashPurpose.SERVER_AUTHORIZATION)
        } returns hashedMasterPassword.asSuccess()
        coEvery {
            accountsService.deleteAccount(hashedMasterPassword)
        } returns Unit.asSuccess()
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1

        repository.userStateFlow.test {
            assertEquals(originalUserState, awaitItem())

            // Deleting the account sets the pending deletion flag
            repository.deleteAccount(password = masterPassword)

            // Update the account. No changes are emitted because
            // the pending deletion blocks the update.
            fakeAuthDiskSource.userState = SINGLE_USER_STATE_2
            expectNoEvents()

            // Clearing the pending deletion allows the change to go through
            repository.clearPendingAccountDeletion()
            assertEquals(finalUserState, awaitItem())
        }
    }

    @Test
    fun `delete account fails if not logged in`() = runTest {
        val masterPassword = "hello world"
        val result = repository.deleteAccount(password = masterPassword)
        assertEquals(DeleteAccountResult.Error, result)
    }

    @Test
    fun `delete account fails if hashPassword fails`() = runTest {
        val masterPassword = "hello world"
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        val kdf = SINGLE_USER_STATE_1.activeAccount.profile.toSdkParams()
        coEvery {
            authSdkSource.hashPassword(EMAIL, masterPassword, kdf, HashPurpose.SERVER_AUTHORIZATION)
        } returns Throwable("Fail").asFailure()

        val result = repository.deleteAccount(password = masterPassword)

        assertEquals(DeleteAccountResult.Error, result)
        coVerify {
            authSdkSource.hashPassword(EMAIL, masterPassword, kdf, HashPurpose.SERVER_AUTHORIZATION)
        }
    }

    @Test
    fun `delete account fails if deleteAccount fails`() = runTest {
        val masterPassword = "hello world"
        val hashedMasterPassword = "dlrow olleh"
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        val kdf = SINGLE_USER_STATE_1.activeAccount.profile.toSdkParams()
        coEvery {
            authSdkSource.hashPassword(EMAIL, masterPassword, kdf, HashPurpose.SERVER_AUTHORIZATION)
        } returns hashedMasterPassword.asSuccess()
        coEvery {
            accountsService.deleteAccount(hashedMasterPassword)
        } returns Throwable("Fail").asFailure()

        val result = repository.deleteAccount(password = masterPassword)

        assertEquals(DeleteAccountResult.Error, result)
        coVerify {
            authSdkSource.hashPassword(EMAIL, masterPassword, kdf, HashPurpose.SERVER_AUTHORIZATION)
            accountsService.deleteAccount(hashedMasterPassword)
        }
    }

    @Test
    fun `delete account succeeds`() = runTest {
        val masterPassword = "hello world"
        val hashedMasterPassword = "dlrow olleh"
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        val kdf = SINGLE_USER_STATE_1.activeAccount.profile.toSdkParams()
        coEvery {
            authSdkSource.hashPassword(EMAIL, masterPassword, kdf, HashPurpose.SERVER_AUTHORIZATION)
        } returns hashedMasterPassword.asSuccess()
        coEvery {
            accountsService.deleteAccount(hashedMasterPassword)
        } returns Unit.asSuccess()

        val result = repository.deleteAccount(password = masterPassword)

        assertEquals(DeleteAccountResult.Success, result)
        coVerify {
            authSdkSource.hashPassword(EMAIL, masterPassword, kdf, HashPurpose.SERVER_AUTHORIZATION)
            accountsService.deleteAccount(hashedMasterPassword)
        }
    }

    @Test
    fun `refreshTokenSynchronously returns failure if not logged in`() = runTest {
        fakeAuthDiskSource.userState = null

        val result = repository.refreshAccessTokenSynchronously(USER_ID_1)

        assertTrue(result.isFailure)
    }

    @Test
    fun `refreshTokenSynchronously returns failure and logs out on failure`() = runTest {
        fakeAuthDiskSource.storeAccountTokens(
            userId = USER_ID_1,
            accountTokens = ACCOUNT_TOKENS_1,
        )
        coEvery {
            identityService.refreshTokenSynchronously(REFRESH_TOKEN)
        } returns Throwable("Fail").asFailure()

        assertTrue(repository.refreshAccessTokenSynchronously(USER_ID_1).isFailure)

        coVerify(exactly = 1) {
            identityService.refreshTokenSynchronously(REFRESH_TOKEN)
        }
    }

    @Test
    fun `refreshTokenSynchronously returns success and update user state on success`() = runTest {
        fakeAuthDiskSource.storeAccountTokens(
            userId = USER_ID_1,
            accountTokens = ACCOUNT_TOKENS_1,
        )
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        coEvery {
            identityService.refreshTokenSynchronously(REFRESH_TOKEN)
        } returns REFRESH_TOKEN_RESPONSE_JSON.asSuccess()
        every {
            REFRESH_TOKEN_RESPONSE_JSON.toUserStateJson(
                userId = USER_ID_1,
                previousUserState = SINGLE_USER_STATE_1,
            )
        } returns SINGLE_USER_STATE_1

        val result = repository.refreshAccessTokenSynchronously(USER_ID_1)

        assertEquals(REFRESH_TOKEN_RESPONSE_JSON.asSuccess(), result)
        coVerify(exactly = 1) {
            identityService.refreshTokenSynchronously(REFRESH_TOKEN)
            REFRESH_TOKEN_RESPONSE_JSON.toUserStateJson(
                userId = USER_ID_1,
                previousUserState = SINGLE_USER_STATE_1,
            )
        }
    }

    @Test
    fun `login when pre login fails should return Error with no message`() = runTest {
        coEvery {
            accountsService.preLogin(email = EMAIL)
        } returns RuntimeException().asFailure()
        val result = repository.login(email = EMAIL, password = PASSWORD, captchaToken = null)
        assertEquals(LoginResult.Error(errorMessage = null), result)
        assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
        coVerify { accountsService.preLogin(email = EMAIL) }
    }

    @Test
    fun `login get token fails should return Error with no message`() = runTest {
        coEvery {
            accountsService.preLogin(email = EMAIL)
        } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns RuntimeException().asFailure()
        val result = repository.login(email = EMAIL, password = PASSWORD, captchaToken = null)
        assertEquals(LoginResult.Error(errorMessage = null), result)
        assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
        coVerify { accountsService.preLogin(email = EMAIL) }
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }
    }

    @Test
    fun `login get token returns Invalid should return Error with correct message`() = runTest {
        coEvery {
            accountsService.preLogin(email = EMAIL)
        } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns GetTokenResponseJson
            .Invalid(
                errorModel = GetTokenResponseJson.Invalid.ErrorModel(
                    errorMessage = "mock_error_message",
                ),
            )
            .asSuccess()

        val result = repository.login(email = EMAIL, password = PASSWORD, captchaToken = null)
        assertEquals(LoginResult.Error(errorMessage = "mock_error_message"), result)
        assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
        coVerify { accountsService.preLogin(email = EMAIL) }
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `login get token succeeds should return Success, unlockVault, update AuthState, update stored keys, and sync`() =
        runTest {
            val successResponse = GET_TOKEN_RESPONSE_SUCCESS
            coEvery {
                accountsService.preLogin(email = EMAIL)
            } returns PRE_LOGIN_SUCCESS.asSuccess()
            coEvery {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.MasterPassword(
                        username = EMAIL,
                        password = PASSWORD_HASH,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            } returns successResponse.asSuccess()
            coEvery {
                vaultRepository.unlockVault(
                    userId = USER_ID_1,
                    email = EMAIL,
                    kdf = ACCOUNT_1.profile.toSdkParams(),
                    userKey = successResponse.key!!,
                    privateKey = successResponse.privateKey!!,
                    organizationKeys = null,
                    masterPassword = PASSWORD,
                )
            } returns VaultUnlockResult.Success
            coEvery { vaultRepository.syncIfNecessary() } just runs
            every {
                GET_TOKEN_RESPONSE_SUCCESS.toUserState(
                    previousUserState = null,
                    environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
                )
            } returns SINGLE_USER_STATE_1
            val result = repository.login(email = EMAIL, password = PASSWORD, captchaToken = null)
            assertEquals(LoginResult.Success, result)
            assertEquals(AuthState.Authenticated(ACCESS_TOKEN), repository.authStateFlow.value)
            coVerify { accountsService.preLogin(email = EMAIL) }
            fakeAuthDiskSource.assertPrivateKey(
                userId = USER_ID_1,
                privateKey = "privateKey",
            )
            fakeAuthDiskSource.assertUserKey(
                userId = USER_ID_1,
                userKey = "key",
            )
            fakeAuthDiskSource.assertMasterPasswordHash(
                userId = USER_ID_1,
                passwordHash = PASSWORD_HASH,
            )
            coVerify {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.MasterPassword(
                        username = EMAIL,
                        password = PASSWORD_HASH,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
                vaultRepository.unlockVault(
                    userId = USER_ID_1,
                    email = EMAIL,
                    kdf = ACCOUNT_1.profile.toSdkParams(),
                    userKey = successResponse.key!!,
                    privateKey = successResponse.privateKey!!,
                    organizationKeys = null,
                    masterPassword = PASSWORD,
                )
                vaultRepository.syncIfNecessary()
            }
            assertEquals(
                SINGLE_USER_STATE_1,
                fakeAuthDiskSource.userState,
            )
            verify { settingsRepository.setDefaultsIfNecessary(userId = USER_ID_1) }
        }

    @Test
    @Suppress("MaxLineLength")
    fun `login get token succeeds with null keys and hasMasterPassword false should not call unlockVault`() =
        runTest {
            val successResponse = GET_TOKEN_RESPONSE_SUCCESS.copy(
                key = null,
                privateKey = null,
                userDecryptionOptions = UserDecryptionOptionsJson(
                    hasMasterPassword = false,
                    keyConnectorUserDecryptionOptions = null,
                    trustedDeviceUserDecryptionOptions = null,
                ),
            )
            coEvery {
                accountsService.preLogin(email = EMAIL)
            } returns PRE_LOGIN_SUCCESS.asSuccess()
            coEvery {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.MasterPassword(
                        username = EMAIL,
                        password = PASSWORD_HASH,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            } returns successResponse.asSuccess()
            coEvery { vaultRepository.syncIfNecessary() } just runs
            every {
                successResponse.toUserState(
                    previousUserState = null,
                    environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
                )
            } returns SINGLE_USER_STATE_1
            val result = repository.login(
                email = EMAIL,
                password = PASSWORD,
                captchaToken = null,
            )
            assertEquals(LoginResult.Success, result)
            assertEquals(AuthState.Authenticated(ACCESS_TOKEN), repository.authStateFlow.value)
            coVerify { accountsService.preLogin(email = EMAIL) }
            fakeAuthDiskSource.assertMasterPasswordHash(
                userId = USER_ID_1,
                passwordHash = PASSWORD_HASH,
            )
            coVerify {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.MasterPassword(
                        username = EMAIL,
                        password = PASSWORD_HASH,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
                vaultRepository.syncIfNecessary()
            }
            assertEquals(
                SINGLE_USER_STATE_1,
                fakeAuthDiskSource.userState,
            )
            verify { settingsRepository.setDefaultsIfNecessary(userId = USER_ID_1) }
            coVerify(exactly = 0) {
                vaultRepository.unlockVault(
                    userId = USER_ID_1,
                    email = EMAIL,
                    kdf = ACCOUNT_1.profile.toSdkParams(),
                    userKey = any(),
                    privateKey = any(),
                    organizationKeys = null,
                    masterPassword = PASSWORD,
                )
            }
        }

    @Suppress("MaxLineLength")
    @Test
    fun `login get token succeeds when there is an existing user should switch to the new logged in user and lock the old user's vault`() =
        runTest {
            // Ensure the initial state for User 2 with a account addition
            fakeAuthDiskSource.userState = SINGLE_USER_STATE_2
            repository.hasPendingAccountAddition = true

            // Set up login for User 1
            val successResponse = GET_TOKEN_RESPONSE_SUCCESS
            coEvery {
                accountsService.preLogin(email = EMAIL)
            } returns PRE_LOGIN_SUCCESS.asSuccess()
            coEvery {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.MasterPassword(
                        username = EMAIL,
                        password = PASSWORD_HASH,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            } returns successResponse.asSuccess()
            coEvery {
                vaultRepository.unlockVault(
                    userId = USER_ID_1,
                    email = EMAIL,
                    kdf = ACCOUNT_1.profile.toSdkParams(),
                    userKey = successResponse.key!!,
                    privateKey = successResponse.privateKey!!,
                    organizationKeys = null,
                    masterPassword = PASSWORD,
                )
            } returns VaultUnlockResult.Success
            coEvery { vaultRepository.syncIfNecessary() } just runs
            every {
                GET_TOKEN_RESPONSE_SUCCESS.toUserState(
                    previousUserState = SINGLE_USER_STATE_2,
                    environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
                )
            } returns MULTI_USER_STATE

            val result = repository.login(email = EMAIL, password = PASSWORD, captchaToken = null)

            assertEquals(LoginResult.Success, result)
            assertEquals(AuthState.Authenticated(ACCESS_TOKEN), repository.authStateFlow.value)
            coVerify { accountsService.preLogin(email = EMAIL) }
            fakeAuthDiskSource.assertPrivateKey(
                userId = USER_ID_1,
                privateKey = "privateKey",
            )
            fakeAuthDiskSource.assertUserKey(
                userId = USER_ID_1,
                userKey = "key",
            )
            coVerify {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.MasterPassword(
                        username = EMAIL,
                        password = PASSWORD_HASH,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
                vaultRepository.unlockVault(
                    userId = USER_ID_1,
                    email = EMAIL,
                    kdf = ACCOUNT_1.profile.toSdkParams(),
                    userKey = successResponse.key!!,
                    privateKey = successResponse.privateKey!!,
                    organizationKeys = null,
                    masterPassword = PASSWORD,
                )
                vaultRepository.syncIfNecessary()
            }
            assertEquals(
                MULTI_USER_STATE,
                fakeAuthDiskSource.userState,
            )
            assertFalse(repository.hasPendingAccountAddition)
            verify { settingsRepository.setDefaultsIfNecessary(userId = USER_ID_1) }
        }

    @Test
    fun `login get token returns captcha request should return CaptchaRequired`() = runTest {
        coEvery { accountsService.preLogin(EMAIL) } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns GetTokenResponseJson.CaptchaRequired(CAPTCHA_KEY).asSuccess()
        val result = repository.login(email = EMAIL, password = PASSWORD, captchaToken = null)
        assertEquals(LoginResult.CaptchaRequired(CAPTCHA_KEY), result)
        assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
        coVerify { accountsService.preLogin(email = EMAIL) }
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }
    }

    @Test
    fun `login get token returns two factor request should return TwoFactorRequired`() = runTest {
        coEvery { accountsService.preLogin(EMAIL) } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns GetTokenResponseJson
            .TwoFactorRequired(
                authMethodsData = TWO_FACTOR_AUTH_METHODS_DATA,
                captchaToken = null,
                ssoToken = null,
            )
            .asSuccess()
        val result = repository.login(email = EMAIL, password = PASSWORD, captchaToken = null)
        assertEquals(LoginResult.TwoFactorRequired, result)
        assertEquals(
            repository.twoFactorResponse,
            GetTokenResponseJson.TwoFactorRequired(TWO_FACTOR_AUTH_METHODS_DATA, null, null),
        )
        assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
        coVerify { accountsService.preLogin(email = EMAIL) }
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }
    }

    @Test
    fun `login two factor with remember saves two factor auth token`() = runTest {
        // Attempt a normal login with a two factor error first, so that the auth
        // data will be cached.
        coEvery { accountsService.preLogin(EMAIL) } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns GetTokenResponseJson
            .TwoFactorRequired(
                authMethodsData = TWO_FACTOR_AUTH_METHODS_DATA,
                captchaToken = null,
                ssoToken = null,
            )
            .asSuccess()
        val firstResult = repository.login(email = EMAIL, password = PASSWORD, captchaToken = null)
        assertEquals(LoginResult.TwoFactorRequired, firstResult)
        coVerify { accountsService.preLogin(email = EMAIL) }
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }

        // Login with two factor data.
        val successResponse = GET_TOKEN_RESPONSE_SUCCESS.copy(
            twoFactorToken = "twoFactorTokenToStore",
        )
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
                twoFactorData = TWO_FACTOR_DATA,
            )
        } returns successResponse.asSuccess()
        coEvery {
            vaultRepository.unlockVault(
                userId = USER_ID_1,
                email = EMAIL,
                kdf = ACCOUNT_1.profile.toSdkParams(),
                userKey = successResponse.key!!,
                privateKey = successResponse.privateKey!!,
                organizationKeys = null,
                masterPassword = PASSWORD,
            )
        } returns VaultUnlockResult.Success
        coEvery { vaultRepository.syncIfNecessary() } just runs
        every {
            successResponse.toUserState(
                previousUserState = null,
                environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
            )
        } returns SINGLE_USER_STATE_1
        val finalResult = repository.login(
            email = EMAIL,
            password = PASSWORD,
            twoFactorData = TWO_FACTOR_DATA,
            captchaToken = null,
        )
        assertEquals(LoginResult.Success, finalResult)
        assertNull(repository.twoFactorResponse)
        fakeAuthDiskSource.assertTwoFactorToken(
            email = EMAIL,
            twoFactorToken = "twoFactorTokenToStore",
        )
    }

    @Test
    fun `login uses remembered two factor tokens`() = runTest {
        fakeAuthDiskSource.storeTwoFactorToken(EMAIL, "storedTwoFactorToken")
        val rememberedTwoFactorData = TwoFactorDataModel(
            code = "storedTwoFactorToken",
            method = TwoFactorAuthMethod.REMEMBER.value.toString(),
            remember = false,
        )
        val successResponse = GET_TOKEN_RESPONSE_SUCCESS
        coEvery {
            accountsService.preLogin(email = EMAIL)
        } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
                twoFactorData = rememberedTwoFactorData,
            )
        } returns successResponse.asSuccess()
        coEvery {
            vaultRepository.unlockVault(
                userId = USER_ID_1,
                email = EMAIL,
                kdf = ACCOUNT_1.profile.toSdkParams(),
                userKey = successResponse.key!!,
                privateKey = successResponse.privateKey!!,
                organizationKeys = null,
                masterPassword = PASSWORD,
            )
        } returns VaultUnlockResult.Success
        coEvery { vaultRepository.syncIfNecessary() } just runs
        every {
            GET_TOKEN_RESPONSE_SUCCESS.toUserState(
                previousUserState = null,
                environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
            )
        } returns SINGLE_USER_STATE_1
        val result = repository.login(email = EMAIL, password = PASSWORD, captchaToken = null)
        assertEquals(LoginResult.Success, result)
        assertEquals(AuthState.Authenticated(ACCESS_TOKEN), repository.authStateFlow.value)
        coVerify { accountsService.preLogin(email = EMAIL) }
        fakeAuthDiskSource.assertPrivateKey(
            userId = USER_ID_1,
            privateKey = "privateKey",
        )
        fakeAuthDiskSource.assertUserKey(
            userId = USER_ID_1,
            userKey = "key",
        )
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
                twoFactorData = rememberedTwoFactorData,
            )
            vaultRepository.unlockVault(
                userId = USER_ID_1,
                email = EMAIL,
                kdf = ACCOUNT_1.profile.toSdkParams(),
                userKey = successResponse.key!!,
                privateKey = successResponse.privateKey!!,
                organizationKeys = null,
                masterPassword = PASSWORD,
            )
            vaultRepository.syncIfNecessary()
        }
        assertEquals(
            SINGLE_USER_STATE_1,
            fakeAuthDiskSource.userState,
        )
        verify { settingsRepository.setDefaultsIfNecessary(userId = USER_ID_1) }
    }

    @Test
    fun `login two factor returns error if no cached auth data`() = runTest {
        val result = repository.login(
            email = EMAIL,
            password = PASSWORD,
            twoFactorData = TWO_FACTOR_DATA,
            captchaToken = null,
        )
        assertEquals(LoginResult.Error(errorMessage = null), result)
    }

    @Test
    fun `login with device get token fails should return Error with no message`() = runTest {
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.AuthRequest(
                    username = EMAIL,
                    authRequestId = DEVICE_REQUEST_ID,
                    accessCode = DEVICE_ACCESS_CODE,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns Throwable("Fail").asFailure()
        val result = repository.login(
            email = EMAIL,
            requestId = DEVICE_REQUEST_ID,
            accessCode = DEVICE_ACCESS_CODE,
            asymmetricalKey = DEVICE_ASYMMETRICAL_KEY,
            requestPrivateKey = DEVICE_REQUEST_PRIVATE_KEY,
            masterPasswordHash = PASSWORD_HASH,
            captchaToken = null,
        )
        assertEquals(LoginResult.Error(errorMessage = null), result)
        assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.AuthRequest(
                    username = EMAIL,
                    authRequestId = DEVICE_REQUEST_ID,
                    accessCode = DEVICE_ACCESS_CODE,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }
    }

    @Test
    fun `login with device get token returns Invalid should return Error with correct message`() =
        runTest {
            coEvery {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.AuthRequest(
                        username = EMAIL,
                        authRequestId = DEVICE_REQUEST_ID,
                        accessCode = DEVICE_ACCESS_CODE,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            } returns GetTokenResponseJson
                .Invalid(
                    errorModel = GetTokenResponseJson.Invalid.ErrorModel(
                        errorMessage = "mock_error_message",
                    ),
                )
                .asSuccess()

            val result = repository.login(
                email = EMAIL,
                requestId = DEVICE_REQUEST_ID,
                accessCode = DEVICE_ACCESS_CODE,
                asymmetricalKey = DEVICE_ASYMMETRICAL_KEY,
                requestPrivateKey = DEVICE_REQUEST_PRIVATE_KEY,
                masterPasswordHash = PASSWORD_HASH,
                captchaToken = null,
            )
            assertEquals(LoginResult.Error(errorMessage = "mock_error_message"), result)
            assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
            coVerify {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.AuthRequest(
                        username = EMAIL,
                        authRequestId = DEVICE_REQUEST_ID,
                        accessCode = DEVICE_ACCESS_CODE,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            }
        }

    @Test
    @Suppress("MaxLineLength")
    fun `login with device get token succeeds should return Success, update AuthState, update stored keys, and sync`() =
        runTest {
            val successResponse = GET_TOKEN_RESPONSE_SUCCESS
            coEvery {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.AuthRequest(
                        username = EMAIL,
                        authRequestId = DEVICE_REQUEST_ID,
                        accessCode = DEVICE_ACCESS_CODE,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            } returns successResponse.asSuccess()
            coEvery { vaultRepository.syncIfNecessary() } just runs
            every {
                GET_TOKEN_RESPONSE_SUCCESS.toUserState(
                    previousUserState = null,
                    environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
                )
            } returns SINGLE_USER_STATE_1
            coEvery {
                vaultRepository.unlockVault(
                    userId = USER_ID_1,
                    email = EMAIL,
                    kdf = ACCOUNT_1.profile.toSdkParams(),
                    privateKey = successResponse.privateKey!!,
                    organizationKeys = null,
                    initUserCryptoMethod = InitUserCryptoMethod.AuthRequest(
                        requestPrivateKey = DEVICE_REQUEST_PRIVATE_KEY,
                        method = AuthRequestMethod.MasterKey(
                            authRequestKey = successResponse.key!!,
                            protectedMasterKey = DEVICE_ASYMMETRICAL_KEY,
                        ),
                    ),
                )
            } returns VaultUnlockResult.Success
            val result = repository.login(
                email = EMAIL,
                requestId = DEVICE_REQUEST_ID,
                accessCode = DEVICE_ACCESS_CODE,
                asymmetricalKey = DEVICE_ASYMMETRICAL_KEY,
                requestPrivateKey = DEVICE_REQUEST_PRIVATE_KEY,
                masterPasswordHash = PASSWORD_HASH,
                captchaToken = null,
            )
            assertEquals(LoginResult.Success, result)
            assertEquals(AuthState.Authenticated(ACCESS_TOKEN), repository.authStateFlow.value)
            fakeAuthDiskSource.assertPrivateKey(
                userId = USER_ID_1,
                privateKey = "privateKey",
            )
            fakeAuthDiskSource.assertUserKey(
                userId = USER_ID_1,
                userKey = "key",
            )
            coVerify {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.AuthRequest(
                        username = EMAIL,
                        authRequestId = DEVICE_REQUEST_ID,
                        accessCode = DEVICE_ACCESS_CODE,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
                vaultRepository.syncIfNecessary()
                vaultRepository.unlockVault(
                    userId = USER_ID_1,
                    email = EMAIL,
                    kdf = ACCOUNT_1.profile.toSdkParams(),
                    privateKey = successResponse.privateKey!!,
                    organizationKeys = null,
                    initUserCryptoMethod = InitUserCryptoMethod.AuthRequest(
                        requestPrivateKey = DEVICE_REQUEST_PRIVATE_KEY,
                        method = AuthRequestMethod.MasterKey(
                            authRequestKey = successResponse.key!!,
                            protectedMasterKey = DEVICE_ASYMMETRICAL_KEY,
                        ),
                    ),
                )
            }
            assertEquals(
                SINGLE_USER_STATE_1,
                fakeAuthDiskSource.userState,
            )
            verify { settingsRepository.setDefaultsIfNecessary(userId = USER_ID_1) }
        }

    @Test
    fun `login with device get token returns captcha request should return CaptchaRequired`() =
        runTest {
            coEvery {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.AuthRequest(
                        username = EMAIL,
                        authRequestId = DEVICE_REQUEST_ID,
                        accessCode = DEVICE_ACCESS_CODE,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            } returns GetTokenResponseJson.CaptchaRequired(CAPTCHA_KEY).asSuccess()
            val result = repository.login(
                email = EMAIL,
                requestId = DEVICE_REQUEST_ID,
                accessCode = DEVICE_ACCESS_CODE,
                asymmetricalKey = DEVICE_ASYMMETRICAL_KEY,
                requestPrivateKey = DEVICE_REQUEST_PRIVATE_KEY,
                masterPasswordHash = PASSWORD_HASH,
                captchaToken = null,
            )
            assertEquals(LoginResult.CaptchaRequired(CAPTCHA_KEY), result)
            assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
            coVerify {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.AuthRequest(
                        username = EMAIL,
                        authRequestId = DEVICE_REQUEST_ID,
                        accessCode = DEVICE_ACCESS_CODE,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            }
        }

    @Test
    fun `login with device get token returns two factor request should return TwoFactorRequired`() =
        runTest {
            coEvery {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.AuthRequest(
                        username = EMAIL,
                        authRequestId = DEVICE_REQUEST_ID,
                        accessCode = DEVICE_ACCESS_CODE,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            } returns GetTokenResponseJson
                .TwoFactorRequired(TWO_FACTOR_AUTH_METHODS_DATA, null, null)
                .asSuccess()
            val result = repository.login(
                email = EMAIL,
                requestId = DEVICE_REQUEST_ID,
                accessCode = DEVICE_ACCESS_CODE,
                asymmetricalKey = DEVICE_ASYMMETRICAL_KEY,
                requestPrivateKey = DEVICE_REQUEST_PRIVATE_KEY,
                masterPasswordHash = PASSWORD_HASH,
                captchaToken = null,
            )
            assertEquals(LoginResult.TwoFactorRequired, result)
            assertEquals(
                repository.twoFactorResponse,
                GetTokenResponseJson.TwoFactorRequired(TWO_FACTOR_AUTH_METHODS_DATA, null, null),
            )
            assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
            coVerify {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.AuthRequest(
                        username = EMAIL,
                        authRequestId = DEVICE_REQUEST_ID,
                        accessCode = DEVICE_ACCESS_CODE,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            }
        }

    @Test
    fun `login with device two factor with remember saves two factor auth token`() = runTest {
        // Attempt a normal login with a two factor error first, so that the auth
        // data will be cached.
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.AuthRequest(
                    username = EMAIL,
                    authRequestId = DEVICE_REQUEST_ID,
                    accessCode = DEVICE_ACCESS_CODE,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns GetTokenResponseJson
            .TwoFactorRequired(TWO_FACTOR_AUTH_METHODS_DATA, null, null)
            .asSuccess()
        val firstResult = repository.login(
            email = EMAIL,
            requestId = DEVICE_REQUEST_ID,
            accessCode = DEVICE_ACCESS_CODE,
            asymmetricalKey = DEVICE_ASYMMETRICAL_KEY,
            requestPrivateKey = DEVICE_REQUEST_PRIVATE_KEY,
            masterPasswordHash = PASSWORD_HASH,
            captchaToken = null,
        )
        assertEquals(LoginResult.TwoFactorRequired, firstResult)
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.AuthRequest(
                    username = EMAIL,
                    authRequestId = DEVICE_REQUEST_ID,
                    accessCode = DEVICE_ACCESS_CODE,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }

        // Login with two factor data.
        val successResponse = GET_TOKEN_RESPONSE_SUCCESS.copy(
            twoFactorToken = "twoFactorTokenToStore",
        )
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.AuthRequest(
                    username = EMAIL,
                    authRequestId = DEVICE_REQUEST_ID,
                    accessCode = DEVICE_ACCESS_CODE,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
                twoFactorData = TWO_FACTOR_DATA,
            )
        } returns successResponse.asSuccess()
        coEvery { vaultRepository.syncIfNecessary() } just runs
        every {
            successResponse.toUserState(
                previousUserState = null,
                environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
            )
        } returns SINGLE_USER_STATE_1
        coEvery {
            vaultRepository.unlockVault(
                userId = SINGLE_USER_STATE_1.activeUserId,
                email = SINGLE_USER_STATE_1.activeAccount.profile.email,
                kdf = SINGLE_USER_STATE_1.activeAccount.profile.toSdkParams(),
                privateKey = successResponse.privateKey!!,
                initUserCryptoMethod = InitUserCryptoMethod.AuthRequest(
                    requestPrivateKey = DEVICE_REQUEST_PRIVATE_KEY,
                    method = AuthRequestMethod.MasterKey(
                        protectedMasterKey = DEVICE_ASYMMETRICAL_KEY,
                        authRequestKey = successResponse.key!!,
                    ),
                ),
                organizationKeys = null,
            )
        } returns VaultUnlockResult.Success
        val finalResult = repository.login(
            email = EMAIL,
            password = null,
            twoFactorData = TWO_FACTOR_DATA,
            captchaToken = null,
        )
        assertEquals(LoginResult.Success, finalResult)
        assertNull(repository.twoFactorResponse)
        fakeAuthDiskSource.assertTwoFactorToken(
            email = EMAIL,
            twoFactorToken = "twoFactorTokenToStore",
        )
    }

    @Test
    fun `SSO login get token fails should return Error with no message`() = runTest {
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.SingleSignOn(
                    ssoCode = SSO_CODE,
                    ssoCodeVerifier = SSO_CODE_VERIFIER,
                    ssoRedirectUri = SSO_REDIRECT_URI,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns RuntimeException().asFailure()
        val result = repository.login(
            email = EMAIL,
            ssoCode = SSO_CODE,
            ssoCodeVerifier = SSO_CODE_VERIFIER,
            ssoRedirectUri = SSO_REDIRECT_URI,
            captchaToken = null,
            organizationIdentifier = ORGANIZATION_IDENTIFIER,
        )
        assertEquals(LoginResult.Error(errorMessage = null), result)
        assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.SingleSignOn(
                    ssoCode = SSO_CODE,
                    ssoCodeVerifier = SSO_CODE_VERIFIER,
                    ssoRedirectUri = SSO_REDIRECT_URI,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }
    }

    @Test
    fun `SSO login get token returns Invalid should return Error with correct message`() = runTest {
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.SingleSignOn(
                    ssoCode = SSO_CODE,
                    ssoCodeVerifier = SSO_CODE_VERIFIER,
                    ssoRedirectUri = SSO_REDIRECT_URI,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns GetTokenResponseJson
            .Invalid(
                errorModel = GetTokenResponseJson.Invalid.ErrorModel(
                    errorMessage = "mock_error_message",
                ),
            )
            .asSuccess()

        val result = repository.login(
            email = EMAIL,
            ssoCode = SSO_CODE,
            ssoCodeVerifier = SSO_CODE_VERIFIER,
            ssoRedirectUri = SSO_REDIRECT_URI,
            captchaToken = null,
            organizationIdentifier = ORGANIZATION_IDENTIFIER,
        )
        assertEquals(LoginResult.Error(errorMessage = "mock_error_message"), result)
        assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.SingleSignOn(
                    ssoCode = SSO_CODE,
                    ssoCodeVerifier = SSO_CODE_VERIFIER,
                    ssoRedirectUri = SSO_REDIRECT_URI,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `SSO login get token succeeds should return Success, update AuthState, update stored keys, and sync`() =
        runTest {
            val successResponse = GET_TOKEN_RESPONSE_SUCCESS
            coEvery {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.SingleSignOn(
                        ssoCode = SSO_CODE,
                        ssoCodeVerifier = SSO_CODE_VERIFIER,
                        ssoRedirectUri = SSO_REDIRECT_URI,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            } returns successResponse.asSuccess()
            coEvery { vaultRepository.syncIfNecessary() } just runs
            every {
                GET_TOKEN_RESPONSE_SUCCESS.toUserState(
                    previousUserState = null,
                    environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
                )
            } returns SINGLE_USER_STATE_1
            val result = repository.login(
                email = EMAIL,
                ssoCode = SSO_CODE,
                ssoCodeVerifier = SSO_CODE_VERIFIER,
                ssoRedirectUri = SSO_REDIRECT_URI,
                captchaToken = null,
                organizationIdentifier = ORGANIZATION_IDENTIFIER,
            )
            assertEquals(LoginResult.Success, result)
            assertEquals(AuthState.Authenticated(ACCESS_TOKEN), repository.authStateFlow.value)
            fakeAuthDiskSource.assertPrivateKey(
                userId = USER_ID_1,
                privateKey = "privateKey",
            )
            fakeAuthDiskSource.assertUserKey(
                userId = USER_ID_1,
                userKey = "key",
            )
            coVerify {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.SingleSignOn(
                        ssoCode = SSO_CODE,
                        ssoCodeVerifier = SSO_CODE_VERIFIER,
                        ssoRedirectUri = SSO_REDIRECT_URI,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
                vaultRepository.syncIfNecessary()
            }
            assertEquals(
                SINGLE_USER_STATE_1,
                fakeAuthDiskSource.userState,
            )
            verify { settingsRepository.setDefaultsIfNecessary(userId = USER_ID_1) }
        }

    @Suppress("MaxLineLength")
    @Test
    fun `SSO login get token succeeds when there is an existing user should switch to the new logged in user`() =
        runTest {
            // Ensure the initial state for User 2 with a account addition
            fakeAuthDiskSource.userState = SINGLE_USER_STATE_2
            repository.hasPendingAccountAddition = true

            // Set up login for User 1
            val successResponse = GET_TOKEN_RESPONSE_SUCCESS
            coEvery {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.SingleSignOn(
                        ssoCode = SSO_CODE,
                        ssoCodeVerifier = SSO_CODE_VERIFIER,
                        ssoRedirectUri = SSO_REDIRECT_URI,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            } returns successResponse.asSuccess()
            coEvery { vaultRepository.syncIfNecessary() } just runs
            every {
                GET_TOKEN_RESPONSE_SUCCESS.toUserState(
                    previousUserState = SINGLE_USER_STATE_2,
                    environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
                )
            } returns MULTI_USER_STATE

            val result = repository.login(
                email = EMAIL,
                ssoCode = SSO_CODE,
                ssoCodeVerifier = SSO_CODE_VERIFIER,
                ssoRedirectUri = SSO_REDIRECT_URI,
                captchaToken = null,
                organizationIdentifier = ORGANIZATION_IDENTIFIER,
            )

            assertEquals(LoginResult.Success, result)
            assertEquals(AuthState.Authenticated(ACCESS_TOKEN), repository.authStateFlow.value)
            fakeAuthDiskSource.assertPrivateKey(
                userId = USER_ID_1,
                privateKey = "privateKey",
            )
            fakeAuthDiskSource.assertUserKey(
                userId = USER_ID_1,
                userKey = "key",
            )
            coVerify {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.SingleSignOn(
                        ssoCode = SSO_CODE,
                        ssoCodeVerifier = SSO_CODE_VERIFIER,
                        ssoRedirectUri = SSO_REDIRECT_URI,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
                vaultRepository.syncIfNecessary()
            }
            assertEquals(
                MULTI_USER_STATE,
                fakeAuthDiskSource.userState,
            )
            assertFalse(repository.hasPendingAccountAddition)
            verify { settingsRepository.setDefaultsIfNecessary(userId = USER_ID_1) }
        }

    @Test
    fun `SSO login get token returns captcha request should return CaptchaRequired`() = runTest {
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.SingleSignOn(
                    ssoCode = SSO_CODE,
                    ssoCodeVerifier = SSO_CODE_VERIFIER,
                    ssoRedirectUri = SSO_REDIRECT_URI,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns GetTokenResponseJson.CaptchaRequired(CAPTCHA_KEY).asSuccess()
        val result = repository.login(
            email = EMAIL,
            ssoCode = SSO_CODE,
            ssoCodeVerifier = SSO_CODE_VERIFIER,
            ssoRedirectUri = SSO_REDIRECT_URI,
            captchaToken = null,
            organizationIdentifier = ORGANIZATION_IDENTIFIER,
        )
        assertEquals(LoginResult.CaptchaRequired(CAPTCHA_KEY), result)
        assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.SingleSignOn(
                    ssoCode = SSO_CODE,
                    ssoCodeVerifier = SSO_CODE_VERIFIER,
                    ssoRedirectUri = SSO_REDIRECT_URI,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }
    }

    @Test
    fun `SSO login get token returns two factor request should return TwoFactorRequired`() =
        runTest {
            coEvery {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.SingleSignOn(
                        ssoCode = SSO_CODE,
                        ssoCodeVerifier = SSO_CODE_VERIFIER,
                        ssoRedirectUri = SSO_REDIRECT_URI,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            } returns GetTokenResponseJson
                .TwoFactorRequired(
                    authMethodsData = TWO_FACTOR_AUTH_METHODS_DATA,
                    captchaToken = null,
                    ssoToken = null,
                )
                .asSuccess()
            val result = repository.login(
                email = EMAIL,
                ssoCode = SSO_CODE,
                ssoCodeVerifier = SSO_CODE_VERIFIER,
                ssoRedirectUri = SSO_REDIRECT_URI,
                captchaToken = null,
                organizationIdentifier = ORGANIZATION_IDENTIFIER,
            )
            assertEquals(LoginResult.TwoFactorRequired, result)
            assertEquals(
                repository.twoFactorResponse,
                GetTokenResponseJson.TwoFactorRequired(TWO_FACTOR_AUTH_METHODS_DATA, null, null),
            )
            assertEquals(AuthState.Unauthenticated, repository.authStateFlow.value)
            coVerify {
                identityService.getToken(
                    email = EMAIL,
                    authModel = IdentityTokenAuthModel.SingleSignOn(
                        ssoCode = SSO_CODE,
                        ssoCodeVerifier = SSO_CODE_VERIFIER,
                        ssoRedirectUri = SSO_REDIRECT_URI,
                    ),
                    captchaToken = null,
                    uniqueAppId = UNIQUE_APP_ID,
                )
            }
        }

    @Test
    fun `SSO login two factor with remember saves two factor auth token`() = runTest {
        // Attempt a normal login with a two factor error first, so that the auth
        // data will be cached.
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.SingleSignOn(
                    ssoCode = SSO_CODE,
                    ssoCodeVerifier = SSO_CODE_VERIFIER,
                    ssoRedirectUri = SSO_REDIRECT_URI,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns GetTokenResponseJson
            .TwoFactorRequired(
                authMethodsData = TWO_FACTOR_AUTH_METHODS_DATA,
                captchaToken = null,
                ssoToken = null,
            )
            .asSuccess()

        val firstResult = repository.login(
            email = EMAIL,
            ssoCode = SSO_CODE,
            ssoCodeVerifier = SSO_CODE_VERIFIER,
            ssoRedirectUri = SSO_REDIRECT_URI,
            captchaToken = null,
            organizationIdentifier = ORGANIZATION_IDENTIFIER,
        )
        assertEquals(LoginResult.TwoFactorRequired, firstResult)
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.SingleSignOn(
                    ssoCode = SSO_CODE,
                    ssoCodeVerifier = SSO_CODE_VERIFIER,
                    ssoRedirectUri = SSO_REDIRECT_URI,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }

        // Login with two factor data.
        val successResponse = GET_TOKEN_RESPONSE_SUCCESS.copy(
            twoFactorToken = "twoFactorTokenToStore",
        )
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.SingleSignOn(
                    ssoCode = SSO_CODE,
                    ssoCodeVerifier = SSO_CODE_VERIFIER,
                    ssoRedirectUri = SSO_REDIRECT_URI,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
                twoFactorData = TWO_FACTOR_DATA,
            )
        } returns successResponse.asSuccess()
        coEvery { vaultRepository.syncIfNecessary() } just runs
        every {
            successResponse.toUserState(
                previousUserState = null,
                environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
            )
        } returns SINGLE_USER_STATE_1
        val finalResult = repository.login(
            email = EMAIL,
            password = null,
            twoFactorData = TWO_FACTOR_DATA,
            captchaToken = null,
        )
        assertEquals(LoginResult.Success, finalResult)
        assertNull(repository.twoFactorResponse)
        fakeAuthDiskSource.assertTwoFactorToken(
            email = EMAIL,
            twoFactorToken = "twoFactorTokenToStore",
        )
    }

    @Test
    fun `SSO login uses remembered two factor tokens`() = runTest {
        fakeAuthDiskSource.storeTwoFactorToken(EMAIL, "storedTwoFactorToken")
        val rememberedTwoFactorData = TwoFactorDataModel(
            code = "storedTwoFactorToken",
            method = TwoFactorAuthMethod.REMEMBER.value.toString(),
            remember = false,
        )
        val successResponse = GET_TOKEN_RESPONSE_SUCCESS
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.SingleSignOn(
                    ssoCode = SSO_CODE,
                    ssoCodeVerifier = SSO_CODE_VERIFIER,
                    ssoRedirectUri = SSO_REDIRECT_URI,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
                twoFactorData = rememberedTwoFactorData,
            )
        } returns successResponse.asSuccess()
        coEvery { vaultRepository.syncIfNecessary() } just runs
        every {
            GET_TOKEN_RESPONSE_SUCCESS.toUserState(
                previousUserState = null,
                environmentUrlData = EnvironmentUrlDataJson.DEFAULT_US,
            )
        } returns SINGLE_USER_STATE_1
        val result = repository.login(
            email = EMAIL,
            ssoCode = SSO_CODE,
            ssoCodeVerifier = SSO_CODE_VERIFIER,
            ssoRedirectUri = SSO_REDIRECT_URI,
            captchaToken = null,
            organizationIdentifier = ORGANIZATION_IDENTIFIER,
        )
        assertEquals(LoginResult.Success, result)
        assertEquals(AuthState.Authenticated(ACCESS_TOKEN), repository.authStateFlow.value)
        fakeAuthDiskSource.assertPrivateKey(
            userId = USER_ID_1,
            privateKey = "privateKey",
        )
        fakeAuthDiskSource.assertUserKey(
            userId = USER_ID_1,
            userKey = "key",
        )
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.SingleSignOn(
                    ssoCode = SSO_CODE,
                    ssoCodeVerifier = SSO_CODE_VERIFIER,
                    ssoRedirectUri = SSO_REDIRECT_URI,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
                twoFactorData = rememberedTwoFactorData,
            )
            vaultRepository.syncIfNecessary()
        }
        assertEquals(
            SINGLE_USER_STATE_1,
            fakeAuthDiskSource.userState,
        )
        verify { settingsRepository.setDefaultsIfNecessary(userId = USER_ID_1) }
    }

    @Test
    fun `register check data breaches error should still return register success`() = runTest {
        coEvery {
            haveIBeenPwnedService.hasPasswordBeenBreached(PASSWORD)
        } returns Throwable().asFailure()
        coEvery {
            accountsService.register(
                body = RegisterRequestJson(
                    email = EMAIL,
                    masterPasswordHash = PASSWORD_HASH,
                    masterPasswordHint = null,
                    captchaResponse = null,
                    key = ENCRYPTED_USER_KEY,
                    keys = RegisterRequestJson.Keys(
                        publicKey = PUBLIC_KEY,
                        encryptedPrivateKey = PRIVATE_KEY,
                    ),
                    kdfType = KdfTypeJson.PBKDF2_SHA256,
                    kdfIterations = DEFAULT_KDF_ITERATIONS.toUInt(),
                ),
            )
        } returns RegisterResponseJson.Success(captchaBypassToken = CAPTCHA_KEY).asSuccess()

        val result = repository.register(
            email = EMAIL,
            masterPassword = PASSWORD,
            masterPasswordHint = null,
            captchaToken = null,
            shouldCheckDataBreaches = true,
        )
        assertEquals(RegisterResult.Success(CAPTCHA_KEY), result)
    }

    @Test
    fun `register check data breaches found should return DataBreachFound`() = runTest {
        coEvery {
            haveIBeenPwnedService.hasPasswordBeenBreached(PASSWORD)
        } returns true.asSuccess()

        val result = repository.register(
            email = EMAIL,
            masterPassword = PASSWORD,
            masterPasswordHint = null,
            captchaToken = null,
            shouldCheckDataBreaches = true,
        )
        assertEquals(RegisterResult.DataBreachFound, result)
    }

    @Test
    fun `register check data breaches Success should return Success`() = runTest {
        coEvery {
            haveIBeenPwnedService.hasPasswordBeenBreached(PASSWORD)
        } returns false.asSuccess()
        coEvery {
            accountsService.register(
                body = RegisterRequestJson(
                    email = EMAIL,
                    masterPasswordHash = PASSWORD_HASH,
                    masterPasswordHint = null,
                    captchaResponse = null,
                    key = ENCRYPTED_USER_KEY,
                    keys = RegisterRequestJson.Keys(
                        publicKey = PUBLIC_KEY,
                        encryptedPrivateKey = PRIVATE_KEY,
                    ),
                    kdfType = KdfTypeJson.PBKDF2_SHA256,
                    kdfIterations = DEFAULT_KDF_ITERATIONS.toUInt(),
                ),
            )
        } returns RegisterResponseJson.Success(captchaBypassToken = CAPTCHA_KEY).asSuccess()

        val result = repository.register(
            email = EMAIL,
            masterPassword = PASSWORD,
            masterPasswordHint = null,
            captchaToken = null,
            shouldCheckDataBreaches = true,
        )
        assertEquals(RegisterResult.Success(CAPTCHA_KEY), result)
        coVerify { haveIBeenPwnedService.hasPasswordBeenBreached(PASSWORD) }
    }

    @Test
    fun `register Success should return Success`() = runTest {
        coEvery { accountsService.preLogin(EMAIL) } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            accountsService.register(
                body = RegisterRequestJson(
                    email = EMAIL,
                    masterPasswordHash = PASSWORD_HASH,
                    masterPasswordHint = null,
                    captchaResponse = null,
                    key = ENCRYPTED_USER_KEY,
                    keys = RegisterRequestJson.Keys(
                        publicKey = PUBLIC_KEY,
                        encryptedPrivateKey = PRIVATE_KEY,
                    ),
                    kdfType = KdfTypeJson.PBKDF2_SHA256,
                    kdfIterations = DEFAULT_KDF_ITERATIONS.toUInt(),
                ),
            )
        } returns RegisterResponseJson.Success(captchaBypassToken = CAPTCHA_KEY).asSuccess()

        val result = repository.register(
            email = EMAIL,
            masterPassword = PASSWORD,
            masterPasswordHint = null,
            captchaToken = null,
            shouldCheckDataBreaches = false,
        )
        assertEquals(RegisterResult.Success(CAPTCHA_KEY), result)
    }

    @Test
    fun `register returns CaptchaRequired captchaKeys empty should return Error no message`() =
        runTest {
            coEvery { accountsService.preLogin(EMAIL) } returns PRE_LOGIN_SUCCESS.asSuccess()
            coEvery {
                accountsService.register(
                    body = RegisterRequestJson(
                        email = EMAIL,
                        masterPasswordHash = PASSWORD_HASH,
                        masterPasswordHint = null,
                        captchaResponse = null,
                        key = ENCRYPTED_USER_KEY,
                        keys = RegisterRequestJson.Keys(
                            publicKey = PUBLIC_KEY,
                            encryptedPrivateKey = PRIVATE_KEY,
                        ),
                        kdfType = KdfTypeJson.PBKDF2_SHA256,
                        kdfIterations = DEFAULT_KDF_ITERATIONS.toUInt(),
                    ),
                )
            } returns RegisterResponseJson
                .CaptchaRequired(
                    validationErrors = RegisterResponseJson
                        .CaptchaRequired
                        .ValidationErrors(
                            captchaKeys = emptyList(),
                        ),
                )
                .asSuccess()

            val result = repository.register(
                email = EMAIL,
                masterPassword = PASSWORD,
                masterPasswordHint = null,
                captchaToken = null,
                shouldCheckDataBreaches = false,
            )
            assertEquals(RegisterResult.Error(errorMessage = null), result)
        }

    @Test
    fun `register returns CaptchaRequired captchaKeys should return CaptchaRequired`() =
        runTest {
            coEvery { accountsService.preLogin(EMAIL) } returns PRE_LOGIN_SUCCESS.asSuccess()
            coEvery {
                accountsService.register(
                    body = RegisterRequestJson(
                        email = EMAIL,
                        masterPasswordHash = PASSWORD_HASH,
                        masterPasswordHint = null,
                        captchaResponse = null,
                        key = ENCRYPTED_USER_KEY,
                        keys = RegisterRequestJson.Keys(
                            publicKey = PUBLIC_KEY,
                            encryptedPrivateKey = PRIVATE_KEY,
                        ),
                        kdfType = KdfTypeJson.PBKDF2_SHA256,
                        kdfIterations = DEFAULT_KDF_ITERATIONS.toUInt(),
                    ),
                )
            } returns RegisterResponseJson
                .CaptchaRequired(
                    validationErrors = RegisterResponseJson
                        .CaptchaRequired
                        .ValidationErrors(
                            captchaKeys = listOf(CAPTCHA_KEY),
                        ),
                )
                .asSuccess()

            val result = repository.register(
                email = EMAIL,
                masterPassword = PASSWORD,
                masterPasswordHint = null,
                captchaToken = null,
                shouldCheckDataBreaches = false,
            )
            assertEquals(RegisterResult.CaptchaRequired(captchaId = CAPTCHA_KEY), result)
        }

    @Test
    fun `register Failure should return Error with no message`() = runTest {
        coEvery { accountsService.preLogin(EMAIL) } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            accountsService.register(
                body = RegisterRequestJson(
                    email = EMAIL,
                    masterPasswordHash = PASSWORD_HASH,
                    masterPasswordHint = null,
                    captchaResponse = null,
                    key = ENCRYPTED_USER_KEY,
                    keys = RegisterRequestJson.Keys(
                        publicKey = PUBLIC_KEY,
                        encryptedPrivateKey = PRIVATE_KEY,
                    ),
                    kdfType = KdfTypeJson.PBKDF2_SHA256,
                    kdfIterations = DEFAULT_KDF_ITERATIONS.toUInt(),
                ),
            )
        } returns RuntimeException().asFailure()

        val result = repository.register(
            email = EMAIL,
            masterPassword = PASSWORD,
            masterPasswordHint = null,
            captchaToken = null,
            shouldCheckDataBreaches = false,
        )
        assertEquals(RegisterResult.Error(errorMessage = null), result)
    }

    @Test
    fun `register returns Invalid should return Error with invalid message`() = runTest {
        coEvery { accountsService.preLogin(EMAIL) } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            accountsService.register(
                body = RegisterRequestJson(
                    email = EMAIL,
                    masterPasswordHash = PASSWORD_HASH,
                    masterPasswordHint = null,
                    captchaResponse = null,
                    key = ENCRYPTED_USER_KEY,
                    keys = RegisterRequestJson.Keys(
                        publicKey = PUBLIC_KEY,
                        encryptedPrivateKey = PRIVATE_KEY,
                    ),
                    kdfType = KdfTypeJson.PBKDF2_SHA256,
                    kdfIterations = DEFAULT_KDF_ITERATIONS.toUInt(),
                ),
            )
        } returns RegisterResponseJson.Invalid("message", mapOf()).asSuccess()

        val result = repository.register(
            email = EMAIL,
            masterPassword = PASSWORD,
            masterPasswordHint = null,
            captchaToken = null,
            shouldCheckDataBreaches = false,
        )
        assertEquals(RegisterResult.Error(errorMessage = "message"), result)
    }

    @Test
    fun `register returns Invalid should return Error with first message in map`() = runTest {
        coEvery { accountsService.preLogin(EMAIL) } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            accountsService.register(
                body = RegisterRequestJson(
                    email = EMAIL,
                    masterPasswordHash = PASSWORD_HASH,
                    masterPasswordHint = null,
                    captchaResponse = null,
                    key = ENCRYPTED_USER_KEY,
                    keys = RegisterRequestJson.Keys(
                        publicKey = PUBLIC_KEY,
                        encryptedPrivateKey = PRIVATE_KEY,
                    ),
                    kdfType = KdfTypeJson.PBKDF2_SHA256,
                    kdfIterations = DEFAULT_KDF_ITERATIONS.toUInt(),
                ),
            )
        } returns RegisterResponseJson
            .Invalid(
                message = "message",
                validationErrors = mapOf("" to listOf("expected")),
            )
            .asSuccess()

        val result = repository.register(
            email = EMAIL,
            masterPassword = PASSWORD,
            masterPasswordHint = null,
            captchaToken = null,
            shouldCheckDataBreaches = false,
        )
        assertEquals(RegisterResult.Error(errorMessage = "expected"), result)
    }

    @Test
    fun `register returns Error body should return Error with message`() = runTest {
        coEvery { accountsService.preLogin(EMAIL) } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            accountsService.register(
                body = RegisterRequestJson(
                    email = EMAIL,
                    masterPasswordHash = PASSWORD_HASH,
                    masterPasswordHint = null,
                    captchaResponse = null,
                    key = ENCRYPTED_USER_KEY,
                    keys = RegisterRequestJson.Keys(
                        publicKey = PUBLIC_KEY,
                        encryptedPrivateKey = PRIVATE_KEY,
                    ),
                    kdfType = KdfTypeJson.PBKDF2_SHA256,
                    kdfIterations = DEFAULT_KDF_ITERATIONS.toUInt(),
                ),
            )
        } returns RegisterResponseJson.Error(message = "message").asSuccess()

        val result = repository.register(
            email = EMAIL,
            masterPassword = PASSWORD,
            masterPasswordHint = null,
            captchaToken = null,
            shouldCheckDataBreaches = false,
        )
        assertEquals(RegisterResult.Error(errorMessage = "message"), result)
    }

    @Test
    fun `resetPassword Success should return Success`() = runTest {
        val currentPassword = "currentPassword"
        val currentPasswordHash = "hashedCurrentPassword"
        val newPassword = "newPassword"
        val newPasswordHash = "newPasswordHash"
        val newKey = "newKey"
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        coEvery {
            authSdkSource.hashPassword(
                email = ACCOUNT_1.profile.email,
                password = currentPassword,
                kdf = ACCOUNT_1.profile.toSdkParams(),
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
        } returns currentPasswordHash.asSuccess()
        coEvery {
            vaultSdkSource.updatePassword(
                userId = ACCOUNT_1.profile.userId,
                newPassword = newPassword,
            )
        } returns UpdatePasswordResponse(
            passwordHash = newPasswordHash,
            newKey = newKey,
        )
            .asSuccess()
        coEvery {
            accountsService.resetPassword(
                body = ResetPasswordRequestJson(
                    currentPasswordHash = currentPasswordHash,
                    newPasswordHash = newPasswordHash,
                    passwordHint = null,
                    key = newKey,
                ),
            )
        } returns Unit.asSuccess()
        coEvery {
            authSdkSource.hashPassword(
                email = ACCOUNT_1.profile.email,
                password = newPassword,
                kdf = ACCOUNT_1.profile.toSdkParams(),
                purpose = HashPurpose.LOCAL_AUTHORIZATION,
            )
        } returns newPasswordHash.asSuccess()

        val result = repository.resetPassword(
            currentPassword = currentPassword,
            newPassword = newPassword,
            passwordHint = null,
        )

        assertEquals(
            ResetPasswordResult.Success,
            result,
        )
        coVerify {
            authSdkSource.hashPassword(
                email = ACCOUNT_1.profile.email,
                password = currentPassword,
                kdf = ACCOUNT_1.profile.toSdkParams(),
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
            vaultSdkSource.updatePassword(
                userId = ACCOUNT_1.profile.userId,
                newPassword = newPassword,
            )
            accountsService.resetPassword(
                body = ResetPasswordRequestJson(
                    currentPasswordHash = currentPasswordHash,
                    newPasswordHash = newPasswordHash,
                    passwordHint = null,
                    key = newKey,
                ),
            )
        }
        fakeAuthDiskSource.assertMasterPasswordHash(
            userId = USER_ID_1,
            passwordHash = newPasswordHash,
        )
    }

    @Test
    fun `resetPassword Failure should return Error`() = runTest {
        val currentPassword = "currentPassword"
        val currentPasswordHash = "hashedCurrentPassword"
        val newPassword = "newPassword"
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        coEvery {
            authSdkSource.hashPassword(
                email = ACCOUNT_1.profile.email,
                password = currentPassword,
                kdf = ACCOUNT_1.profile.toSdkParams(),
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
        } returns currentPasswordHash.asSuccess()
        coEvery {
            vaultSdkSource.updatePassword(
                userId = ACCOUNT_1.profile.userId,
                newPassword = newPassword,
            )
        } returns Throwable("Fail").asFailure()

        val result = repository.resetPassword(
            currentPassword = currentPassword,
            newPassword = newPassword,
            passwordHint = null,
        )

        assertEquals(
            ResetPasswordResult.Error,
            result,
        )
        coVerify {
            authSdkSource.hashPassword(
                email = ACCOUNT_1.profile.email,
                password = currentPassword,
                kdf = ACCOUNT_1.profile.toSdkParams(),
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
            vaultSdkSource.updatePassword(
                userId = ACCOUNT_1.profile.userId,
                newPassword = newPassword,
            )
        }
    }

    @Test
    fun `setPassword without active account should return Error`() = runTest {
        fakeAuthDiskSource.userState = null

        val result = repository.setPassword(
            organizationIdentifier = "organizationId",
            password = "password",
            passwordHint = "passwordHint",
        )

        assertEquals(SetPasswordResult.Error, result)
        fakeAuthDiskSource.assertMasterPasswordHash(userId = USER_ID_1, passwordHash = null)
        fakeAuthDiskSource.assertPrivateKey(userId = USER_ID_1, privateKey = null)
        fakeAuthDiskSource.assertUserKey(userId = USER_ID_1, userKey = null)
    }

    @Test
    fun `setPassword with authSdkSource hashPassword failure should return Error`() = runTest {
        val password = "password"
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        coEvery {
            authSdkSource.hashPassword(
                email = EMAIL,
                password = password,
                kdf = SINGLE_USER_STATE_1.activeAccount.profile.toSdkParams(),
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
        } returns Throwable("Fail").asFailure()

        val result = repository.setPassword(
            organizationIdentifier = "organizationId",
            password = password,
            passwordHint = "passwordHint",
        )

        assertEquals(SetPasswordResult.Error, result)
        fakeAuthDiskSource.assertMasterPasswordHash(userId = USER_ID_1, passwordHash = null)
        fakeAuthDiskSource.assertPrivateKey(userId = USER_ID_1, privateKey = null)
        fakeAuthDiskSource.assertUserKey(userId = USER_ID_1, userKey = null)
    }

    @Test
    fun `setPassword with authSdkSource makeRegisterKeys failure should return Error`() = runTest {
        val password = "password"
        val passwordHash = "passwordHash"
        val kdf = SINGLE_USER_STATE_1.activeAccount.profile.toSdkParams()
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        coEvery {
            authSdkSource.hashPassword(
                email = EMAIL,
                password = password,
                kdf = kdf,
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
        } returns passwordHash.asSuccess()
        coEvery {
            authSdkSource.makeRegisterKeys(
                email = EMAIL,
                password = password,
                kdf = kdf,
            )
        } returns Throwable("Fail").asFailure()

        val result = repository.setPassword(
            organizationIdentifier = "organizationId",
            password = password,
            passwordHint = "passwordHint",
        )

        assertEquals(SetPasswordResult.Error, result)
        fakeAuthDiskSource.assertMasterPasswordHash(userId = USER_ID_1, passwordHash = null)
        fakeAuthDiskSource.assertPrivateKey(userId = USER_ID_1, privateKey = null)
        fakeAuthDiskSource.assertUserKey(userId = USER_ID_1, userKey = null)
    }

    @Test
    fun `setPassword with accountsService setPassword failure should return Error`() = runTest {
        val password = "password"
        val passwordHash = "passwordHash"
        val passwordHint = "passwordHint"
        val organizationId = ORGANIZATION_IDENTIFIER
        val encryptedUserKey = "encryptedUserKey"
        val privateRsaKey = "privateRsaKey"
        val publicRsaKey = "publicRsaKey"
        val profile = SINGLE_USER_STATE_1.activeAccount.profile
        val kdf = profile.toSdkParams()
        val registerKeyResponse = RegisterKeyResponse(
            masterPasswordHash = passwordHash,
            encryptedUserKey = encryptedUserKey,
            keys = RsaKeyPair(public = publicRsaKey, private = privateRsaKey),
        )
        val setPasswordRequestJson = SetPasswordRequestJson(
            passwordHash = passwordHash,
            passwordHint = passwordHint,
            organizationIdentifier = organizationId,
            kdfIterations = profile.kdfIterations,
            kdfMemory = profile.kdfMemory,
            kdfParallelism = profile.kdfParallelism,
            kdfType = profile.kdfType,
            key = encryptedUserKey,
            keys = RegisterRequestJson.Keys(
                publicKey = publicRsaKey,
                encryptedPrivateKey = privateRsaKey,
            ),
        )
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        coEvery {
            authSdkSource.hashPassword(
                email = EMAIL,
                password = password,
                kdf = kdf,
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
        } returns passwordHash.asSuccess()
        coEvery {
            authSdkSource.makeRegisterKeys(email = EMAIL, password = password, kdf = kdf)
        } returns registerKeyResponse.asSuccess()
        coEvery {
            accountsService.setPassword(body = setPasswordRequestJson)
        } returns Throwable("Fail").asFailure()

        val result = repository.setPassword(
            organizationIdentifier = organizationId,
            password = password,
            passwordHint = passwordHint,
        )

        assertEquals(SetPasswordResult.Error, result)
        fakeAuthDiskSource.assertMasterPasswordHash(userId = USER_ID_1, passwordHash = null)
        fakeAuthDiskSource.assertPrivateKey(userId = USER_ID_1, privateKey = null)
        fakeAuthDiskSource.assertUserKey(userId = USER_ID_1, userKey = null)
    }

    @Test
    fun `setPassword with accountsService setPassword success should return Success`() = runTest {
        val password = "password"
        val passwordHash = "passwordHash"
        val passwordHint = "passwordHint"
        val organizationIdentifier = ORGANIZATION_IDENTIFIER
        val organizationId = "orgId"
        val encryptedUserKey = "encryptedUserKey"
        val privateRsaKey = "privateRsaKey"
        val publicRsaKey = "publicRsaKey"
        val publicOrgKey = "publicOrgKey"
        val resetPasswordKey = "resetPasswordKey"
        val profile = SINGLE_USER_STATE_1.activeAccount.profile
        val kdf = profile.toSdkParams()
        val registerKeyResponse = RegisterKeyResponse(
            masterPasswordHash = passwordHash,
            encryptedUserKey = encryptedUserKey,
            keys = RsaKeyPair(public = publicRsaKey, private = privateRsaKey),
        )
        val setPasswordRequestJson = SetPasswordRequestJson(
            passwordHash = passwordHash,
            passwordHint = passwordHint,
            organizationIdentifier = organizationIdentifier,
            kdfIterations = profile.kdfIterations,
            kdfMemory = profile.kdfMemory,
            kdfParallelism = profile.kdfParallelism,
            kdfType = profile.kdfType,
            key = encryptedUserKey,
            keys = RegisterRequestJson.Keys(
                publicKey = publicRsaKey,
                encryptedPrivateKey = privateRsaKey,
            ),
        )
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        coEvery {
            authSdkSource.hashPassword(
                email = EMAIL,
                password = password,
                kdf = kdf,
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
        } returns passwordHash.asSuccess()
        coEvery {
            authSdkSource.makeRegisterKeys(email = EMAIL, password = password, kdf = kdf)
        } returns registerKeyResponse.asSuccess()
        coEvery {
            accountsService.setPassword(body = setPasswordRequestJson)
        } returns Unit.asSuccess()
        coEvery {
            organizationService.getOrganizationAutoEnrollStatus(organizationIdentifier)
        } returns OrganizationAutoEnrollStatusResponseJson(
            organizationId = organizationId,
            isResetPasswordEnabled = true,
        )
            .asSuccess()
        coEvery {
            organizationService.getOrganizationKeys(organizationId)
        } returns OrganizationKeysResponseJson(
            privateKey = "",
            publicKey = publicOrgKey,
        )
            .asSuccess()
        coEvery {
            organizationService.organizationResetPasswordEnroll(
                organizationId = organizationId,
                userId = profile.userId,
                passwordHash = passwordHash,
                resetPasswordKey = resetPasswordKey,
            )
        } returns Unit.asSuccess()
        coEvery {
            vaultSdkSource.getResetPasswordKey(
                orgPublicKey = publicOrgKey,
                userId = profile.userId,
            )
        } returns resetPasswordKey.asSuccess()
        coEvery {
            vaultRepository.unlockVaultWithMasterPassword(password)
        } returns VaultUnlockResult.Success

        val result = repository.setPassword(
            organizationIdentifier = organizationIdentifier,
            password = password,
            passwordHint = passwordHint,
        )

        assertEquals(SetPasswordResult.Success, result)
        fakeAuthDiskSource.assertMasterPasswordHash(userId = USER_ID_1, passwordHash = passwordHash)
        fakeAuthDiskSource.assertPrivateKey(userId = USER_ID_1, privateKey = privateRsaKey)
        fakeAuthDiskSource.assertUserKey(userId = USER_ID_1, userKey = encryptedUserKey)
        fakeAuthDiskSource.assertUserState(SINGLE_USER_STATE_1_WITH_PASS)
        coVerify {
            authSdkSource.hashPassword(
                email = EMAIL,
                password = password,
                kdf = kdf,
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
            authSdkSource.makeRegisterKeys(email = EMAIL, password = password, kdf = kdf)
            accountsService.setPassword(body = setPasswordRequestJson)
            organizationService.getOrganizationAutoEnrollStatus(organizationIdentifier)
            organizationService.getOrganizationKeys(organizationId)
            organizationService.organizationResetPasswordEnroll(
                organizationId = organizationId,
                userId = profile.userId,
                passwordHash = passwordHash,
                resetPasswordKey = resetPasswordKey,
            )
            vaultRepository.unlockVaultWithMasterPassword(password)
            vaultSdkSource.getResetPasswordKey(
                orgPublicKey = publicOrgKey,
                userId = profile.userId,
            )
        }
    }

    @Test
    fun `setPassword with unlockVaultWithMasterPassword error should return Failure`() = runTest {
        val password = "password"
        val passwordHash = "passwordHash"
        val passwordHint = "passwordHint"
        val organizationIdentifier = ORGANIZATION_IDENTIFIER
        val organizationId = "orgId"
        val encryptedUserKey = "encryptedUserKey"
        val privateRsaKey = "privateRsaKey"
        val publicRsaKey = "publicRsaKey"
        val publicOrgKey = "publicOrgKey"
        val resetPasswordKey = "resetPasswordKey"
        val profile = SINGLE_USER_STATE_1.activeAccount.profile
        val kdf = profile.toSdkParams()
        val registerKeyResponse = RegisterKeyResponse(
            masterPasswordHash = passwordHash,
            encryptedUserKey = encryptedUserKey,
            keys = RsaKeyPair(public = publicRsaKey, private = privateRsaKey),
        )
        val setPasswordRequestJson = SetPasswordRequestJson(
            passwordHash = passwordHash,
            passwordHint = passwordHint,
            organizationIdentifier = organizationIdentifier,
            kdfIterations = profile.kdfIterations,
            kdfMemory = profile.kdfMemory,
            kdfParallelism = profile.kdfParallelism,
            kdfType = profile.kdfType,
            key = encryptedUserKey,
            keys = RegisterRequestJson.Keys(
                publicKey = publicRsaKey,
                encryptedPrivateKey = privateRsaKey,
            ),
        )
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        coEvery {
            authSdkSource.hashPassword(
                email = EMAIL,
                password = password,
                kdf = kdf,
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
        } returns passwordHash.asSuccess()
        coEvery {
            authSdkSource.makeRegisterKeys(email = EMAIL, password = password, kdf = kdf)
        } returns registerKeyResponse.asSuccess()
        coEvery {
            accountsService.setPassword(body = setPasswordRequestJson)
        } returns Unit.asSuccess()
        coEvery {
            vaultRepository.unlockVaultWithMasterPassword(password)
        } returns VaultUnlockResult.GenericError

        val result = repository.setPassword(
            organizationIdentifier = organizationIdentifier,
            password = password,
            passwordHint = passwordHint,
        )

        assertEquals(SetPasswordResult.Error, result)
        fakeAuthDiskSource.assertMasterPasswordHash(userId = USER_ID_1, passwordHash = null)
        fakeAuthDiskSource.assertPrivateKey(userId = USER_ID_1, privateKey = privateRsaKey)
        fakeAuthDiskSource.assertUserKey(userId = USER_ID_1, userKey = encryptedUserKey)
        fakeAuthDiskSource.assertUserState(SINGLE_USER_STATE_1)
        coVerify {
            authSdkSource.hashPassword(
                email = EMAIL,
                password = password,
                kdf = kdf,
                purpose = HashPurpose.SERVER_AUTHORIZATION,
            )
            authSdkSource.makeRegisterKeys(email = EMAIL, password = password, kdf = kdf)
            accountsService.setPassword(body = setPasswordRequestJson)
            vaultRepository.unlockVaultWithMasterPassword(password)
        }
        coVerify(exactly = 0) {
            organizationService.getOrganizationAutoEnrollStatus(organizationIdentifier)
            organizationService.getOrganizationKeys(organizationId)
            organizationService.organizationResetPasswordEnroll(
                organizationId = organizationId,
                userId = profile.userId,
                passwordHash = passwordHash,
                resetPasswordKey = resetPasswordKey,
            )
            vaultSdkSource.getResetPasswordKey(
                orgPublicKey = publicOrgKey,
                userId = profile.userId,
            )
        }
    }

    @Test
    fun `passwordHintRequest with valid email should return Success`() = runTest {
        val email = "valid@example.com"
        coEvery {
            accountsService.requestPasswordHint(email)
        } returns PasswordHintResponseJson.Success.asSuccess()

        val result = repository.passwordHintRequest(email)

        assertEquals(PasswordHintResult.Success, result)
    }

    @Test
    fun `passwordHintRequest with error response should return Error`() = runTest {
        val email = "error@example.com"
        val errorMessage = "Error message"
        coEvery {
            accountsService.requestPasswordHint(email)
        } returns PasswordHintResponseJson.Error(errorMessage).asSuccess()

        val result = repository.passwordHintRequest(email)

        assertEquals(PasswordHintResult.Error(errorMessage), result)
    }

    @Test
    fun `passwordHintRequest with failure should return Error with null message`() = runTest {
        val email = "failure@example.com"
        coEvery {
            accountsService.requestPasswordHint(email)
        } returns RuntimeException("Network error").asFailure()

        val result = repository.passwordHintRequest(email)

        assertEquals(PasswordHintResult.Error(null), result)
    }

    @Test
    fun `setCaptchaCallbackToken should change the value of captchaTokenResultFlow`() = runTest {
        repository.captchaTokenResultFlow.test {
            repository.setCaptchaCallbackTokenResult(CaptchaCallbackTokenResult.Success("mockk"))
            assertEquals(
                CaptchaCallbackTokenResult.Success("mockk"),
                awaitItem(),
            )
        }
    }

    @Test
    fun `setDuoCallbackToken should change the value of duoTokenResultFlow`() = runTest {
        repository.duoTokenResultFlow.test {
            repository.setDuoCallbackTokenResult(DuoCallbackTokenResult.Success("mockk"))
            assertEquals(
                DuoCallbackTokenResult.Success("mockk"),
                awaitItem(),
            )
        }
    }

    @Test
    fun `setSsoCallbackResult should change the value of ssoCallbackResultFlow`() = runTest {
        repository.ssoCallbackResultFlow.test {
            repository.setSsoCallbackResult(
                SsoCallbackResult.Success(state = "mockk_state", code = "mockk_code"),
            )
            assertEquals(
                SsoCallbackResult.Success(state = "mockk_state", code = "mockk_code"),
                awaitItem(),
            )
        }
    }

    @Test
    fun `setYubiKeyResult should change the value of yubiKeyResultFlow`() = runTest {
        val yubiKeyResult = YubiKeyResult("mockk")
        repository.yubiKeyResultFlow.test {
            repository.setYubiKeyResult(yubiKeyResult)
            assertEquals(yubiKeyResult, awaitItem())
        }
    }

    @Test
    fun `getOrganizationDomainSsoDetails Failure should return Failure `() = runTest {
        val email = "test@gmail.com"
        val throwable = Throwable()
        coEvery {
            organizationService.getOrganizationDomainSsoDetails(email)
        } returns throwable.asFailure()
        val result = repository.getOrganizationDomainSsoDetails(email)
        assertEquals(OrganizationDomainSsoDetailsResult.Failure, result)
    }

    @Test
    fun `getOrganizationDomainSsoDetails Success should return Success`() = runTest {
        val email = "test@gmail.com"
        coEvery {
            organizationService.getOrganizationDomainSsoDetails(email)
        } returns OrganizationDomainSsoDetailsResponseJson(
            isSsoAvailable = true,
            organizationIdentifier = "Test Org",
            domainName = "bitwarden.com",
            isSsoRequired = false,
            verifiedDate = ZonedDateTime.parse("2024-09-13T00:00Z"),
        )
            .asSuccess()
        val result = repository.getOrganizationDomainSsoDetails(email)
        assertEquals(
            OrganizationDomainSsoDetailsResult.Success(
                isSsoAvailable = true,
                organizationIdentifier = "Test Org",
            ),
            result,
        )
    }

    @Test
    fun `prevalidateSso Failure should return Failure `() = runTest {
        val organizationId = "organizationid"
        val throwable = Throwable()
        coEvery {
            identityService.prevalidateSso(organizationId)
        } returns throwable.asFailure()
        val result = repository.prevalidateSso(organizationId)
        assertEquals(PrevalidateSsoResult.Failure, result)
    }

    @Test
    fun `prevalidateSso Success with a blank token should return Failure`() = runTest {
        val organizationId = "organizationid"
        coEvery {
            identityService.prevalidateSso(organizationId)
        } returns PrevalidateSsoResponseJson(token = "").asSuccess()
        val result = repository.prevalidateSso(organizationId)
        assertEquals(PrevalidateSsoResult.Failure, result)
    }

    @Test
    fun `prevalidateSso Success with a valid token should return Success`() = runTest {
        val organizationId = "organizationid"
        coEvery {
            identityService.prevalidateSso(organizationId)
        } returns PrevalidateSsoResponseJson(token = "token").asSuccess()
        val result = repository.prevalidateSso(organizationId)
        assertEquals(PrevalidateSsoResult.Success(token = "token"), result)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `logout for an inactive account should call logout on the UserLogoutManager`() {
        val userId = USER_ID_2
        fakeAuthDiskSource.userState = MULTI_USER_STATE

        repository.logout(userId = userId)

        verify { userLogoutManager.logout(userId = userId) }
    }

    @Test
    fun `resendVerificationCodeEmail uses cached request data to make api call`() = runTest {
        // Attempt a normal login with a two factor error first, so that the necessary
        // data will be cached.
        coEvery { accountsService.preLogin(EMAIL) } returns PRE_LOGIN_SUCCESS.asSuccess()
        coEvery {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        } returns GetTokenResponseJson
            .TwoFactorRequired(
                authMethodsData = TWO_FACTOR_AUTH_METHODS_DATA,
                captchaToken = null,
                ssoToken = null,
            )
            .asSuccess()
        val firstResult = repository.login(email = EMAIL, password = PASSWORD, captchaToken = null)
        assertEquals(LoginResult.TwoFactorRequired, firstResult)
        coVerify { accountsService.preLogin(email = EMAIL) }
        coVerify {
            identityService.getToken(
                email = EMAIL,
                authModel = IdentityTokenAuthModel.MasterPassword(
                    username = EMAIL,
                    password = PASSWORD_HASH,
                ),
                captchaToken = null,
                uniqueAppId = UNIQUE_APP_ID,
            )
        }

        // Resend the verification code email.
        coEvery {
            accountsService.resendVerificationCodeEmail(
                body = ResendEmailRequestJson(
                    deviceIdentifier = UNIQUE_APP_ID,
                    email = EMAIL,
                    passwordHash = PASSWORD_HASH,
                    ssoToken = null,
                ),
            )
        } returns Unit.asSuccess()
        val resendEmailResult = repository.resendVerificationCodeEmail()
        assertEquals(ResendEmailResult.Success, resendEmailResult)
        coVerify {
            accountsService.resendVerificationCodeEmail(
                body = ResendEmailRequestJson(
                    deviceIdentifier = UNIQUE_APP_ID,
                    email = EMAIL,
                    passwordHash = PASSWORD_HASH,
                    ssoToken = null,
                ),
            )
        }
    }

    @Test
    fun `resendVerificationCodeEmail returns error if no request data cached`() = runTest {
        val result = repository.resendVerificationCodeEmail()
        assertEquals(ResendEmailResult.Error(message = null), result)
    }

    @Test
    fun `switchAccount when there is no saved UserState should do nothing`() {
        val updatedUserId = USER_ID_2

        fakeAuthDiskSource.userState = null
        assertNull(repository.userStateFlow.value)

        assertEquals(
            SwitchAccountResult.NoChange,
            repository.switchAccount(userId = updatedUserId),
        )

        assertNull(repository.userStateFlow.value)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `switchAccount when the given userId is the same as the current activeUserId should reset any pending account additions`() {
        val originalUserId = USER_ID_1
        val originalUserState = SINGLE_USER_STATE_1.toUserState(
            vaultState = VAULT_UNLOCK_DATA,
            userOrganizationsList = emptyList(),
            hasPendingAccountAddition = false,
            isBiometricsEnabledProvider = { false },
            vaultUnlockTypeProvider = { VaultUnlockType.MASTER_PASSWORD },
            isLoggedInProvider = { false },
            isDeviceTrustedProvider = { false },
        )
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        assertEquals(
            originalUserState,
            repository.userStateFlow.value,
        )
        repository.hasPendingAccountAddition = true

        assertEquals(
            SwitchAccountResult.NoChange,
            repository.switchAccount(userId = originalUserId),
        )

        assertEquals(
            originalUserState,
            repository.userStateFlow.value,
        )
        assertFalse(repository.hasPendingAccountAddition)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `switchAccount when the given userId does not correspond to a saved account should do nothing`() {
        val invalidId = "invalidId"
        val originalUserState = SINGLE_USER_STATE_1.toUserState(
            vaultState = VAULT_UNLOCK_DATA,
            userOrganizationsList = emptyList(),
            hasPendingAccountAddition = false,
            isBiometricsEnabledProvider = { false },
            vaultUnlockTypeProvider = { VaultUnlockType.MASTER_PASSWORD },
            isLoggedInProvider = { false },
            isDeviceTrustedProvider = { false },
        )
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        assertEquals(
            originalUserState,
            repository.userStateFlow.value,
        )

        assertEquals(
            SwitchAccountResult.NoChange,
            repository.switchAccount(userId = invalidId),
        )

        assertEquals(
            originalUserState,
            repository.userStateFlow.value,
        )
    }

    @Suppress("MaxLineLength")
    @Test
    fun `switchAccount when the userId is valid should update the current UserState and reset any pending account additions`() {
        val updatedUserId = USER_ID_2
        val originalUserState = MULTI_USER_STATE.toUserState(
            vaultState = VAULT_UNLOCK_DATA,
            userOrganizationsList = emptyList(),
            hasPendingAccountAddition = false,
            isBiometricsEnabledProvider = { false },
            vaultUnlockTypeProvider = { VaultUnlockType.MASTER_PASSWORD },
            isLoggedInProvider = { false },
            isDeviceTrustedProvider = { false },
        )
        fakeAuthDiskSource.userState = MULTI_USER_STATE
        assertEquals(
            originalUserState,
            repository.userStateFlow.value,
        )
        repository.hasPendingAccountAddition = true

        assertEquals(
            SwitchAccountResult.AccountSwitched,
            repository.switchAccount(userId = updatedUserId),
        )

        assertEquals(
            originalUserState.copy(activeUserId = updatedUserId),
            repository.userStateFlow.value,
        )
        assertFalse(repository.hasPendingAccountAddition)
    }

    @Test
    fun `updateLastActiveTime should update the last active time for the current user`() {
        val userId = USER_ID_1
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1

        assertNull(fakeAuthDiskSource.getLastActiveTimeMillis(userId = userId))

        repository.updateLastActiveTime()

        assertEquals(
            elapsedRealtimeMillis,
            fakeAuthDiskSource.getLastActiveTimeMillis(userId = userId),
        )
    }

    @Test
    fun `getIsKnownDevice should return failure when service returns failure`() = runTest {
        coEvery {
            devicesService.getIsKnownDevice(EMAIL, UNIQUE_APP_ID)
        } returns Throwable("Fail").asFailure()

        val result = repository.getIsKnownDevice(EMAIL)

        coVerify(exactly = 1) {
            devicesService.getIsKnownDevice(EMAIL, UNIQUE_APP_ID)
        }
        assertEquals(KnownDeviceResult.Error, result)
    }

    @Test
    fun `getIsKnownDevice should return success when service returns success`() = runTest {
        val isKnownDevice = true
        coEvery {
            devicesService.getIsKnownDevice(EMAIL, UNIQUE_APP_ID)
        } returns isKnownDevice.asSuccess()

        val result = repository.getIsKnownDevice(EMAIL)

        coVerify(exactly = 1) {
            devicesService.getIsKnownDevice(EMAIL, UNIQUE_APP_ID)
        }
        assertEquals(KnownDeviceResult.Success(isKnownDevice), result)
    }

    @Test
    fun `getPasswordBreachCount should return failure when service returns failure`() = runTest {
        val password = "password"
        coEvery {
            haveIBeenPwnedService.getPasswordBreachCount(password)
        } returns Throwable("Fail").asFailure()

        val result = repository.getPasswordBreachCount(password)

        coVerify(exactly = 1) {
            haveIBeenPwnedService.getPasswordBreachCount(password)
        }
        assertEquals(BreachCountResult.Error, result)
    }

    @Test
    fun `getPasswordBreachCount should return success when service returns success`() = runTest {
        val password = "password"
        val breachCount = 5
        coEvery {
            haveIBeenPwnedService.getPasswordBreachCount(password)
        } returns breachCount.asSuccess()

        val result = repository.getPasswordBreachCount(password)

        coVerify(exactly = 1) {
            haveIBeenPwnedService.getPasswordBreachCount(password)
        }
        assertEquals(BreachCountResult.Success(breachCount), result)
    }

    @Test
    fun `getPasswordStrength returns expected results for various strength levels`() = runTest {
        coEvery {
            authSdkSource.passwordStrength(any(), eq("level_0"))
        } returns LEVEL_0.asSuccess()

        coEvery {
            authSdkSource.passwordStrength(any(), eq("level_1"))
        } returns LEVEL_1.asSuccess()

        coEvery {
            authSdkSource.passwordStrength(any(), eq("level_2"))
        } returns LEVEL_2.asSuccess()

        coEvery {
            authSdkSource.passwordStrength(any(), eq("level_3"))
        } returns LEVEL_3.asSuccess()

        coEvery {
            authSdkSource.passwordStrength(any(), eq("level_4"))
        } returns LEVEL_4.asSuccess()

        assertEquals(
            PasswordStrengthResult.Success(LEVEL_0),
            repository.getPasswordStrength(EMAIL, "level_0"),
        )

        assertEquals(
            PasswordStrengthResult.Success(LEVEL_1),
            repository.getPasswordStrength(EMAIL, "level_1"),
        )

        assertEquals(
            PasswordStrengthResult.Success(LEVEL_2),
            repository.getPasswordStrength(EMAIL, "level_2"),
        )

        assertEquals(
            PasswordStrengthResult.Success(LEVEL_3),
            repository.getPasswordStrength(EMAIL, "level_3"),
        )

        assertEquals(
            PasswordStrengthResult.Success(LEVEL_4),
            repository.getPasswordStrength(EMAIL, "level_4"),
        )
    }

    @Test
    fun `validatePassword with no current user returns ValidatePasswordResult Error`() = runTest {
        val userId = "userId"
        val password = "password"
        val passwordHash = "passwordHash"
        fakeAuthDiskSource.userState = null
        coEvery {
            vaultSdkSource.validatePassword(
                userId = userId,
                password = password,
                passwordHash = passwordHash,
            )
        } returns true.asSuccess()

        val result = repository
            .validatePassword(
                password = password,
            )

        assertEquals(
            ValidatePasswordResult.Error,
            result,
        )
    }

    @Suppress("MaxLineLength")
    @Test
    fun `validatePassword with no stored password hash and no stored user key returns ValidatePasswordResult Error`() =
        runTest {
            val userId = USER_ID_1
            val password = "password"
            val passwordHash = "passwordHash"
            fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
            coEvery {
                vaultSdkSource.validatePassword(
                    userId = userId,
                    password = password,
                    passwordHash = passwordHash,
                )
            } returns true.asSuccess()

            val result = repository
                .validatePassword(
                    password = password,
                )

            assertEquals(
                ValidatePasswordResult.Error,
                result,
            )
        }

    @Test
    fun `validatePassword with sdk failure returns a ValidatePasswordResult Error`() = runTest {
        val userId = USER_ID_1
        val password = "password"
        val passwordHash = "passwordHash"
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        fakeAuthDiskSource.storeMasterPasswordHash(userId = userId, passwordHash = passwordHash)
        coEvery {
            vaultSdkSource.validatePassword(
                userId = userId,
                password = password,
                passwordHash = passwordHash,
            )
        } returns Throwable().asFailure()

        val result = repository
            .validatePassword(
                password = password,
            )

        assertEquals(
            ValidatePasswordResult.Error,
            result,
        )
    }

    @Test
    fun `validatePassword with sdk success returns a ValidatePasswordResult Success`() = runTest {
        val userId = USER_ID_1
        val password = "password"
        val passwordHash = "passwordHash"
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        fakeAuthDiskSource.storeMasterPasswordHash(userId = userId, passwordHash = passwordHash)
        coEvery {
            vaultSdkSource.validatePassword(
                userId = userId,
                password = password,
                passwordHash = passwordHash,
            )
        } returns true.asSuccess()

        val result = repository
            .validatePassword(
                password = password,
            )

        assertEquals(
            ValidatePasswordResult.Success(isValid = true),
            result,
        )
    }

    @Suppress("MaxLineLength")
    @Test
    fun `validatePassword with no stored password hash and a stored user key with sdk failure returns ValidatePasswordResult Success invalid`() =
        runTest {
            val userId = USER_ID_1
            val password = "password"
            val userKey = "userKey"
            fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
            fakeAuthDiskSource.storeUserKey(userId = userId, userKey = userKey)
            coEvery {
                vaultSdkSource.validatePasswordUserKey(
                    userId = userId,
                    password = password,
                    encryptedUserKey = userKey,
                )
            } returns Throwable("Fail").asFailure()

            val result = repository.validatePassword(password = password)

            assertEquals(ValidatePasswordResult.Success(isValid = false), result)
        }

    @Suppress("MaxLineLength")
    @Test
    fun `validatePassword with no stored password hash and a stored user key with sdk success returns ValidatePasswordResult Success valid`() =
        runTest {
            val userId = USER_ID_1
            val password = "password"
            val userKey = "userKey"
            val passwordHash = "passwordHash"
            fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
            fakeAuthDiskSource.storeUserKey(userId = userId, userKey = userKey)
            coEvery {
                vaultSdkSource.validatePasswordUserKey(
                    userId = userId,
                    password = password,
                    encryptedUserKey = userKey,
                )
            } returns passwordHash.asSuccess()

            val result = repository.validatePassword(password = password)

            assertEquals(ValidatePasswordResult.Success(isValid = true), result)
            fakeAuthDiskSource.assertMasterPasswordHash(
                userId = userId,
                passwordHash = passwordHash,
            )
        }

    @Test
    fun `logOutFlow emission for action account should call logout on the UserLogoutManager`() {
        val userId = USER_ID_1
        fakeAuthDiskSource.userState = MULTI_USER_STATE

        mutableLogoutFlow.tryEmit(NotificationLogoutData(userId = userId))

        coVerify(exactly = 1) {
            userLogoutManager.logout(userId = userId)
        }
    }

    @Test
    fun `syncOrgKeysFlow emissions should refresh access token and sync`() {
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1
        fakeAuthDiskSource.storeAccountTokens(userId = USER_ID_1, accountTokens = ACCOUNT_TOKENS_1)
        coEvery {
            identityService.refreshTokenSynchronously(REFRESH_TOKEN)
        } returns REFRESH_TOKEN_RESPONSE_JSON.asSuccess()
        every {
            REFRESH_TOKEN_RESPONSE_JSON.toUserStateJson(
                userId = USER_ID_1,
                previousUserState = SINGLE_USER_STATE_1,
            )
        } returns SINGLE_USER_STATE_1

        coEvery { vaultRepository.sync() } just runs

        mutableSyncOrgKeysFlow.tryEmit(Unit)

        coVerify(exactly = 1) {
            identityService.refreshTokenSynchronously(REFRESH_TOKEN)
            vaultRepository.sync()
        }
    }

    @Test
    fun `validatePasswordAgainstPolicy validates password against policy requirements`() = runTest {
        fakeAuthDiskSource.userState = SINGLE_USER_STATE_1

        // A helper method to set a policy with the given parameters.
        fun setPolicy(
            minLength: Int = 0,
            minComplexity: Int? = null,
            requireUpper: Boolean = false,
            requireLower: Boolean = false,
            requireNumbers: Boolean = false,
            requireSpecial: Boolean = false,
        ) {
            every {
                policyManager.getActivePolicies(type = PolicyTypeJson.MASTER_PASSWORD)
            } returns listOf(
                createMockPolicy(
                    type = PolicyTypeJson.MASTER_PASSWORD,
                    isEnabled = true,
                    data = buildJsonObject {
                        put(key = "minLength", value = minLength)
                        put(key = "minComplexity", value = minComplexity)
                        put(key = "requireUpper", value = requireUpper)
                        put(key = "requireLower", value = requireLower)
                        put(key = "requireNumbers", value = requireNumbers)
                        put(key = "requireSpecial", value = requireSpecial)
                        put(key = "enforceOnLogin", value = true)
                    },
                ),
            )
        }

        setPolicy(minLength = 10)
        assertFalse(repository.validatePasswordAgainstPolicies(password = "123"))

        val password = "simple"
        coEvery {
            authSdkSource.passwordStrength(
                email = SINGLE_USER_STATE_1.activeAccount.profile.email,
                password = password,
            )
        } returns LEVEL_0.asSuccess()
        setPolicy(minComplexity = 10)
        assertFalse(repository.validatePasswordAgainstPolicies(password = password))

        setPolicy(requireUpper = true)
        assertFalse(repository.validatePasswordAgainstPolicies(password = "lower"))

        setPolicy(requireLower = true)
        assertFalse(repository.validatePasswordAgainstPolicies(password = "UPPER"))

        setPolicy(requireNumbers = true)
        assertFalse(repository.validatePasswordAgainstPolicies(password = "letters"))

        setPolicy(requireSpecial = true)
        assertFalse(repository.validatePasswordAgainstPolicies(password = "letters"))
    }

    companion object {
        private const val UNIQUE_APP_ID = "testUniqueAppId"
        private const val EMAIL = "test@bitwarden.com"
        private const val EMAIL_2 = "test2@bitwarden.com"
        private const val PASSWORD = "password"
        private const val PASSWORD_HASH = "passwordHash"
        private const val ACCESS_TOKEN = "accessToken"
        private const val ACCESS_TOKEN_2 = "accessToken2"
        private const val REFRESH_TOKEN = "refreshToken"
        private const val REFRESH_TOKEN_2 = "refreshToken2"
        private const val CAPTCHA_KEY = "captcha"
        private const val TWO_FACTOR_CODE = "123456"
        private val TWO_FACTOR_METHOD = TwoFactorAuthMethod.EMAIL
        private const val TWO_FACTOR_REMEMBER = true
        private val TWO_FACTOR_DATA = TwoFactorDataModel(
            code = TWO_FACTOR_CODE,
            method = TWO_FACTOR_METHOD.value.toString(),
            remember = TWO_FACTOR_REMEMBER,
        )
        private const val SSO_CODE = "ssoCode"
        private const val SSO_CODE_VERIFIER = "ssoCodeVerifier"
        private const val SSO_REDIRECT_URI = "bitwarden://sso-test"
        private const val DEVICE_ACCESS_CODE = "accessCode"
        private const val DEVICE_REQUEST_ID = "authRequestId"
        private const val DEVICE_ASYMMETRICAL_KEY = "asymmetricalKey"
        private const val DEVICE_REQUEST_PRIVATE_KEY = "requestPrivateKey"

        private const val DEFAULT_KDF_ITERATIONS = 600000
        private const val ENCRYPTED_USER_KEY = "encryptedUserKey"
        private const val PUBLIC_KEY = "PublicKey"
        private const val PRIVATE_KEY = "privateKey"
        private const val USER_ID_1 = "2a135b23-e1fb-42c9-bec3-573857bc8181"
        private const val USER_ID_2 = "b9d32ec0-6497-4582-9798-b350f53bfa02"
        private const val ORGANIZATION_IDENTIFIER = "organizationIdentifier"
        private val ORGANIZATIONS = listOf(createMockOrganization(number = 0))
        private val TWO_FACTOR_AUTH_METHODS_DATA = mapOf(
            TwoFactorAuthMethod.EMAIL to JsonObject(
                mapOf("Email" to JsonPrimitive("ex***@email.com")),
            ),
            TwoFactorAuthMethod.AUTHENTICATOR_APP to JsonObject(mapOf("Email" to JsonNull)),
        )
        private val PRE_LOGIN_SUCCESS = PreLoginResponseJson(
            kdfParams = PreLoginResponseJson.KdfParams.Pbkdf2(iterations = 1u),
        )
        private val AUTH_REQUEST_RESPONSE = AuthRequestResponse(
            privateKey = PRIVATE_KEY,
            publicKey = PUBLIC_KEY,
            accessCode = "accessCode",
            fingerprint = "fingerprint",
        )
        private val REFRESH_TOKEN_RESPONSE_JSON = RefreshTokenResponseJson(
            accessToken = ACCESS_TOKEN_2,
            expiresIn = 3600,
            refreshToken = REFRESH_TOKEN_2,
            tokenType = "Bearer",
        )
        private val GET_TOKEN_RESPONSE_SUCCESS = GetTokenResponseJson.Success(
            accessToken = ACCESS_TOKEN,
            refreshToken = "refreshToken",
            tokenType = "Bearer",
            expiresInSeconds = 3600,
            key = "key",
            kdfType = KdfTypeJson.ARGON2_ID,
            kdfIterations = 600000,
            kdfMemory = 16,
            kdfParallelism = 4,
            privateKey = "privateKey",
            shouldForcePasswordReset = true,
            shouldResetMasterPassword = true,
            twoFactorToken = null,
            masterPasswordPolicyOptions = null,
            userDecryptionOptions = null,
        )
        private val ACCOUNT_1 = AccountJson(
            profile = AccountJson.Profile(
                userId = USER_ID_1,
                email = EMAIL,
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
        private val SINGLE_USER_STATE_1_WITH_PASS = UserStateJson(
            activeUserId = USER_ID_1,
            accounts = mapOf(
                USER_ID_1 to ACCOUNT_1.copy(
                    profile = ACCOUNT_1.profile.copy(
                        userDecryptionOptions = UserDecryptionOptionsJson(
                            hasMasterPassword = true,
                            keyConnectorUserDecryptionOptions = null,
                            trustedDeviceUserDecryptionOptions = null,
                        ),
                    ),
                ),
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
        private val ACCOUNT_TOKENS_1: AccountTokensJson = AccountTokensJson(
            accessToken = ACCESS_TOKEN,
            refreshToken = REFRESH_TOKEN,
        )
        private val ACCOUNT_TOKENS_2: AccountTokensJson = AccountTokensJson(
            accessToken = ACCESS_TOKEN_2,
            refreshToken = "refreshToken",
        )
        private val USER_ORGANIZATIONS = listOf(
            UserOrganizations(
                userId = USER_ID_1,
                organizations = ORGANIZATIONS.toOrganizations(),
            ),
        )
        private val VAULT_UNLOCK_DATA = listOf(
            VaultUnlockData(
                userId = USER_ID_1,
                status = VaultUnlockData.Status.UNLOCKED,
            ),
        )
    }
}
