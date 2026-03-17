package com.mazda.control

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Диагностика сети без root прав
 * Проверка доступности сервера Fake32960Server
 */
class NetworkDiagnostics(
    private val context: Context,
    private val logger: ((String) -> Unit)? = null
) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    data class DiagnosticResult(
        val interfaceName: String,
        val ipAddress: String,
        val canReachHost: Boolean,
        val portOpen: Boolean,
        val responseTimeMs: Long
    )

    data class NetworkInterfaceInfo(
        val name: String,
        val displayName: String,
        val ipAddress: String,
        val isUp: Boolean
    )

    /**
     * Получить все сетевые интерфейсы
     */
    fun getNetworkInterfaces(): List<NetworkInterfaceInfo> {
        val interfaces = mutableListOf<NetworkInterfaceInfo>()
        
        try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val iface = networkInterfaces.nextElement()
                val addresses = iface.inetAddresses
                
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        interfaces.add(
                            NetworkInterfaceInfo(
                                name = iface.name,
                                displayName = iface.displayName ?: iface.name,
                                ipAddress = addr.hostAddress ?: "unknown",
                                isUp = iface.isUp
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get network interfaces", e)
        }
        
        return interfaces
    }

    /**
     * Проверка доступности хоста через ping (InetAddress.isReachable)
     * Работает без root на некоторых устройствах
     */
    fun pingHost(host: String, timeoutMs: Int = 3000): Boolean {
        return try {
            val address = InetAddress.getByName(host)
            val reachable = address.isReachable(timeoutMs)
            Log.d(TAG, "Ping $host: $reachable (timeout=${timeoutMs}ms)")
            reachable
        } catch (e: Exception) {
            Log.e(TAG, "Ping failed for $host", e)
            false
        }
    }

    /**
     * Проверка доступности порта через TCP socket connection
     * Это более надёжный способ без root
     */
    fun checkPort(host: String, port: Int, timeoutMs: Int = 3000): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(
                InetSocketAddress(host, port),
                timeoutMs
            )
            Log.d(TAG, "Port $host:$port - OPEN")
            true
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Port $host:$port - TIMEOUT", e)
            false
        } catch (e: IOException) {
            Log.e(TAG, "Port $host:$port - CLOSED (${e.message})", e)
            false
        } finally {
            try {
                socket?.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }

    /**
     * Комплексная проверка сервера
     */
    fun diagnoseServer(host: String, port: Int): ServerDiagnosticReport {
        val startTime = System.currentTimeMillis()
        
        val interfaces = getNetworkInterfaces()
        val pingResult = pingHost(host, 3000)
        val portResult = checkPort(host, port, 3000)
        val activeNetwork = getActiveNetworkInfo()
        
        val endTime = System.currentTimeMillis()
        
        return ServerDiagnosticReport(
            host = host,
            port = port,
            timestamp = startTime,
            durationMs = endTime - startTime,
            networkInterfaces = interfaces,
            activeNetworkType = activeNetwork,
            pingSuccess = pingResult,
            portOpen = portResult,
            canConnect = pingResult || portResult
        )
    }

    /**
     * Получить информацию об активной сети
     */
    fun getActiveNetworkInfo(): String {
        return try {
            val network = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(network)
            
            when {
                caps == null -> "NO_ACTIVE_NETWORK"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "UNKNOWN (${caps})"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active network", e)
            "ERROR"
        }
    }

    /**
     * Сканирование диапазона IP адресов
     * Ищет активные хосты в диапазоне
     */
    fun scanIpRange(
        baseIp: String, // например "172.16.2"
        range: IntRange = 1..50,
        timeoutMs: Int = 1000
    ): List<String> {
        val activeHosts = mutableListOf<String>()

        for (i in range) {
            val ip = "$baseIp.$i"
            if (pingHost(ip, timeoutMs)) {
                activeHosts.add(ip)
                logScan("    ├─ PING $ip: ✅")
            }
            // Небольшая задержка между пингами
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                break
            }
        }
        
        if (activeHosts.isEmpty()) {
            logScan("    ❌ No responsive hosts in $baseIp.1-$baseIp.${range.last}")
        }

        return activeHosts
    }

    /**
     * Проверка портов на найденном хосте
     * Сканирует популярные порты
     */
    fun scanPorts(host: String, ports: List<Int> = listOf(22, 80, 443, 50001, 50002, 8080)): Map<Int, Boolean> {
        val results = mutableMapOf<Int, Boolean>()

        for (port in ports) {
            val isOpen = checkPort(host, port, 2000)
            results[port] = isOpen
            if (isOpen) {
                logScan("      ├─ Port $port: ✅ OPEN")
            }
        }

        return results
    }

    /**
     * Полное сканирование сети на предмет TBox сервисов
     * Сканирует подсети и порты, ищет открытые сервисы
     */
    fun scanForTBoxServices(): List<TBoxServiceInfo> {
        val tboxServices = mutableListOf<TBoxServiceInfo>()
        
        // Типичные подсети для автомобильных систем
        val subnets = listOf(
            "127.0.0",      // localhost
            "192.168.0",    // локальная сеть
            "192.168.1",
            "172.16.0",     // автомобильная сеть
            "172.16.1",
            "172.16.2",
            "10.0.0"
        )
        
        // Порты для проверки
        val tboxPorts = listOf(
            32960,  // Известный порт Fake32960Server
            50000, 50001, 50002, 50010, 50050,  // Автомобильные сервисы
            8080, 8888,  // HTTP сервисы
            1337, 31337,  // Кастомные сервисы
            22  // SSH (если есть root)
        )
        
        logScan("🔍 Starting TBox service scan...")
        logScan("═══════════════════════════════════════")
        
        for (subnet in subnets) {
            logScan("📋 Scanning subnet: $subnet.x")
            val hosts = scanIpRange(subnet, 1..50, 500)
            
            if (hosts.isEmpty()) {
                logScan("  ℹ️  No active hosts in $subnet.x")
            } else {
                logScan("  ✅ Found ${hosts.size} active hosts in $subnet.x")
            }
            
            for (host in hosts) {
                logScan("  🔍 Checking host: $host")
                val openPorts = scanPorts(host, tboxPorts)
                val openPortList = openPorts.filter { it.value }.keys
                
                if (openPortList.isNotEmpty()) {
                    logScan("  ✅ FOUND $host with open ports: $openPortList")
                    
                    openPortList.forEach { port ->
                        tboxServices.add(
                            TBoxServiceInfo(
                                host = host,
                                port = port,
                                subnet = subnet
                            )
                        )
                        logScan("    └─ $host:$port ADDED")
                    }
                } else {
                    logScan("  ❌ $host - no open ports")
                }
            }
        }
        
        logScan("═══════════════════════════════════════")
        logScan("Scan complete. Found ${tboxServices.size} potential TBox services")
        return tboxServices
    }
    
    /**
     * Вспомогательная функция для логирования
     */
    private fun logScan(message: String) {
        Log.d(TAG, message)
        logger?.invoke(message)
    }

    companion object {
        private const val TAG = "NetworkDiagnostics"
    }
}

