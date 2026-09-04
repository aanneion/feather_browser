package com.example.weather

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class WeatherRepository(
    context: Context,
    private val ipLocationProvider: IpLocationProvider = DefaultIpLocationProvider(),
    private val weatherProvider: WeatherProvider = OpenMeteoWeatherProvider()
) {
    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

    companion object {
        private const val PREFS_NAME = "feather_weather_cache"
        private const val KEY_CACHED_WEATHER = "cached_weather_json"
        private const val KEY_CACHED_LOCATION = "cached_location_json"

        // Cache lifetimes
        private const val WEATHER_CACHE_TTL_MS = 20 * 60 * 1000L // 20 minutes
        private const val LOCATION_CACHE_TTL_MS = 4 * 60 * 60 * 1000L // 4 hours
    }

    init {
        // Prime state immediately with cached weather if available
        val cached = loadCachedWeather()
        if (cached != null) {
            val isStale = (System.currentTimeMillis() - cached.timestamp) > WEATHER_CACHE_TTL_MS
            _weatherState.value = if (isStale) WeatherUiState.Offline(cached) else WeatherUiState.Success(cached)
        }
    }

    suspend fun refreshWeather(forceNetwork: Boolean = false) {
        val cached = loadCachedWeather()
        val now = System.currentTimeMillis()

        if (!forceNetwork && cached != null && (now - cached.timestamp) < WEATHER_CACHE_TTL_MS) {
            _weatherState.value = WeatherUiState.Success(cached)
            return
        }

        // If no cached data, display loading indicator
        if (cached == null) {
            _weatherState.value = WeatherUiState.Loading
        }

        try {
            // 1. Get Location (cached or network)
            var location = loadCachedLocation()
            if (location == null || (now - location.timestamp) > LOCATION_CACHE_TTL_MS || forceNetwork) {
                val freshLoc = ipLocationProvider.getLocation()
                if (freshLoc != null) {
                    location = freshLoc
                    saveCachedLocation(freshLoc)
                }
            }

            if (location == null) {
                if (cached != null) {
                    _weatherState.value = WeatherUiState.Offline(cached)
                } else {
                    _weatherState.value = WeatherUiState.Error("Unable to locate approximate area via IP")
                }
                return
            }

            // 2. Fetch Weather for location
            val weather = weatherProvider.getCurrentWeather(
                latitude = location.latitude,
                longitude = location.longitude,
                locationName = location.city,
                countryName = location.country
            )

            if (weather != null) {
                saveCachedWeather(weather)
                _weatherState.value = WeatherUiState.Success(weather)
            } else {
                if (cached != null) {
                    _weatherState.value = WeatherUiState.Offline(cached)
                } else {
                    _weatherState.value = WeatherUiState.Error("Weather forecast currently unavailable")
                }
            }
        } catch (e: Exception) {
            if (cached != null) {
                _weatherState.value = WeatherUiState.Offline(cached)
            } else {
                _weatherState.value = WeatherUiState.Error(e.message ?: "Failed to fetch weather")
            }
        }
    }

    private fun saveCachedWeather(weather: WeatherData) {
        try {
            val json = JSONObject().apply {
                put("locationName", weather.locationName)
                put("countryName", weather.countryName)
                put("temperatureCelsius", weather.temperatureCelsius)
                if (weather.feelsLikeCelsius != null) put("feelsLikeCelsius", weather.feelsLikeCelsius)
                put("condition", weather.condition)
                if (weather.humidityPercent != null) put("humidityPercent", weather.humidityPercent)
                if (weather.windSpeedKmh != null) put("windSpeedKmh", weather.windSpeedKmh)
                put("weatherIcon", weather.weatherIcon.name)
                put("timestamp", weather.timestamp)
            }
            sharedPrefs.edit().putString(KEY_CACHED_WEATHER, json.toString()).apply()
        } catch (e: Exception) {
            // Safe ignore
        }
    }

    fun loadCachedWeather(): WeatherData? {
        val raw = sharedPrefs.getString(KEY_CACHED_WEATHER, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val iconName = json.optString("weatherIcon", WeatherIcon.CLEAR_DAY.name)
            val icon = try { WeatherIcon.valueOf(iconName) } catch (e: Exception) { WeatherIcon.CLEAR_DAY }

            WeatherData(
                locationName = json.optString("locationName", "Local Area"),
                countryName = json.optString("countryName", ""),
                temperatureCelsius = json.optDouble("temperatureCelsius", 20.0),
                feelsLikeCelsius = if (json.has("feelsLikeCelsius")) json.optDouble("feelsLikeCelsius") else null,
                condition = json.optString("condition", "Clear"),
                humidityPercent = if (json.has("humidityPercent")) json.optInt("humidityPercent") else null,
                windSpeedKmh = if (json.has("windSpeedKmh")) json.optDouble("windSpeedKmh") else null,
                weatherIcon = icon,
                timestamp = json.optLong("timestamp", 0L)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun saveCachedLocation(loc: IpLocation) {
        try {
            val json = JSONObject().apply {
                put("city", loc.city)
                if (loc.region != null) put("region", loc.region)
                put("country", loc.country)
                put("lat", loc.latitude)
                put("lon", loc.longitude)
                put("timestamp", loc.timestamp)
            }
            sharedPrefs.edit().putString(KEY_CACHED_LOCATION, json.toString()).apply()
        } catch (e: Exception) {
            // Safe ignore
        }
    }

    private fun loadCachedLocation(): IpLocation? {
        val raw = sharedPrefs.getString(KEY_CACHED_LOCATION, null) ?: return null
        return try {
            val json = JSONObject(raw)
            IpLocation(
                city = json.optString("city", "Local Area"),
                region = json.optString("region", "").ifBlank { null },
                country = json.optString("country", ""),
                latitude = json.optDouble("lat", 0.0),
                longitude = json.optDouble("lon", 0.0),
                timestamp = json.optLong("timestamp", 0L)
            )
        } catch (e: Exception) {
            null
        }
    }
}
