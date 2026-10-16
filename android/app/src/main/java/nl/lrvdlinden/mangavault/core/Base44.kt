package nl.lrvdlinden.mangavault.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Backend configuration — same Base44 backend as https://mangas.base44.app. */
object Config {
    const val APP_BASE_URL = "https://mangas.base44.app"
    const val API_BASE_URL = "https://mangas.base44.app/api"
    const val APP_ID = "6acd06d4ea8aff769f949a17"
    const val URL_SCHEME = "mangavault"
    fun publicProfileUrl(id: String) = "$APP_BASE_URL/u/$id"
}

class ApiException(val status: Int, message: String) : Exception(message)

/** Lenient JSON used for every backend response: unknown keys ignored, nulls fall back to defaults. */
val VaultJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

/** Cursor page returned by `/entities/<Name>/v2/list`. */
@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val next_cursor: String? = null,
    val has_more: Boolean? = null,
)

/** Decodes Int, Double (3.0), numeric String or null as Int (null → 0). Use: `@Serializable(with = FlexInt::class) val n: Int = 0` */
object FlexInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
    override fun deserialize(decoder: Decoder): Int {
        val d = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val e = d.decodeJsonElement()
        return (e as? JsonPrimitive)?.flexInt() ?: 0
    }
}

fun JsonPrimitive.flexInt(): Int? =
    if (this is JsonNull) null else intOrNull ?: doubleOrNull?.toInt() ?: contentOrNull?.trim()?.toDoubleOrNull()?.toInt()

/** Converts Kotlin values (Map, List, String, Number, Boolean, null, JsonElement) to a JsonElement. */
fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this.toDouble())
    is Map<*, *> -> JsonObject(this.entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    is Iterable<*> -> JsonArray(this.map { it.toJsonElement() })
    is Array<*> -> JsonArray(this.map { it.toJsonElement() })
    else -> JsonPrimitive(this.toString())
}

// Small helpers for reading untyped JSON objects (function responses).
fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.flexInt() ?: 0
fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

fun parseObject(s: String): JsonObject =
    runCatching { VaultJson.parseToJsonElement(s).jsonObject }.getOrElse { JsonObject(emptyMap()) }

/**
 * Native port of the Base44 JS SDK (auth, entities, functions, file upload).
 * Every function is a suspend function that runs the request on Dispatchers.IO.
 */
object Base44 {
    private lateinit var prefs: SharedPreferences
    private const val TOKEN_KEY = "base44_access_token"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Called when the server rejects the stored token (HTTP 401). */
    @Volatile var onUnauthorized: (() -> Unit)? = null

