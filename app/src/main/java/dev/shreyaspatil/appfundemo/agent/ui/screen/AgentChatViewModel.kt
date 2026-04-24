package dev.shreyaspatil.appfundemo.agent.ui.screen

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shreyaspatil.appfunctions.notyagent.LlmAgent
import dev.shreyaspatil.appfundemo.agent.NotyAgentExecutor
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

class AgentChatViewModel(
    private val executor: NotyAgentExecutor,
    applicationContext: Context
) : ViewModel() {

    // Holds the chat history
    private val _messages = mutableStateListOf<ChatMessage>()
    private val _llmAgent = mutableStateOf<LlmAgent?>(null)
    val messages: List<ChatMessage> = _messages

    var location by mutableStateOf<Location?>(null)
        private set

    fun onLocationReceived(loc: Location) {
        location = loc
    }

    init {
        viewModelScope.launch {
            val appFunctions = executor.getAvailableAppFunctions()
            _llmAgent.value = LlmAgent("AIzaSyAQQa0k8RwLAG1NikWs0vxvtixo_pnPlsY", appFunctions, applicationContext)
            _messages.add(ChatMessage("Available AppFunctions: \n${appFunctions.values.joinToString("\n") { it.id }}", false))
        }

        _messages.add(
            ChatMessage(
                "Hi, I'm Gemini, ready to execute AppFunctions",
                false
            )
        )
    }

    fun onSendMessage(messageText: String) {
        if (messageText.isBlank()) return

        viewModelScope.launch {
            _messages.add(ChatMessage(messageText, isUser = true))
            processMessage(messageText.lowercase().trim())
        }
    }

    private fun processMessage(lowerCaseText: String) {
        val agent = _llmAgent.value ?: return
        viewModelScope.launch {
            // TODO: Loading state
            try {
                val result = agent.send(
                    userMessage = lowerCaseText,
                    onStep = { step ->
                        // Stream intermediate steps to UI as they happen
                        if (step.functionId != null) {
                            _messages.add(ChatMessage("⚙️ Calling ${step.functionId}…\n${step.thought}", isUser = false))
                        }
                    },
                    executeFn = { functionId, params ->
                        executor.executeAppFunction(functionId, params)
                    }
                )

                _messages.add(ChatMessage(result.finalAnswer, isUser = false))
            } catch (e: Exception) {
                Log.e("LlmAgent", e.toString())
                _messages.add(ChatMessage("Error: ${e.message}", isUser = false))
            }
        }
    }
}