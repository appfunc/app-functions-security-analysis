package dev.shreyaspatil.appfundemo.agent

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.content.Context
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.metadata.AppFunctionAppMetadata
import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionFloatTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionLongTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionPackageMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import androidx.appfunctions.metadata.AppFunctionUnitTypeMetadata
import androidx.lifecycle.viewModelScope
import dev.shreyaspatil.appfunctions.notyagent.AppFunctionDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.collections.emptyMap
import kotlin.collections.firstOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TARGET_PACKAGE = "dev.filipfan.appfunctionspilot.tool"


class NotyAgentExecutor(context: Context) {
    private val appContext = context.applicationContext

    private val mainExecutor = context.mainExecutor
    val functionMetadataMap = mutableMapOf<FunctionDeclaration, AppFunctionMetadata>()

    private val appFunctionManager: AppFunctionManager =
        AppFunctionManager.getInstance(appContext)
            ?: throw UnsupportedOperationException("AppFunctions not supported on this device")
    private val appFunctionManagerExecutor: android.app.appfunctions.AppFunctionManager = context.getSystemService(android.app.appfunctions.AppFunctionManager::class.java)

    suspend fun getAvailableAppFunctions(): Map<FunctionDeclaration, AppFunctionMetadata> {
        val packages = try {
            appFunctionManager.observeAppFunctions(AppFunctionSearchSpec()).first()
        } catch (e: Exception) {
            Log.e("NotyAgentExecutor", "Error observing app functions", e)
            emptyList()
        }


        Log.i("NotyAgentExecutor", "Found ${packages.size} packages with AppFunctions")

        for (pkg in packages) {
            Log.i("NotyAgentExecutor", "Processing package: ${pkg.packageName} (${pkg.appFunctions.size} functions)")
            functionMetadataMap += processPackageMetadata(pkg)
        }

        if (functionMetadataMap.isEmpty()) {
            Log.w("NotyAgentExecutor", "No AppFunctions discovered. Ensure provider apps are installed and have DISCOVER_APP_FUNCTIONS permission granted to this app.")
        }

        return functionMetadataMap
    }

    private fun AppFunctionMetadata.toFunctionDeclaration(): FunctionDeclaration = FunctionDeclaration(
        name = this.id,
        shortName = this.id.substringAfterLast("#"),
        description = this.description,
        parameters = this.toParametersSchema(),
        response = this.response.valueType.toSchema(this.components),
    )

    private fun AppFunctionMetadata.toParametersSchema(): Schema? {
        if (parameters.isEmpty()) return null

        // Parameters are wrapped into a single, top-level object.
        return createObjectSchema(parameters, components)
    }

    private fun createObjectSchema(
        params: List<AppFunctionParameterMetadata>,
        components: AppFunctionComponentsMetadata,
    ): Schema = Schema(
        type = DataType.OBJECT,
        properties = params.associate { param ->
            val baseSchema = param.dataType.toSchema(components)
            // An argument, use the description of parameter level.
            param.name to baseSchema.copy(description = param.description)
        },
        required = params.filter { it.isRequired }.map { it.name },
    )

    private fun AppFunctionDataTypeMetadata.toSchema(
        components: AppFunctionComponentsMetadata,
    ): Schema = when (this) {
        // Primitives
        is AppFunctionIntTypeMetadata -> createPrimitiveSchema(
            DataType.INT,
            description,
            isNullable,
            enumValues?.toList(),
        )

        is AppFunctionLongTypeMetadata -> createPrimitiveSchema(DataType.LONG, description, isNullable)
        is AppFunctionFloatTypeMetadata -> createPrimitiveSchema(
            DataType.FLOAT,
            description,
            isNullable,
        )

        is AppFunctionDoubleTypeMetadata -> createPrimitiveSchema(
            DataType.DOUBLE,
            description,
            isNullable,
        )

        is AppFunctionBooleanTypeMetadata -> createPrimitiveSchema(
            DataType.BOOLEAN,
            description,
            isNullable,
        )

        is AppFunctionStringTypeMetadata -> createPrimitiveSchema(
            DataType.STRING,
            description,
            isNullable,
            enumValues?.toList(),
        )

        is AppFunctionUnitTypeMetadata -> createPrimitiveSchema(DataType.UNIT, description, isNullable)

        // Complex Types
        is AppFunctionArrayTypeMetadata -> Schema(
            type = DataType.ARRAY,
            description = this.description,
            nullable = this.isNullable,
            items = this.itemType.toSchema(components),
        )

        is AppFunctionObjectTypeMetadata -> Schema(
            type = DataType.OBJECT,
            description = this.description,
            nullable = this.isNullable,
            properties = this.properties.mapValues { (_, value) -> value.toSchema(components) },
            required = this.required,
        )

        is AppFunctionReferenceTypeMetadata -> {
            val resolvedType = components.dataTypes[this.referenceDataType]
                ?: throw IllegalStateException("Reference to ${this.referenceDataType} not found.")
            resolvedType.toSchema(components)
        }

        else -> throw IllegalStateException("Unexpected data type: $this")
    }