    @Volatile private var _token: String? = null
    var token: String?
        get() = _token
        set(v) {
            _token = v
            if (::prefs.isInitialized) prefs.edit().apply { if (v == null) remove(TOKEN_KEY) else putString(TOKEN_KEY, v) }.apply()
        }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("mangavault", Context.MODE_PRIVATE)
        _token = prefs.getString(TOKEN_KEY, null)
    }

    private const val APP_PATH = "apps/${Config.APP_ID}"
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    // MARK: Low level

    suspend fun send(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        json: Any? = null,
        body: RequestBody? = null,
        authorized: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        val urlBuilder = "${Config.API_BASE_URL}/$path".toHttpUrl().newBuilder()
        for ((k, v) in query) urlBuilder.addQueryParameter(k, v)
        val reqBody: RequestBody? = when {
            json != null -> VaultJson.encodeToString(JsonElement.serializer(), json.toJsonElement()).toRequestBody(JSON_TYPE)
            body != null -> body
            method == "POST" || method == "PUT" -> "{}".toRequestBody(JSON_TYPE)
            else -> null
        }
        val builder = Request.Builder().url(urlBuilder.build()).header("Accept", "application/json").method(method, reqBody)
        val tok = token
        if (authorized && tok != null) builder.header("Authorization", "Bearer $tok")
        http.newCall(builder.build()).execute().use { res ->
            val text = res.body?.string() ?: ""
            if (!res.isSuccessful) {
                if (res.code == 401 && authorized && tok != null) onUnauthorized?.invoke()
                throw ApiException(res.code, errorMessage(text, res.code))
            }
            text
        }
    }

    private fun errorMessage(text: String, status: Int): String {
        val o = parseObject(text)
        for (key in listOf("message", "detail", "error")) {
            o.str(key)?.takeIf { it.isNotBlank() }?.let { return it }
            (o[key] as? JsonArray)?.firstOrNull()?.let { first ->
                (first as? JsonObject)?.str("msg")?.let { return it }
            }
        }
        if (text.isNotBlank() && text.length < 200) return text
        return "Request failed ($status)"
    }

    inline fun <reified T> decode(s: String): T = VaultJson.decodeFromString(s)

    fun jsonString(q: Map<String, Any?>): String =
        VaultJson.encodeToString(JsonElement.serializer(), q.toJsonElement())

    // MARK: Auth

    @Serializable
    data class LoginResponse(val access_token: String? = null)

    suspend fun login(email: String, password: String) {
        val res = decode<LoginResponse>(send("POST", "$APP_PATH/auth/login", json = mapOf("email" to email, "password" to password), authorized = false))
        token = res.access_token ?: throw ApiException(0, "Invalid email or password")
    }

    suspend fun register(email: String, password: String) {
        send("POST", "$APP_PATH/auth/register", json = mapOf("email" to email, "password" to password), authorized = false)
    }

    suspend fun verifyOtp(email: String, code: String) {
        val text = send("POST", "$APP_PATH/auth/verify-otp", json = mapOf("email" to email, "otp_code" to code), authorized = false)
        runCatching { decode<LoginResponse>(text) }.getOrNull()?.access_token?.let { token = it }
    }

    suspend fun resendOtp(email: String) {
        send("POST", "$APP_PATH/auth/resend-otp", json = mapOf("email" to email), authorized = false)
    }

    suspend fun resetPasswordRequest(email: String) {
        send("POST", "$APP_PATH/auth/reset-password-request", json = mapOf("email" to email), authorized = false)
    }

    suspend fun me(): nl.lrvdlinden.mangavault.model.AppUser = decode(send("GET", "$APP_PATH/entities/User/me"))

    /** Google sign-in URL; after sign-in the backend redirects to `fromUrl?access_token=…`. */
    fun googleLoginUrl(fromUrl: String): String =
        "${Config.API_BASE_URL}/apps/auth/login".toHttpUrl().newBuilder()
            .addQueryParameter("app_id", Config.APP_ID)
            .addQueryParameter("from_url", fromUrl)
            .build().toString()

    fun logout() { token = null }

    // MARK: Entities

    suspend inline fun <reified T> list(
        entity: String,
        query: Map<String, Any?> = emptyMap(),
        sort: String? = null,
        limit: Int = 100,
        fields: List<String>? = null,
        cursor: String? = null,
    ): Page<T> {
        val q = mutableListOf("q" to jsonString(query), "limit" to limit.toString())
        if (sort != null) q += "sort" to sort
        if (fields != null) q += "fields" to fields.joinToString(",")
        if (cursor != null) q += "cursor" to cursor
        return decode(send("GET", "apps/${Config.APP_ID}/entities/$entity/v2/list", query = q))
    }

    /** Loads every page (cursor paging) up to [max] items. */
    suspend inline fun <reified T> listAll(
        entity: String,
        query: Map<String, Any?> = emptyMap(),
        sort: String? = null,
        pageSize: Int = 500,
        fields: List<String>? = null,
        max: Int = 5000,
    ): List<T> {
        val out = mutableListOf<T>()
        var cursor: String? = null
        do {
            val page = list<T>(entity, query, sort, pageSize, fields, cursor)
            out += page.items
            cursor = page.next_cursor
        } while (page.has_more == true && cursor != null && out.size < max)
        return out
    }

    suspend fun distinct(entity: String, field: String, query: Map<String, Any?> = emptyMap()): List<String> {
        val text = send("GET", "$APP_PATH/entities/$entity/v2/list",
            query = listOf("q" to jsonString(query), "limit" to "100", "distinct" to field))
        return parseObject(text).arr("items")?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    }

    suspend inline fun <reified T> get(entity: String, id: String): T =
        decode(send("GET", "apps/${Config.APP_ID}/entities/$entity/$id"))

    suspend inline fun <reified T> create(entity: String, data: Map<String, Any?>): T =
        decode(send("POST", "apps/${Config.APP_ID}/entities/$entity", json = data))

    suspend fun update(entity: String, id: String, data: Map<String, Any?>) {
        send("PUT", "$APP_PATH/entities/$entity/$id", json = data)
    }

    suspend fun delete(entity: String, id: String) {
        send("DELETE", "$APP_PATH/entities/$entity/$id")
    }

    /** `/entities/<Name>/count` */
    suspend fun count(entity: String, query: Map<String, Any?> = emptyMap()): Int {
        val text = send("GET", "$APP_PATH/entities/$entity/count", query = listOf("q" to jsonString(query)))
        return parseObject(text).int("count")
    }

    // MARK: Functions & integrations

    /** Calls a backend function and returns the raw JSON text. */
    suspend fun invoke(function: String, payload: Map<String, Any?> = emptyMap()): String =
        send("POST", "$APP_PATH/functions/$function", json = payload)

    /** `integrations.Core.UploadPublicFile` — returns the public file URL. */
    suspend fun uploadPublicFile(bytes: ByteArray, filename: String, mimeType: String): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val text = send("POST", "$APP_PATH/integration-endpoints/Core/UploadPublicFile", body = body)
        return parseObject(text).str("file_url") ?: throw ApiException(0, "Upload failed")
    }
}

/** Helper for `JsonElement` lists in responses. */
fun JsonElement?.asObjects(): List<JsonObject> =
    (this as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

@Suppress("unused")
private fun JsonElement.debugLong(): Long? = (this as? JsonPrimitive)?.longOrNull

@Suppress("unused")
private fun JsonElement.asArrayOrEmpty(): JsonArray = runCatching { jsonArray }.getOrElse { JsonArray(emptyList()) }
