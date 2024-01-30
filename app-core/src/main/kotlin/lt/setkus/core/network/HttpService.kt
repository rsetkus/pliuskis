package lt.setkus.core.network

import lt.setkus.core.BuildConfig.SERVICE_BASE_URL
import lt.setkus.core.BuildConfig.API_KEY
import lt.setkus.core.BuildConfig.HOST_PATTERN
import lt.setkus.core.BuildConfig.PIN
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object HttpService {

    private val pinner: CertificatePinner by lazy {
        CertificatePinner.Builder()
            .add(HOST_PATTERN, PIN)
            .build()
    }

    private val okhttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val httpUrl = chain.request().url
                    .newBuilder()
                    .addQueryParameter("apikey", API_KEY)
                    .build()

                val request = chain.request().newBuilder().url(httpUrl).build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            .certificatePinner(pinner)
            .build()
    }

    fun getRetrofit() = Retrofit.Builder()
        .client(okhttpClient)
        .baseUrl(SERVICE_BASE_URL)
        .build()
}