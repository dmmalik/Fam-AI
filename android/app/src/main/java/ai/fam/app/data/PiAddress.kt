package ai.fam.app.data

import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException

data class PiAddress(
    val scheme: String,
    val host: String,
    val port: Int,
) {
    val pinLiteral: String? = if (isIpLiteral(host)) host else null

    fun requestHost(): String = if (pinLiteral != null) "farm-ai.local" else host

    fun origin(): String = "$scheme://$host:$port"

    companion object {
        const val DEFAULT = "http://farm-ai.local:8080"

        fun parse(raw: String): PiAddress? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            val withScheme = if ("://" in text) text else "http://$text"
            val uri = try {
                URI(withScheme)
            } catch (_: URISyntaxException) {
                return null
            }
            if (uri.userInfo != null) return null
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.trim()?.lowercase() ?: return null
            if (host.isEmpty() || host == "0.0.0.0" || host == "::") return null
            if (!isLocalHost(host)) return null
            val port = when {
                uri.port in 1..65535 -> uri.port
                scheme == "https" -> 443
                else -> 8080
            }
            return PiAddress(scheme, host, port)
        }

        fun isLocalHost(host: String): Boolean {
            if (host == "localhost") return true
            if (host.endsWith(".local")) return true
            return isPrivateIpLiteral(host)
        }

        fun isIpLiteral(host: String): Boolean {
            return IPV4.matches(host) || host.contains(':')
        }

        fun isPrivateIpLiteral(host: String): Boolean {
            val address = when {
                IPV4.matches(host) -> parseIpv4(host) ?: return false
                host.contains(':') -> parseIpv6(host) ?: return false
                else -> return false
            }
            return isPrivateAddress(address)
        }

        fun isPrivateAddress(address: InetAddress): Boolean {
            if (address.isLoopbackAddress) return true
            val bytes = address.address
            if (bytes.size == 4) {
                val first = bytes[0].toInt() and 0xff
                val second = bytes[1].toInt() and 0xff
                if (first == 10) return true
                if (first == 172 && second in 16..31) return true
                if (first == 192 && second == 168) return true
                if (first == 169 && second == 254) return true
                return false
            }
            if (address.isLinkLocalAddress) return true
            val first = bytes[0].toInt() and 0xff
            return first and 0xfe == 0xfc
        }

        private fun parseIpv4(host: String): InetAddress? {
            val parts = host.split('.')
            if (parts.size != 4) return null
            val bytes = ByteArray(4)
            for (index in 0..3) {
                val number = parts[index].toIntOrNull() ?: return null
                if (number !in 0..255) return null
                bytes[index] = number.toByte()
            }
            return InetAddress.getByAddress(bytes)
        }

        private fun parseIpv6(host: String): InetAddress? {
            val bare = host.removePrefix("[").removeSuffix("]")
            if (!bare.contains(':')) return null
            return try {
                val address = InetAddress.getByName(bare)
                if (address.address.size != 16) null else address
            } catch (_: Exception) {
                null
            }
        }

        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    }
}
