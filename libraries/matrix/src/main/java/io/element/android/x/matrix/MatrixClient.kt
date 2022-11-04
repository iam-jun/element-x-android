package io.element.android.x.matrix

import io.element.android.x.core.data.CoroutineDispatchers
import io.element.android.x.matrix.core.UserId
import io.element.android.x.matrix.room.MatrixRoom
import io.element.android.x.matrix.room.RoomSummaryDataSource
import io.element.android.x.matrix.room.RoomSummaryDetailsFactory
import io.element.android.x.matrix.room.RustRoomSummaryDataSource
import io.element.android.x.matrix.room.message.RoomMessageFactory
import io.element.android.x.matrix.session.SessionStore
import io.element.android.x.matrix.sync.SlidingSyncObserverProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.*
import timber.log.Timber
import java.io.Closeable

class MatrixClient internal constructor(
    private val client: Client,
    private val sessionStore: SessionStore,
    private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : Closeable {

    private val clientDelegate = object : ClientDelegate {
        override fun didReceiveAuthError(isSoftLogout: Boolean) {
            Timber.v("didReceiveAuthError()")
        }

        override fun didReceiveSyncUpdate() {
            Timber.v("didReceiveSyncUpdate()")
        }

        override fun didUpdateRestoreToken() {
            Timber.v("didUpdateRestoreToken()")
        }
    }

    private val slidingSyncView = SlidingSyncViewBuilder()
        .timelineLimit(limit = 1u)
        .requiredState(
            requiredState = listOf(
                RequiredState(key = "m.room.avatar", value = ""),
                RequiredState(key = "m.room.name", value = ""),
                RequiredState(key = "m.room.encryption", value = ""),
            )
        )
        .name(name = "HomeScreenView")
        .syncMode(mode = SlidingSyncMode.FULL_SYNC)
        .build()

    private val slidingSync = client
        .slidingSync()
        .homeserver("https://slidingsync.lab.element.dev")
        .withCommonExtensions()
        .addView(slidingSyncView)
        .build()

    private val slidingSyncObserverProxy = SlidingSyncObserverProxy(coroutineScope)
    private val roomSummaryDataSource: RustRoomSummaryDataSource =
        RustRoomSummaryDataSource(slidingSyncObserverProxy.updateSummaryFlow, slidingSync, slidingSyncView, dispatchers)
    private var slidingSyncObserverToken: StoppableSpawn? = null

    init {
        client.setDelegate(clientDelegate)
    }

    fun getRoom(roomId: String): MatrixRoom? {
        val slidingSyncRoom = slidingSync.getRoom(roomId) ?: return null
        val room = slidingSyncRoom.fullRoom() ?: return null
        return MatrixRoom(
            slidingSyncUpdateFlow = slidingSyncObserverProxy.updateSummaryFlow,
            slidingSyncRoom = slidingSyncRoom,
            room = room
        )
    }

    fun startSync() {
        roomSummaryDataSource.startSync()
        slidingSync.setObserver(slidingSyncObserverProxy)
        slidingSyncObserverToken = slidingSync.sync()
    }

    fun stopSync() {
        roomSummaryDataSource.stopSync()
        slidingSync.setObserver(null)
        slidingSyncObserverToken?.cancel()
    }

    fun roomSummaryDataSource(): RoomSummaryDataSource = roomSummaryDataSource

    override fun close() {
        stopSync()
        roomSummaryDataSource.close()
        client.setDelegate(null)
    }

    suspend fun logout() = withContext(dispatchers.io) {
        close()
        client.logout()
        sessionStore.reset()
    }

    fun userId(): UserId = UserId(client.userId())
    suspend fun loadUserDisplayName(): Result<String> = withContext(dispatchers.io) {
        runCatching {
            client.displayName()
        }
    }

    suspend fun loadUserAvatarURLString(): Result<String> = withContext(dispatchers.io) {
        runCatching {
            client.avatarUrl()
        }
    }

    suspend fun loadMediaContentForSource(source: MediaSource): Result<ByteArray> =
        withContext(dispatchers.io) {
            runCatching {
                client.getMediaContent(source).toUByteArray().toByteArray()
            }
        }

    suspend fun loadMediaThumbnailForSource(
        source: MediaSource,
        width: Long,
        height: Long
    ): Result<ByteArray> =
        withContext(dispatchers.io) {
            runCatching {
                client.getMediaThumbnail(source, width.toULong(), height.toULong()).toUByteArray()
                    .toByteArray()
            }
        }


}