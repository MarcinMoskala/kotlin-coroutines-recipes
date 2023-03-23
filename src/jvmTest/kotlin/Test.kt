import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

interface MessagesApi {
    fun observeMessages(channel: String): Flow<MessageDto>
}

data class MessageDto(
    val fromUserId: String
) {
    fun toMessage(): Message = Message(
        fromUserId = fromUserId
    )
}

data class Message(
    val fromUserId: String
)

class MessagesRepository(
    private val api: MessagesApi
) {
    fun observeMessages(fromUserId: String, channel: String) = api
        .observeMessages(channel)
        .filter { it.fromUserId == fromUserId }
        .map { it.toMessage() }
        .distinctUntilChanged()
}

class FakeMessagesApi(private val results: Map<String, Flow<MessageDto>>) : MessagesApi {
    override fun observeMessages(channel: String): Flow<MessageDto> = results[channel]!!
}

class MessagesRepositoryTest {
    private val aChannel1 = "SOME_CHANNER"
    private val aUserId1 = "SOME_USER_ID"
    private val aUserId2 = "SOME_USER_ID"
    private val aMessageDto1 = MessageDto(fromUserId = aUserId1)

    @Test
    fun `should drop repeating messages`() = runTest {
        // given
        val api = FakeMessagesApi(
            mapOf(
                aChannel1 to flowOf(aMessageDto1, aMessageDto1)
            )
        )
        val repository = MessagesRepository(api)

        // when
        val result = repository.observeMessages(aUserId1, aChannel1)

        // then
        assertEquals(listOf(aMessageDto1.toMessage()), result.toList())
    }

//    @Test
//    fun `should respond only with messages from specific user`() = runTest {
//        // given
//        val api = FakeMessagesApi(
//            mapOf(
//                aChannel1 to flowOf(
//                    aMessageDto1.copy(fromUserId = aUserId1),
//                    aMessageDto1.copy(fromUserId = aUserId2),
//                    aMessageDto1.copy(fromUserId = aUserId1),
//                )
//            )
//        )
//        val repository = MessagesRepository(api)
//
//        // when
//        val result = repository.observeMessages(aUserId1, aChannel1)
//
//        // then
//        assertEquals(listOf(aMessageDto1.toMessage()), result.toList())
//    }
}