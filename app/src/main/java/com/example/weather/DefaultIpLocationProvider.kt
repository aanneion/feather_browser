package com.example.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class DefaultIpLocationProvider : IpLocationProvider {

    override suspend fun getLocation(): IpLocation? {
        return withContext(Dispatchers.IO) {
            // First attempt: ip-api.com (most granular ISP/campus city database, accurately identifies Mymensingh for BAU)
            val ipApiResult = fetchFromIpApiCom()
            if (ipApiResult != null) return@withContext ipApiResult

            // Second attempt: ipwho.is (fast, HTTPS fallback with coordinate refinement)
            val ipWhoResult = fetchFromIpWhoIs()
            if (ipWhoResult != null) return@withContext ipWhoResult

            // Third attempt: get.geojs.io (unlimited, fast, HTTPS fallback with coordinate refinement)
            fetchFromGeoJs()
        }
    }

    private fun fetchFromIpApiCom(): IpLocation? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("http://ip-api.com/json/?fields=status,message,country,countryCode,region,regionName,city,district,zip,lat,lon,timezone,isp,org,as,query")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "FeatherBrowser/1.0 (Android; Mobile)")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(response)
                if (json.optString("status") == "success") {
                    var city = json.optString("city", "").ifBlank { json.optString("district", "Local Area") }
                    val region = json.optString("regionName", "")
                    val country = json.optString("country", "")
                    val lat = json.optDouble("lat", 0.0)
                    val lon = json.optDouble("lon", 0.0)

                    // Refine city with reverse geocoding if available to ensure exact district/city accuracy
                    city = refineLocationCity(lat, lon, city)

                    return IpLocation(
                        city = city.ifBlank { "Local Area" },
                        region = region.ifBlank { null },
                        country = country,
                        latitude = lat,
                        longitude = lon,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchFromIpWhoIs(): IpLocation? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://ipwho.is/")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "FeatherBrowser/1.0 (Android; Mobile)")
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = true
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(response)
                if (json.optBoolean("success", false)) {
                    var city = json.optString("city", "Local Area").ifBlank { "Local Area" }
                    val region = json.optString("region", "")
                    val country = json.optString("country", "")
                    val lat = json.optDouble("latitude", 0.0)
                    val lon = json.optDouble("longitude", 0.0)
                    if (lat != 0.0 || lon != 0.0) {
                        city = refineLocationCity(lat, lon, city)
                        return IpLocation(
                            city = city.ifBlank { "Local Area" },
                            region = region.ifBlank { null },
                            country = country,
                            latitude = lat,
                            longitude = lon,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchFromGeoJs(): IpLocation? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://get.geojs.io/v1/ip/geo.json")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "FeatherBrowser/1.0 (Android; Mobile)")
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = true
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(response)
                val latStr = json.optString("latitude", "")
                val lonStr = json.optString("longitude", "")
                val lat = latStr.toDoubleOrNull() ?: 0.0
                val lon = lonStr.toDoubleOrNull() ?: 0.0
                if (lat != 0.0 || lon != 0.0) {
                    var city = json.optString("city", "Local Area").ifBlank { "Local Area" }
                    val region = json.optString("region", "")
                    val country = json.optString("country", "")
                    city = refineLocationCity(lat, lon, city)
                    return IpLocation(
                        city = city.ifBlank { "Local Area" },
                        region = region.ifBlank { null },
                        country = country,
                        latitude = lat,
                        longitude = lon,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun refineLocationCity(lat: Double, lon: Double, fallbackCity: String): String {
        if (lat == 0.0 && lon == 0.0) return fallbackCity
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(String.format(Locale.US, "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.4f&longitude=%.4f&localityLanguage=en", lat, lon))
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
                setRequestProperty("User-Agent", "FeatherBrowser/1.0 (Android; Mobile)")
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val res = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(res)
                val refined = json.optString("city", "").ifBlank {
                    json.optString("locality", "").ifBlank {
                        json.optString("principalSubdivision", "")
                    }
                }
                if (refined.isNotBlank()) {
                    return refined
                }
            }
            fallbackCity
        } catch (e: Exception) {
            fallbackCity
        } finally {
            connection?.disconnect()
        }
    }
}
