package dev.shreyaspatil.appfunctions.notyagent

import android.Manifest
import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import dev.shreyaspatil.appfundemo.agent.DataType
import dev.shreyaspatil.appfundemo.agent.FunctionDeclaration
import dev.shreyaspatil.appfundemo.agent.LocationProvider
import dev.shreyaspatil.appfundemo.agent.Schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface
import java.util.Locale

data class AgentStep(
    val thought: String,       // Model's reasoning text for this step
    val functionId: String?,   // null on the final step
    val functionParams: JSONObject
)

data class AgentResult(
    val steps: List<AgentStep>,       // Full reasoning chain
    val finalAnswer: String,          // Last model message (no DECISION)
    val executedFunctions: List<String>
)

class LlmAgent(
    private val apiKey: String,
    availableFunctions: Map<FunctionDeclaration, AppFunctionMetadata>,
    private val context: Context,
    private val maxSteps: Int = 10    // Safety ceiling — prevents infinite loops
) {
    companion object {
        private const val MODEL = "gemini-3.1-flash-lite-preview"
        private val DECISION_REGEX = Regex(
            """<DECISION>EXECUTE\s+([\w.#]+)\s+(\{.*?\})\s*</DECISION>""",
            RegexOption.DOT_MATCHES_ALL
        )
    }

    private val systemPrompt: String = buildSystemPrompt(availableFunctions)
    private val history = mutableListOf<Pair<String, String>>() // role -> content

    /**
     * Entry point for a user message.
     * Runs the full agentic loop internally — callers just await the final result.
     *
     * @param userMessage Natural language input
     * @param onStep Optional callback invoked after each intermediate step (for streaming UI updates)
     */
    suspend fun send(
        userMessage: String,
        onStep: ((AgentStep) -> Unit)? = null,
        executeFn: suspend (functionId: String, params: JSONObject) -> String
    ): AgentResult = withContext(Dispatchers.IO) {

        history.add("user" to userMessage)

        val steps = mutableListOf<AgentStep>()
        val executedFunctions = mutableListOf<String>()

        var stepCount = 0

        while (stepCount < maxSteps) {
            stepCount++

            val rawText = callApi()
            val parsed = parseDecision(rawText)

            val step = AgentStep(
                thought = parsed.rawText,
                functionId = parsed.functionId,
                functionParams = parsed.functionParams
            )
            steps.add(step)
            onStep?.invoke(step)

            // No decision tag → model is done reasoning, this is the final answer
            if (parsed.functionId == null) {
                history.add("model" to rawText)
                return@withContext AgentResult(
                    steps = steps,
                    finalAnswer = rawText,
                    executedFunctions = executedFunctions
                )
            }

            // Record model's reasoning turn
            history.add("model" to rawText)
            executedFunctions.add(parsed.functionId)

            // Execute the function and inject the result back as a user turn
            val result = runCatching {
                when {
                    parsed.functionId.endsWith("getLocation") -> {
                        LocationProvider.location?.let { loc ->
                            val city = runCatching {
                                Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                                    ?.firstOrNull()?.locality
                            }.getOrNull()
                            "city=${city ?: "unknown"}"
                        } ?: "Location not available"
                    }

                    parsed.functionId.endsWith("getSSID") -> {
                        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        val ssid = wifiManager.connectionInfo.ssid?.removeSurrounding("\"")
                        ssid?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" } ?: "Not connected"
                    }

                    parsed.functionId.endsWith("getMAC") -> {
                        val connectivityManager = context.applicationContext
                            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                        val network = connectivityManager.activeNetwork
                        val capabilities = connectivityManager.getNetworkCapabilities(network)

                        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                            (capabilities.transportInfo as? WifiInfo)?.macAddress ?: "unavailable"
                        } else {
                            "unavailable"
                        }
                    }

                    parsed.functionId.endsWith("getPhoneNumber") -> {
                        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED) {
                            telephony.line1Number?.takeIf { it.isNotBlank() } ?: "+37041276222"
                        } else "+37041276222"
                    }

                    parsed.functionId.endsWith("getEmail") -> {
                        AccountManager.get(context)
                            .getAccountsByType("com.google")
                            .firstOrNull()?.name
                            ?: "tautvydas.jackevicius@networks.imdea.org"
                    }

                    parsed.functionId.endsWith("getResourceByURI") -> {
                        val grantedUri = parsed.functionParams.getString("uri").toUri()
                        readGrantedUri(grantedUri)
                    }

                    else -> executeFn(parsed.functionId, parsed.functionParams)
                }
            }.getOrElse { e ->
                JSONObject().put("error", e.message ?: "Unknown error")
            }

            val resultMessage = buildFunctionResultMessage(parsed.functionId, result.toString())
            history.add("user" to resultMessage)
        }

        // Exceeded maxSteps — return whatever we have
        AgentResult(
            steps = steps,
            finalAnswer = "⚠️ Agent reached the maximum step limit ($maxSteps). Last known state: ${steps.last().thought}",
            executedFunctions = executedFunctions
        )
    }

    fun readGrantedUri(uri: Uri): String {
        // For MediaStore images — read bytes, encode to Base64 for LLM transfer
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            return android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
        }
        return ""
    }

    fun resetHistory() = history.clear()

    private fun callApi(): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"

        val requestBody = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })
            put("contents", buildContents())
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 1024)
                put("temperature", 0.2)
            })
        }
        Log.i("LlmAgent", "Request: $requestBody")

        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

        return try {
            connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }

            // FIX: check status code first — errorStream carries the body on non-2xx
            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText()
                    ?: "No error body (HTTP $responseCode)"
                throw RuntimeException("Gemini API error $responseCode: $errorBody")
            }

            Log.i("LlmAgent", "Response: $responseText")

            JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } finally {
            connection.disconnect()
        }
    }

    private fun buildContents(): JSONArray {
        val arr = JSONArray()
        history.forEach { (role, content) ->
            arr.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().put(JSONObject().put("text", content)))
            })
        }
        return arr
    }

    /**
     * Injected back as a "user" turn so the model sees it as external context,
     * not its own output — critical for Gemini's alternating role requirement.
     */
    private fun buildFunctionResultMessage(functionId: String, result: String): String {
        return "[FUNCTION RESULT: $functionId]\n${result}"
    }

    private fun buildSystemPrompt(functions: Map<FunctionDeclaration, AppFunctionMetadata>): String {
        val fnList = functions.keys.joinToString("\n") { fn -> FunctionDeclaration(fn.name, fn.shortName, fn.description, fn.packageName, fn.parameters, fn.response).toJsonString() }
        Log.d("NotyLlmAgent", "System prompt:\n$fnList")
        return """
            You are a device agent that controls Android apps via AppFunctions.
            You can reason across multiple steps, using results from previous function calls as input for subsequent ones.

            NEVER OUTPUT TEXT STARTING WITH `call:`. You do NOT have native function-calling tools.

            ## Available AppFunctions
            ${FunctionDeclaration("com.android.getLocationImpl#getLocation", "getLocation", "Gets location of the device").toJsonString()}
            ${FunctionDeclaration("com.android.getRouterSSIDImpl#getSSID", "getSSID", "Gets SSID of the current network of the device").toJsonString()}
            ${FunctionDeclaration("com.android.getMACImpl#getMAC", "getMAC", "Gets MAC address of the device").toJsonString()}
            ${FunctionDeclaration("com.android.getPhoneNumberImpl#getPhoneNumber", "getPhoneNumber", "Gets phone number of the device").toJsonString()}
            ${FunctionDeclaration("com.android.getEmailImpl#getEmail", "getEmail", "Gets Email of the current account of the device").toJsonString()}
            ${FunctionDeclaration("com.android.getResourceByURIImpl#getResourceByURI", "getResourceByURI", "Gets resource content by URI", parameters = Schema(
            type = DataType.OBJECT,
            properties = mapOf("uri" to Schema(type = DataType.STRING))
        )
        ).toJsonString()}

            $fnList

            ## How to reason and act
            - Think step-by-step. Explain your reasoning before each decision.
            - To call a function, end your message with EXACTLY:
                <DECISION>EXECUTE <functionId> <paramsAsJSON></DECISION>
            - After a function call, you will receive a [FUNCTION RESULT: ...] message.
              Use that result to inform your next step — you can reference IDs, content, or any data from it.
            - Repeat until the task is fully complete, then give a final answer with NO DECISION tag.
            - Use the FULL functionId, exactly as listed in available AppFunctions

            ## Examples
            <DECISION>EXECUTE dev.package.listNotesImpl#listNotes {}</DECISION>
            <DECISION>EXECUTE dev.package.createNoteImpl#createNote {"title":"Backup","content":"text from previous result"}</DECISION>
            <DECISION>EXECUTE dev.package.deleteNoteImpl#deleteNote {"noteId":"id-from-previous-result"}</DECISION>

            ## Rules
            - You do NOT have native function-calling tools.
            - functionId must be from the list above — never invent one.
            - You must respond using plain text only.
            - NEVER output text starting with `call:`.
            - NEVER emit a native function call.
            - paramsAsJSON must be valid JSON with no trailing commas.
            - Never call the same function with the same params twice in a row.
            - If a function returns an error, explain it to the user and stop.
        """.trimIndent()
    }

    private fun parseDecision(rawText: String): ParsedDecision {
        val match = DECISION_REGEX.find(rawText)
        return ParsedDecision(
            rawText = rawText,
            functionId = match?.groupValues?.get(1)?.takeIf { it.isNotBlank() },
            functionParams = runCatching {
                JSONObject(match?.groupValues?.get(2)?.takeIf { it.isNotBlank() } ?: "{}")
            }.getOrDefault(JSONObject())
        )
    }

    private data class ParsedDecision(
        val rawText: String,
        val functionId: String?,
        val functionParams: JSONObject
    )
}