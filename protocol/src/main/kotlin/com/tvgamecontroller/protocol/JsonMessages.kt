package com.tvgamecontroller.protocol

object Protocol {
    const val VERSION = 1
    const val DEFAULT_PORT = 9842
    const val SERVICE_TYPE = "_tvgamepad._tcp."
    const val SERVICE_NAME = "TV Gamepad"
}

data class HelloMessage(
    val type: String = "hello",
    val role: String = "controller",
    val name: String,
    val protocol: Int = Protocol.VERSION,
)

data class AuthMessage(
    val type: String = "auth",
    val pin: String,
)

data class WelcomeMessage(
    val type: String = "welcome",
    val pinRequired: Boolean,
    val serverName: String,
    val protocol: Int = Protocol.VERSION,
)

data class ReadyMessage(
    val type: String = "ready",
    val serverName: String,
)

data class ErrorMessage(
    val type: String = "error",
    val message: String,
)

data class RumbleMessage(
    val type: String = "rumble",
    val low: Float,
    val high: Float,
    val ms: Int,
)

object JsonCodec {
    fun hello(name: String): String =
        """{"type":"hello","role":"controller","name":${quote(name)},"protocol":${Protocol.VERSION}}"""

    fun auth(pin: String): String =
        """{"type":"auth","pin":${quote(pin)}}"""

    fun welcome(pinRequired: Boolean, serverName: String): String =
        """{"type":"welcome","pinRequired":$pinRequired,"serverName":${quote(serverName)},"protocol":${Protocol.VERSION}}"""

    fun ready(serverName: String): String =
        """{"type":"ready","serverName":${quote(serverName)}}"""

    fun error(message: String): String =
        """{"type":"error","message":${quote(message)}}"""

    fun rumble(low: Float, high: Float, ms: Int): String =
        """{"type":"rumble","low":$low,"high":$high,"ms":$ms}"""

    fun stateJson(state: GamepadState): String {
        val s = state.clamped()
        return buildString {
            append("{\"type\":\"state\"")
            append(",\"seq\":").append(s.seq)
            append(",\"lx\":").append(fmt(s.leftStickX))
            append(",\"ly\":").append(fmt(s.leftStickY))
            append(",\"rx\":").append(fmt(s.rightStickX))
            append(",\"ry\":").append(fmt(s.rightStickY))
            append(",\"lt\":").append(fmt(s.leftTrigger))
            append(",\"rt\":").append(fmt(s.rightTrigger))
            append(",\"buttons\":").append(s.buttons)
            append(",\"hat\":").append(s.hat)
            append(",\"gx\":").append(fmt(s.gyroX))
            append(",\"gy\":").append(fmt(s.gyroY))
            append(",\"gz\":").append(fmt(s.gyroZ))
            append(",\"ax\":").append(fmt(s.accelX))
            append(",\"ay\":").append(fmt(s.accelY))
            append(",\"az\":").append(fmt(s.accelZ))
            append(",\"motion\":").append(s.motionEnabled)
            append('}')
        }
    }

    fun parseState(json: String): GamepadState? {
        if (!json.contains("\"type\":\"state\"")) return null
        return GamepadState(
            seq = longField(json, "seq") ?: 0L,
            leftStickX = floatField(json, "lx") ?: 0f,
            leftStickY = floatField(json, "ly") ?: 0f,
            rightStickX = floatField(json, "rx") ?: 0f,
            rightStickY = floatField(json, "ry") ?: 0f,
            leftTrigger = floatField(json, "lt") ?: 0f,
            rightTrigger = floatField(json, "rt") ?: 0f,
            buttons = intField(json, "buttons") ?: 0,
            hat = intField(json, "hat") ?: Hat.NEUTRAL,
            gyroX = floatField(json, "gx") ?: 0f,
            gyroY = floatField(json, "gy") ?: 0f,
            gyroZ = floatField(json, "gz") ?: 0f,
            accelX = floatField(json, "ax") ?: 0f,
            accelY = floatField(json, "ay") ?: 0f,
            accelZ = floatField(json, "az") ?: 0f,
            motionEnabled = boolField(json, "motion") ?: false,
        ).clamped()
    }

    fun messageType(json: String): String? = stringField(json, "type")

    fun stringField(json: String, key: String): String? {
        val needle = "\"$key\""
        val keyIndex = json.indexOf(needle)
        if (keyIndex < 0) return null
        var i = json.indexOf(':', keyIndex + needle.length)
        if (i < 0) return null
        i += 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length || json[i] != '"') return null
        i++
        val start = i
        while (i < json.length && json[i] != '"') {
            if (json[i] == '\\') i++
            i++
        }
        if (i > json.length) return null
        return json.substring(start, i).replace("\\\"", "\"").replace("\\\\", "\\")
    }

    fun intField(json: String, key: String): Int? = numberToken(json, key)?.toIntOrNull()

    fun longField(json: String, key: String): Long? = numberToken(json, key)?.toLongOrNull()

    fun floatField(json: String, key: String): Float? = numberToken(json, key)?.toFloatOrNull()

    fun boolField(json: String, key: String): Boolean? {
        val token = rawToken(json, key) ?: return null
        return when (token) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun numberToken(json: String, key: String): String? {
        val token = rawToken(json, key) ?: return null
        return token.takeWhile { it == '-' || it == '+' || it == '.' || it.isDigit() || it == 'e' || it == 'E' }
    }

    private fun rawToken(json: String, key: String): String? {
        val needle = "\"$key\""
        val keyIndex = json.indexOf(needle)
        if (keyIndex < 0) return null
        var i = json.indexOf(':', keyIndex + needle.length)
        if (i < 0) return null
        i += 1
        while (i < json.length && json[i].isWhitespace()) i++
        if (i >= json.length) return null
        val start = i
        while (i < json.length && json[i] != ',' && json[i] != '}' && !json[i].isWhitespace()) i++
        return json.substring(start, i)
    }

    fun quote(value: String): String =
        buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(ch)
                }
            }
            append('"')
        }

    private fun fmt(value: Float): String = String.format(java.util.Locale.US, "%.5f", value)
}

object Pairing {
    fun generatePin(seed: Int = (System.currentTimeMillis() % 9000).toInt() + 1000): String =
        seed.coerceIn(1000, 9999).toString()

    fun connectUri(host: String, port: Int = Protocol.DEFAULT_PORT, pin: String): String =
        "tvgamepad://connect?host=$host&port=$port&pin=$pin"

    fun parseConnectUri(uri: String): Triple<String, Int, String>? {
        val clean = uri.trim()
        if (!clean.startsWith("tvgamepad://connect")) return null
        val query = clean.substringAfter('?', "")
        val params = query.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
        }.toMap()
        val host = params["host"] ?: return null
        val port = params["port"]?.toIntOrNull() ?: Protocol.DEFAULT_PORT
        val pin = params["pin"] ?: ""
        return Triple(host, port, pin)
    }
}
