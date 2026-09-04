package com.example.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class OpenMeteoWeatherProvider : WeatherProvider {

    override suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double,
        locationName: String,
        countryName: String
    ): WeatherData? {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                // Open-Meteo URL requesting current temperature, apparent_temperature, relative_humidity_2m, weather_code, wind_speed_10m, is_day
                val urlString = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m&wind_speed_unit=kmh",
                    latitude,
                    longitude
                )

                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "FeatherBrowser/1.0 (Android; Mobile)")
                    setRequestProperty("Accept", "application/json")
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }

                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val rootJson = JSONObject(response)
                val current = rootJson.optJSONObject("current") ?: return@withContext null

                val temp = current.optDouble("temperature_2m", 25.0)
                val feelsLike = if (current.has("apparent_temperature")) current.optDouble("apparent_temperature") else null
                val humidity = if (current.has("relative_humidity_2m")) current.optInt("relative_humidity_2m") else null
                val windSpeed = if (current.has("wind_speed_10m")) current.optDouble("wind_speed_10m") else null
                val weatherCode = current.optInt("weather_code", 0)
                val isDay = current.optInt("is_day", 1) == 1

                val (conditionName, icon) = mapWmoCode(weatherCode, isDay)

                WeatherData(
                    locationName = locationName,
                    countryName = countryName,
                    temperatureCelsius = temp,
                    feelsLikeCelsius = feelsLike,
                    condition = conditionName,
                    humidityPercent = humidity,
                    windSpeedKmh = windSpeed,
                    weatherIcon = icon,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun mapWmoCode(code: Int, isDay: Boolean): Pair<String, WeatherIcon> {
        return when (code) {
            0 -> Pair("Clear Sky", if (isDay) WeatherIcon.CLEAR_DAY else WeatherIcon.CLEAR_NIGHT)
            1 -> Pair("Mainly Clear", if (isDay) WeatherIcon.CLEAR_DAY else WeatherIcon.CLEAR_NIGHT)
            2 -> Pair("Partly Cloudy", if (isDay) WeatherIcon.PARTLY_CLOUDY_DAY else WeatherIcon.PARTLY_CLOUDY_NIGHT)
            3 -> Pair("Overcast", WeatherIcon.CLOUDY)
            45, 48 -> Pair("Foggy", WeatherIcon.MIST_OR_FOG)
            51, 53, 55 -> Pair("Drizzle", WeatherIcon.RAIN)
            56, 57 -> Pair("Freezing Drizzle", WeatherIcon.SNOW)
            61, 63 -> Pair("Rain", WeatherIcon.RAIN)
            65 -> Pair("Heavy Rain", WeatherIcon.RAIN)
            66, 67 -> Pair("Freezing Rain", WeatherIcon.SNOW)
            71, 73, 75 -> Pair("Snow Fall", WeatherIcon.SNOW)
            77 -> Pair("Snow Grains", WeatherIcon.SNOW)
            80, 81, 82 -> Pair("Rain Showers", WeatherIcon.RAIN)
            85, 86 -> Pair("Snow Showers", WeatherIcon.SNOW)
            95 -> Pair("Thunderstorm", WeatherIcon.THUNDERSTORM)
            96, 99 -> Pair("Thunderstorm & Hail", WeatherIcon.THUNDERSTORM)
            else -> Pair("Fair", if (isDay) WeatherIcon.CLEAR_DAY else WeatherIcon.CLEAR_NIGHT)
        }
    }
}