    private fun createPrimitiveSchema(
        type: DataType,
        description: String,
        nullable: Boolean,
        enumValues: List<Any>? = null,
    ) = Schema(
        type = type,
        description = description,
        nullable = nullable,
        enum = enumValues ?: emptyList(),
    )


    fun List<AppFunctionMetadata>.toFunctionDeclarations(): Map<FunctionDeclaration, AppFunctionMetadata> = this.associateBy { it.toFunctionDeclaration() }


    private fun processPackageMetadata(metadata: AppFunctionPackageMetadata): Map<FunctionDeclaration, AppFunctionMetadata> {
        return metadata.appFunctions.toFunctionDeclarations()
    }

    suspend fun executeAppFunction(functionId: String, params: JSONObject): String {
        val entry = functionMetadataMap.entries.firstOrNull { (_, y ) ->
            y.id == functionId
        }

//        val packageName = inferPackageNameFromFunctionId(functionId) ?: throw RuntimeException("Could not infer package name from functionId")
        val packageName = entry?.value?.packageName  ?: throw RuntimeException("Could not infer package name from functionId")
        val builder = ExecuteAppFunctionRequest.Builder(packageName, functionId)

        Log.i("NotyAgentExecutor", "Executing function: $functionId for package: $packageName")

        builder.setParameters(jsonObjectToGenericDocument(json = params))

        val executeAppFunctionRequest = builder.build()
        Log.d("NotyAgentExecutor", "Sending Request: ${executeAppFunctionRequest.functionIdentifier} ${executeAppFunctionRequest.parameters}")

        val response = runRawExecution(executeAppFunctionRequest)

        Log.d("NotyAgentExecutor", "Received Response: $response")

        val json = genericDocumentToJson(response)

        return json.toString(2)
    }

    private suspend fun runRawExecution(
        request: ExecuteAppFunctionRequest,
    ) = suspendCancellableCoroutine<GenericDocument> { cont ->
        val cancellationSignal = CancellationSignal()

        appFunctionManagerExecutor.executeAppFunction(
            request,
            mainExecutor,
            cancellationSignal,
            object : OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> {

                override fun onResult(response: ExecuteAppFunctionResponse) {
                    cont.resume(response.resultDocument)
                }

                override fun onError(error: AppFunctionException) {
                    Log.e("NotyAgentExecutor", "Execution failed: ${error.message}")
                    cont.resumeWithException(error)
                }
            }
        )

        cont.invokeOnCancellation {
            cancellationSignal.cancel()
        }
    }

    private fun errorJson(msg: String) = JSONObject().put("error", msg).toString()

    fun jsonObjectToGenericDocument(
        json: JSONObject,
        namespace: String = "default",
        id: String = json.optString("id", UUID.randomUUID().toString()),
        schemaType: String = json.optString("schemaType", "JsonObject")
    ): GenericDocument {
        val builder = GenericDocument.Builder<GenericDocument.Builder<*>>(namespace, id, schemaType)

        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "id" || key == "schemaType") continue

