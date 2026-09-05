package com.example.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

class DefaultIpLocationProvider : IpLocationProvider {

    override suspend fun getLocation(): IpLocation? {
        return withContext(Dispatchers.IO) {
            // First attempt: ipwho.is (fast, HTTPS, generous limits, high accuracy)
            val ipWhoResult = fetchFromIpWhoIs()
            if (ipWhoResult != null) return@withContext ipWhoResult

            // Second attempt: get.geojs.io (unlimited, fast, HTTPS fallback)
            val geoJsResult = fetchFromGeoJs()
            if (geoJsResult != null) return@withContext geoJsResult

            // Final fallback: ip-api.com (HTTP fallback)
            fetchFromIpApiCom()
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
                    val city = json.optString("city", "Local Area").ifBlank { "Local Area" }
                    val region = json.optString("region", "")
                    val country = json.optString("country", "")
                    val lat = json.optDouble("latitude", 0.0)
                    val lon = json.optDouble("longitude", 0.0)
                    if (lat != 0.0 || lon != 0.0) {
                        return IpLocation(
                            city = city,
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
                    val city = json.optString("city", "Local Area").ifBlank { "Local Area" }
                    val region = json.optString("region", "")
                    val country = json.optString("country", "")
                    return IpLocation(
                        city = city,
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

    private fun fetchFromIpApiCom(): IpLocation? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("http://ip-api.com/json/")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "FeatherBrowser/1.0 (Android; Mobile)")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(response)
                if (json.optString("status") == "success") {
                    val city = json.optString("city", "Local Area").ifBlank { "Local Area" }
                    val region = json.optString("regionName", "")
                    val country = json.optString("country", "")
                    val lat = json.optDouble("lat", 0.0)
                    val lon = json.optDouble("lon", 0.0)
                    return IpLocation(
                        city = city,
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
}
