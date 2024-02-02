/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.element.android.features.onboarding.impl

import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.google.gson.Gson
import io.element.android.features.onboarding.impl.credential.DefaultLoginSharedCredentialUserStory
import io.element.android.features.onboarding.impl.credential.LoginSharedCredentialEvents
import io.element.android.features.onboarding.impl.credential.SharedCredential
import io.element.android.features.onboarding.impl.credential.loginError
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.di.ApplicationContext
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Note: this Presenter is ignored regarding code coverage because it cannot reach the coverage threshold.
 * When this presenter get more code in it, please remove the ignore rule in the kover configuration.
 */
class OnBoardingPresenter @Inject constructor(
    private val buildMeta: BuildMeta,
    @ApplicationContext private val context: Context,
    private val defaultLoginUserStory: DefaultLoginSharedCredentialUserStory,
    private val authenticationService: MatrixAuthenticationService,
    ) : Presenter<OnBoardingState> {
    @Composable
    override fun present(): OnBoardingState {
        val localCoroutineScope = rememberCoroutineScope()
        val sharedCredential = getSharedCredential()
        val loginAction: MutableState<AsyncData<SessionId>> = remember {
            mutableStateOf(AsyncData.Uninitialized)
        }

        fun handleEvents(event: LoginSharedCredentialEvents) {
            when (event) {
                LoginSharedCredentialEvents.Submit -> {
                    localCoroutineScope.submit(sharedCredential, loginAction)
                }
                LoginSharedCredentialEvents.ClearError -> loginAction.value = AsyncData.Uninitialized
            }
        }

        return OnBoardingState(
            isDebugBuild = buildMeta.buildType != BuildType.RELEASE,
            canLoginWithQrCode = OnBoardingConfig.CAN_LOGIN_WITH_QR_CODE,
            canCreateAccount = OnBoardingConfig.CAN_CREATE_ACCOUNT,
            sharedCredential = sharedCredential,
            loginAction = loginAction.value,
            eventSink = ::handleEvents
        )
    }
    private fun getSharedCredential(): SharedCredential? {
        Timber.tag("getUser").d("start getUser")
        return try {
            val url = "content://com.aoh.SharedProvider/data"
            val contentUri = Uri.parse(url)
            val selectionClause = "_key LIKE ?"
            val selectionArgs = arrayOf("user")
            val c: Cursor? = context.contentResolver.query(contentUri, null, selectionClause, selectionArgs, null)
            var jsonString = ""
            if(c != null) {
                val index = c.getColumnIndex("value")
                while (c.moveToNext()) {
                    jsonString += c.getString(index)
                }
                c.close()
            }else{
                Timber.tag("getUser").d("cursor is null")
            }

            val isValid = jsonString.contains("username") && jsonString.contains("password")
            Timber.tag("getUser").d("jsonString $isValid $jsonString")

            if(isValid) {
                val gson = Gson()

              return gson.fromJson(jsonString, SharedCredential::class.java)
            }

            null
        } catch (e: Exception) {
            Timber.tag("getUser").e(e.message!!)
            null
        }
    }

    private fun CoroutineScope.submit(sharedCredential: SharedCredential?, loggedInState: MutableState<AsyncData<SessionId>>) = launch {
        loggedInState.value = AsyncData.Loading()
        authenticationService.setHomeserver("https://matrix.org")

        authenticationService.login(sharedCredential!!.username, sharedCredential.password)
            .onSuccess { sessionId ->
                // We will not navigate to the WaitList screen, so the login user story is done
                defaultLoginUserStory.setLoginFlowIsDone(true)
                loggedInState.value = AsyncData.Success(sessionId)
            }
            .onFailure { failure ->
                Timber.tag("Onboarding").d("Start submit onFailure $failure")

                loggedInState.value = AsyncData.Failure(failure)
            }
    }
}