/**
 * Отчёт о диагностике сервера
 */
data class ServerDiagnosticReport(
    val host: String,
    val port: Int,
    val timestamp: Long,
    val durationMs: Long,
    val networkInterfaces: List<NetworkDiagnostics.NetworkInterfaceInfo>,
    val activeNetworkType: String,
    val pingSuccess: Boolean,
    val portOpen: Boolean,
    val canConnect: Boolean
) {
    fun toUiString(): String {
        val sb = StringBuilder()
        sb.appendLine("🔍 ОТЧЁТ О ДИАГНОСТИКЕ СЕРВЕРА")
        sb.appendLine("═══════════════════════════════════════")
        sb.appendLine("📍 Сервер: $host:$port")
        sb.appendLine("⏱️ Время проверки: ${timestamp} (длительность: ${durationMs}ms)")
        sb.appendLine("")
        sb.appendLine("🌐 Активная сеть: $activeNetworkType")
        sb.appendLine("")
        sb.appendLine("📋 Сетевые интерфейсы:")
        networkInterfaces.forEach { iface ->
            sb.appendLine("  • ${iface.name} (${iface.displayName}): ${iface.ipAddress} ${if (iface.isUp) "↑" else "↓"}")
        }
        sb.appendLine("")
        sb.appendLine("📍 Результаты:")
        sb.appendLine("  ${if (pingSuccess) "✅" else "❌"} Ping: $pingSuccess")
        sb.appendLine("  ${if (portOpen) "✅" else "❌"} Порт $port: $portOpen")
        sb.appendLine("")
        sb.appendLine("🎯 ИТОГ: ${if (canConnect) "СЕРВЕР ДОСТУПЕН" else "СЕРВЕР НЕДОСТУПЕН"}")
        return sb.toString()
    }
}

/**
 * Информация о найденном TBox сервисе
 */
data class TBoxServiceInfo(
    val host: String,
    val port: Int,
    val subnet: String
) {
    fun toUiString(): String {
        return "🚗 TBox Service: $host:$port (subnet: $subnet.x)"
    }
}
