package com.clockweather.app.di

import com.clockweather.app.BuildConfig
import com.clockweather.app.data.remote.api.GoogleWeatherApi
import com.clockweather.app.data.remote.api.NominatimReverseGeocodingApi
import com.clockweather.app.data.remote.api.OpenMeteoGeocodingApi
import com.clockweather.app.data.remote.api.OpenMeteoWeatherApi
import com.clockweather.app.data.remote.api.OpenWeatherMapApi
import com.clockweather.app.data.remote.api.WeatherApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    // BODY logging floods Logcat with full weather payloads and can make
                    // the debug console look like the app is crashing. Keep request/response
                    // visibility in debug builds without dumping entire payloads.
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .build()

    @Provides
    @Singleton
    @Named("weather")
    fun provideWeatherRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(OpenMeteoWeatherApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    @Named("geocoding")
    fun provideGeocodingRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(OpenMeteoGeocodingApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    @Named("reverseGeocoding")
    fun provideReverseGeocodingRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(NominatimReverseGeocodingApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    @Named("weatherapi")
    fun provideWeatherApiRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(WeatherApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    @Named("openweathermap")
    fun provideOpenWeatherMapRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(OpenWeatherMapApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideOpenMeteoWeatherApi(@Named("weather") retrofit: Retrofit): OpenMeteoWeatherApi =
        retrofit.create(OpenMeteoWeatherApi::class.java)

    @Provides
    @Singleton
    fun provideGeocodingApi(@Named("geocoding") retrofit: Retrofit): OpenMeteoGeocodingApi =
        retrofit.create(OpenMeteoGeocodingApi::class.java)

    @Provides
    @Singleton
    fun provideReverseGeocodingApi(@Named("reverseGeocoding") retrofit: Retrofit): NominatimReverseGeocodingApi =
        retrofit.create(NominatimReverseGeocodingApi::class.java)

    @Provides
    @Singleton
    fun provideWeatherApi(@Named("weatherapi") retrofit: Retrofit): WeatherApi =
        retrofit.create(WeatherApi::class.java)

    @Provides
    @Singleton
    @Named("weatherApiKey")
    fun provideWeatherApiKey(): String = BuildConfig.WEATHER_API_KEY

    @Provides
    @Singleton
    @Named("googleweather")
    fun provideGoogleWeatherRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(GoogleWeatherApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideGoogleWeatherApi(@Named("googleweather") retrofit: Retrofit): GoogleWeatherApi =
        retrofit.create(GoogleWeatherApi::class.java)

    @Provides
    @Singleton
    @Named("googleWeatherApiKey")
    fun provideGoogleWeatherApiKey(): String = BuildConfig.GOOGLE_WEATHER_API_KEY

    @Provides
    @Singleton
    fun provideOpenWeatherMapApi(@Named("openweathermap") retrofit: Retrofit): OpenWeatherMapApi =
        retrofit.create(OpenWeatherMapApi::class.java)

    @Provides
    @Singleton
    @Named("openWeatherMapApiKey")
    fun provideOpenWeatherMapApiKey(): String = BuildConfig.OPENWEATHERMAP_API_KEY
}

