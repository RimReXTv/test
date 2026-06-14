package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.protocol.NetworkType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Moshi Data Classes for Gemini REST request ---

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class ThinkingConfig(
    @Json(name = "thinkingLevel") val thinkingLevel: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "thinkingConfig") val thinkingConfig: ThinkingConfig? = null,
    @Json(name = "temperature") val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

/**
 * Protocol Advisor utilizing gemini-3.1-pro-preview with HIGH thinking level (0.21, gemini-api skill).
 */
class ProtocolAdvisor {

    // System instruction to prime the Gemini model with full AET Whitepaper and protocol constants:
    private val systemInstructionText = """
        You are the AET Genesis Protocol Co-Counsel & Consensus Auditor, a world-class cryptographic engineering advisor.
        You are assisting the user inside the mobile node wallet of the AET Protocol (AET Genesis Ledger).
        
        Protocol Specifications:
        - Protocol Name: AET Genesis Ledger (AET Protocol)
        - Consensus Model: Hybrid Proof of Availability + Validation (PoAV for mobile, Proof of Authority for genesis root validators).
        - Genesis Validators: Immutable nodes bootstrap in the code of the protocol (0.5).
        - Chain IDs:
            - AET Mainnet: 'aet-mainnet-101' (Min fee: 10 microAET)
            - AET Testnet: 'aet-testnet-202' (Min fee: 5 microAET)
            - AET Devnet: 'aet-devnet-606' (Min fee: 1 microAET)
        - Tokenomics: Sum total of 10 Billion microAET (Maximum supply limits). Validator rewards distributed on block confirmation.
        - Cryptosystem:
            - SHA-256 for block and transaction hashes.
            - ECDSA (secp256r1) with SHA256withECDSA for block and transaction signing.
            - Addresses of format "AET_" + first 24 chars of public key SHA-256 hash in uppercase.
        - Network Rules & Anti-Spam: Blocks consist of deterministic BlockHeader and a list of verified Transactions. Fork resolution selects highest validator score or longest valid chain. Minimum fee enforced, replay protection via nonce + chain ID.
        
        You have thinking capabilities ENABLED at HIGH level. You should think deeply about complex questions related to consensus mechanisms, Byzantine fault tolerance inside PoAV, elliptic curve signatures, replay protection vectors, state machine replication, or token economics.
        Avoid vague answers. Provide precise formulas, step-by-step block propagation logic, or cryptographic verification procedures. Speak professionally.
    """.trimIndent()

    suspend fun getAdvice(prompt: String, activeNetwork: NetworkType): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "GEMINI_API_KEY") {
            return "❌ API Key Missing: Gemini API Key is empty or placeholder in .env. Please configure GEMINI_API_KEY in the Secrets panel in the AI Studio UI to enable the Protocol Thinking Advisor."
        }

        // Request object targeting 'gemini-3.1-pro-preview' with 'thinkingConfig' -> 'thinkingLevel': 'high'
        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = "Active Environment: ${activeNetwork.displayName} (${activeNetwork.chainId})\n\nUser request:\n$prompt")))
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText))),
            generationConfig = GenerationConfig(
                thinkingConfig = ThinkingConfig(thinkingLevel = "high")
            )
        )

        return try {
            val response = RetrofitClient.service.generateContent(
                model = "gemini-3.1-pro-preview",
                apiKey = apiKey,
                request = request
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response could be formulated by the model."
        } catch (e: Exception) {
            Log.e("ProtocolAdvisor", "Gemini API call failed", e)
            "Error querying Protocol Advisor: ${e.message ?: "Unknown error"}. Check if your network connection is online and GEMINI_API_KEY is correct."
        }
    }
}
