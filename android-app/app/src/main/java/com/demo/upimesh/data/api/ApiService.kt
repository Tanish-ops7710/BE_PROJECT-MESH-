package com.demo.upimesh.data.api

import com.demo.upimesh.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ApiService {

    @GET("api/server-key")
    suspend fun getServerPublicKey(): Response<ServerKeyResponse>

    @POST("api/demo/send")
    suspend fun demoSend(@Body req: DemoSendRequest): Response<Map<String, Any>>

    @POST("api/demo/create-packet")
    suspend fun createPacketOnly(@Body req: DemoSendRequest): Response<MeshPacket>

    @GET("api/mesh/state")
    suspend fun getMeshState(): Response<MeshStateResponse>

    @POST("api/mesh/gossip")
    suspend fun gossip(): Response<GossipResultResponse>

    @POST("api/mesh/flush")
    suspend fun flush(): Response<FlushResultResponse>

    @POST("api/mesh/reset")
    suspend fun resetMesh(): Response<Map<String, String>>

    @POST("api/bridge/ingest")
    suspend fun ingestPacket(
        @Body packet: MeshPacket,
        @Header("X-Bridge-Node-Id") bridgeNodeId: String = "android-app-bridge",
        @Header("X-Hop-Count") hopCount: Int = 1
    ): Response<IngestResponse>

    @GET("api/accounts")
    suspend fun getAccounts(): Response<List<Account>>

    @POST("api/accounts/register")
    suspend fun registerAccount(@Body req: RegisterRequest): Response<Account>

    @GET("api/transactions")
    suspend fun getTransactions(): Response<List<Transaction>>

    // Dynamic dynamic authentication endpoints

    @POST("api/auth/otp/send")
    suspend fun sendOtp(@Query("phoneNumber") phoneNumber: String): Response<Map<String, Any>>

    @POST("api/auth/otp/verify")
    suspend fun verifyOtp(@Query("phoneNumber") phoneNumber: String, @Query("otp") otp: String): Response<Map<String, Any>>

    @POST("api/auth/register")
    suspend fun registerUser(@Body req: AuthRegisterRequest): Response<Account>

    @POST("api/auth/login")
    suspend fun loginUser(@Body req: AuthLoginRequest): Response<AuthLoginResponse>

    @POST("api/auth/logout")
    suspend fun logoutUser(@Header("Authorization") token: String): Response<Map<String, Any>>

    @GET("api/auth/session")
    suspend fun getSessionAccount(@Header("Authorization") token: String): Response<Account>

    @POST("api/auth/change-mpin")
    suspend fun changeMpin(
        @Query("vpa") vpa: String,
        @Query("oldMpin") oldMpin: String,
        @Query("newMpin") newMpin: String
    ): Response<Map<String, Any>>

    @POST("api/auth/reset-mpin")
    suspend fun resetMpin(
        @Query("vpa") vpa: String,
        @Query("newMpin") newMpin: String
    ): Response<Map<String, Any>>

    @POST("api/auth/delete-account")
    suspend fun deleteAccount(@Query("vpa") vpa: String): Response<Map<String, Any>>
}

object NetworkModule {
    var baseUrl: String = "http://127.0.0.1:9090/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    fun createApiService(customUrl: String? = null): ApiService {
        val urlToUse = customUrl ?: baseUrl
        return Retrofit.Builder()
            .baseUrl(urlToUse)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