            when (val value = json.opt(key)) {
                null, JSONObject.NULL -> Unit
                is String -> builder.setPropertyString(key, value)
                is Int -> builder.setPropertyLong(key, value.toLong())
                is Long -> builder.setPropertyLong(key, value)
                is Float -> builder.setPropertyDouble(key, value.toDouble())
                is Double -> builder.setPropertyDouble(key, value)
                is Boolean -> builder.setPropertyBoolean(key, value)
                is JSONObject -> builder.setPropertyDocument(
                    key,
                    jsonObjectToGenericDocument(
                        json = value,
                        namespace = namespace,
                        schemaType = key.replaceFirstChar { it.uppercase() }
                    )
                )
                is JSONArray -> setJsonArrayProperty(builder, key, value, namespace)
                else -> builder.setPropertyString(key, value.toString())
            }
        }

        return builder.build()
    }

    private fun setJsonArrayProperty(
        builder: GenericDocument.Builder<*>,
        key: String,
        array: JSONArray,
        namespace: String
    ) {
        if (array.length() == 0) return

        when (array.opt(0)) {
            is String -> {
                val values = Array(array.length()) { i -> array.optString(i) }
                builder.setPropertyString(key, *values)
            }

            is Int, is Long -> {
                val values = LongArray(array.length()) { i ->
                    array.opt(i).toString().toLong()
                }
                builder.setPropertyLong(key, *values)
            }

            is Float, is Double -> {
                val values = DoubleArray(array.length()) { i ->
                    array.opt(i).toString().toDouble()
                }
                builder.setPropertyDouble(key, *values)
            }

            is Boolean -> {
                val values = BooleanArray(array.length()) { i ->
                    array.optBoolean(i)
                }
                builder.setPropertyBoolean(key, *values)
            }

            is JSONObject -> {
                val docs = Array(array.length()) { i ->
                    jsonObjectToGenericDocument(
                        json = array.getJSONObject(i),
                        namespace = namespace,
                        schemaType = key.replaceFirstChar { it.uppercase() }
                    )
                }
                builder.setPropertyDocument(key, *docs)
            }

            else -> {
                val values = Array(array.length()) { i -> array.opt(i).toString() }
                builder.setPropertyString(key, *values)
            }
        }
    }

    fun genericDocumentToJson(doc: GenericDocument): JSONObject {
        val json = JSONObject()
        json.put("id", doc.id)
        json.put("namespace", doc.namespace)
        json.put("schemaType", doc.schemaType)

        for (name in doc.propertyNames) {
            var handled = false

            doc.getPropertyStringArray(name)?.takeIf { it.isNotEmpty() }?.let {
                json.put(name, if (it.size == 1) it[0] else JSONArray(it.toList()))
                handled = true
            }
            if (handled) continue

            doc.getPropertyLongArray(name)?.takeIf { it.isNotEmpty() }?.let {
                val arr = JSONArray()
                it.forEach(arr::put)
                json.put(name, if (it.size == 1) it[0] else arr)
                handled = true
            }
            if (handled) continue

            doc.getPropertyDoubleArray(name)?.takeIf { it.isNotEmpty() }?.let {
                val arr = JSONArray()
                it.forEach(arr::put)
                json.put(name, if (it.size == 1) it[0] else arr)
                handled = true
            }
            if (handled) continue

            doc.getPropertyBooleanArray(name)?.takeIf { it.isNotEmpty() }?.let {
                val arr = JSONArray()
                it.forEach(arr::put)
                json.put(name, if (it.size == 1) it[0] else arr)
                handled = true
            }
            if (handled) continue

            doc.getPropertyDocumentArray(name)?.takeIf { it.isNotEmpty() }?.let {
                val arr = JSONArray()
                it.forEach { nested -> arr.put(genericDocumentToJson(nested)) }
                json.put(name, if (it.size == 1) arr.get(0) else arr)
            }
        }

        return json
    }

    private fun inferPackageNameFromFunctionId(functionId: String): String? {
        Log.d("NotyAgentExecutor", "Inferring package name from functionId: $functionId")
        val slashPkg = functionId.substringBefore("/", "")
        if (slashPkg.contains('.')) return slashPkg
        val segments = functionId.substringBefore("#").split(".")
        return if (segments.size >= 3) segments.take(3).joinToString(".") else null
    }
}
