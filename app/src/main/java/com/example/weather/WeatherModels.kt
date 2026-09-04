package com.example.weather

enum class WeatherIcon {
    CLEAR_DAY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY_DAY,
    PARTLY_CLOUDY_NIGHT,
    CLOUDY,
    RAIN,
    THUNDERSTORM,
    SNOW,
    MIST_OR_FOG
}

data class WeatherData(
    val locationName: String,
    val countryName: String,
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double?,
    val condition: String,
    val humidityPercent: Int?,
    val windSpeedKmh: Double?,
    val weatherIcon: WeatherIcon,
    val timestamp: Long
)

data class IpLocation(
    val city: String,
    val region: String?,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Success(val weather: WeatherData) : WeatherUiState
    data class Offline(val weather: WeatherData?) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}
