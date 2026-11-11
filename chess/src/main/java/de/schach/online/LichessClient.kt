package de.schach.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

class LichessException(message: String, val code: Int = 0) : IOException(message)

/**
 * Minimaler Client für die Lichess Board-API (https://lichess.org/api#tag/Board).
 * Damit spielt man über die App echte Partien gegen Menschen auf lichess.org.
 */
class LichessClient(private val token: String) {

    data class Account(val id: String, val username: String, val ratings: Map<String, Int>)

    data class OngoingGame(val gameId: String, val opponent: String, val isMyTurn: Boolean, val color: String, val speed: String)

    suspend fun account(): Account {
        val json = JSONObject(request("GET", "/api/account"))
        val ratings = HashMap<String, Int>()
        json.optJSONObject("perfs")?.let { perfs ->
            for (key in listOf("blitz", "rapid", "classical", "correspondence")) {
                perfs.optJSONObject(key)?.optInt("rating")?.takeIf { it > 0 }?.let { ratings[key] = it }
            }
        }
        return Account(json.getString("id"), json.getString("username"), ratings)
    }

    suspend fun ongoingGames(): List<OngoingGame> {
        val arr = JSONObject(request("GET", "/api/account/playing")).optJSONArray("nowPlaying") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val g = arr.getJSONObject(i)
            OngoingGame(
                gameId = g.getString("gameId"),
                opponent = g.optJSONObject("opponent")?.optString("username").orEmpty().ifEmpty { "Gegner" },
                isMyTurn = g.optBoolean("isMyTurn"),
                color = g.optString("color"),
                speed = g.optString("speed"),
            )
        }
    }

    /** Ereignisse des Kontos: Partiebeginn, eingehende Herausforderungen … */
    fun events(): Flow<JSONObject> = ndjson("GET", "/api/stream/event")

    /** Partieverlauf: zuerst „gameFull“, danach „gameState“ bei jedem Zug. */
    fun gameStream(gameId: String): Flow<JSONObject> = ndjson("GET", "/api/board/game/stream/$gameId")

    /**
     * Sucht einen zufälligen Gegner. Die Suche läuft, solange der Flow gesammelt wird;
     * Abbrechen des Sammelns bricht die Suche ab. Der Partiebeginn kommt über [events].
     */
    fun seek(minutes: Int, increment: Int, rated: Boolean): Flow<JSONObject> = ndjson(
        "POST", "/api/board/seek",
        mapOf("rated" to rated.toString(), "time" to minutes.toString(), "increment" to increment.toString(), "variant" to "standard", "color" to "random"),
    )

    suspend fun challenge(username: String, minutes: Int, increment: Int, rated: Boolean, color: String): String {
        val json = JSONObject(
            request(
                "POST", "/api/challenge/${enc(username)}",
                mapOf(
                    "rated" to rated.toString(),
                    "clock.limit" to (minutes * 60).toString(),
                    "clock.increment" to increment.toString(),
                    "color" to color,
                    "variant" to "standard",
                ),
            ),
        )
        return json.optJSONObject("challenge")?.optString("id") ?: json.optString("id")
    }

    suspend fun cancelChallenge(id: String) { request("POST", "/api/challenge/$id/cancel") }
    suspend fun acceptChallenge(id: String) { request("POST", "/api/challenge/$id/accept") }
    suspend fun declineChallenge(id: String) { request("POST", "/api/challenge/$id/decline") }

    suspend fun move(gameId: String, uci: String) { request("POST", "/api/board/game/$gameId/move/$uci") }
    suspend fun resign(gameId: String) { request("POST", "/api/board/game/$gameId/resign") }
    suspend fun abort(gameId: String) { request("POST", "/api/board/game/$gameId/abort") }
    suspend fun draw(gameId: String, accept: Boolean) { request("POST", "/api/board/game/$gameId/draw/${if (accept) "yes" else "no"}") }
    suspend fun claimVictory(gameId: String) { request("POST", "/api/board/game/$gameId/claim-victory") }

    // ---------------------------------------------------------------- HTTP

    private fun open(method: String, path: String, form: Map<String, String>?, streaming: Boolean): HttpURLConnection {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = if (streaming) 0 else 20_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", if (streaming) "application/x-ndjson" else "application/json")
        if (method == "POST") {
            conn.doOutput = true
            val body = form.orEmpty().entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }.toByteArray()
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        }
        return conn
    }

    private fun check(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code in 200..299) return
        val body = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
        val msg = runCatching { JSONObject(body).let { it.optString("error").ifEmpty { it.toString() } } }.getOrDefault(body)
        throw LichessException(
            when (code) {
                401 -> "Der Lichess-Zugangsschlüssel ist ungültig oder abgelaufen."
                403 -> "Lichess erlaubt das mit diesem Schlüssel nicht ($msg)."
                429 -> "Zu viele Anfragen – bitte eine Minute warten."
                else -> msg.ifEmpty { "Lichess-Fehler $code" }
            },
            code,
        )
    }

    private suspend fun request(method: String, path: String, form: Map<String, String>? = null): String =
        withContext(Dispatchers.IO) {
            val conn = open(method, path, form, streaming = false)
            try {
                check(conn)
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

    /** Liest eine NDJSON-Antwort Zeile für Zeile. Leere Zeilen (Keep-Alive) werden ignoriert. */
    private fun ndjson(method: String, path: String, form: Map<String, String>? = null): Flow<JSONObject> = callbackFlow {
        val conn = AtomicReference<HttpURLConnection?>(null)
        launch(Dispatchers.IO) {
            try {
                val c = open(method, path, form, streaming = true)
                conn.set(c)
                check(c)
                c.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        trySend(JSONObject(line))
                    }
                }
                close()
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose { runCatching { conn.get()?.disconnect() } }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val BASE = "https://lichess.org"

        /** Seite zum Erstellen eines persönlichen Zugangsschlüssels mit den nötigen Rechten */
        const val TOKEN_URL = "$BASE/account/oauth/token/create?scopes[]=board:play&scopes[]=challenge:read" +
            "&scopes[]=challenge:write&description=Schach-App"

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}
