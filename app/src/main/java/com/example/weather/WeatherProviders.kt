package com.example.weather

interface IpLocationProvider {
    suspend fun getLocation(): IpLocation?
}

interface WeatherProvider {
    suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double,
        locationName: String,
        countryName: String
    ): WeatherData?
}
