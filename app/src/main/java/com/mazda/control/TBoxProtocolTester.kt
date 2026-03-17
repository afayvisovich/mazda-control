package com.mazda.control

import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Тестер протокола TBox
 * 
 * Подключается к найденным сервисам и пытается определить:
 * - Формат пакетов (30-байтовый заголовок с 0x23 0x23)
 * - Ответы сервера
 * - Работоспособность протокола
 */
object TBoxProtocolTester {

    private const val TAG = "TBoxProtocolTester"
    private const val CONNECTION_TIMEOUT = 2000
    private const val READ_TIMEOUT = 1000

    data class TestResult(
        val host: String,
        val port: Int,
        val connected: Boolean,
        val responded: Boolean,
        val responseBytes: ByteArray?,
        val errorMessage: String?
    )

    /**
     * Тестовое подключение к сервису
     */
    fun testService(host: String, port: Int): TestResult {
        var socket: Socket? = null
        return try {
            Log.d(TAG, "🔌 Connecting to $host:$port...")
            
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), CONNECTION_TIMEOUT)
            socket.soTimeout = READ_TIMEOUT
            
            Log.d(TAG, "✅ Connected to $host:$port")
            
            // Попытаться прочитать ответ (возможно сервер что-то шлёт)
            val input = socket.getInputStream()
            val buffer = ByteArray(1024)
            
            val bytesRead = try {
                input.read(buffer, 0, 1024)
            } catch (e: SocketTimeoutException) {
                Log.d(TAG, "⏱️ Read timeout - server is silent")
                -1
            }
            
            val responseBytes = if (bytesRead > 0) {
                buffer.copyOf(bytesRead)
            } else {
                null
            }
            
            if (bytesRead > 0) {
                Log.d(TAG, "📥 Received $bytesRead bytes: ${responseBytes!!.toHex()}")
                
                // Проверка на формат 0x23 0x23
                val hasMagicBytes = bytesRead >= 2 && 
                    responseBytes!![0] == 0x23.toByte() && 
                    responseBytes!![1] == 0x23.toByte()
                
                if (hasMagicBytes) {
                    Log.d(TAG, "✅ MAGIC BYTES DETECTED (0x23 0x23)!")
                }
                
                // Проверка на формат 0x5A
                val hasOldFormat = bytesRead >= 1 && responseBytes!![0] == 0x5A.toByte()
                if (hasOldFormat) {
                    Log.d(TAG, "⚠️ OLD FORMAT DETECTED (0x5A)")
                }
            } else {
                Log.d(TAG, "📭 No response from server")
            }
            
            TestResult(
                host = host,
                port = port,
                connected = true,
                responded = bytesRead > 0,
                responseBytes = responseBytes,
                errorMessage = null
            )
            
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "❌ Connection timeout to $host:$port")
            TestResult(
                host = host,
                port = port,
                connected = false,
                responded = false,
                responseBytes = null,
                errorMessage = "Connection timeout"
            )
        } catch (e: IOException) {
            Log.e(TAG, "❌ Connection failed to $host:$port - ${e.message}")
            TestResult(
                host = host,
                port = port,
                connected = false,
                responded = false,
                responseBytes = null,
                errorMessage = e.message
            )
        } finally {
            try {
                socket?.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }

    /**
     * Отправить тестовый пакет (спойлер open)
     */
    fun sendTestCommand(
        host: String, 
        port: Int, 
        command: ByteArray
    ): TestCommandResult {
        var socket: Socket? = null
        return try {
            Log.d(TAG, "📤 Sending test command to $host:$port...")
            
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), CONNECTION_TIMEOUT)
            socket.soTimeout = READ_TIMEOUT
            
            // Отправить команду
            val output = socket.getOutputStream()
            output.write(command)
            output.flush()
            Log.d(TAG, "✅ Sent ${command.size} bytes: ${command.toHex()}")
            
            // Подождать ответ
            val input = socket.getInputStream()
            val buffer = ByteArray(1024)
            val bytesRead = try {
                input.read(buffer, 0, 1024)
            } catch (e: SocketTimeoutException) {
                Log.d(TAG, "⏱️ Read timeout waiting for response")
                -1
            }
            
            if (bytesRead > 0) {
                val response = buffer.copyOf(bytesRead)
                Log.d(TAG, "📥 Received response: ${response.size} bytes")
                Log.d(TAG, "Response: ${response.toHex()}")
                
                // Проверка формата ответа
                val hasMagicBytes = response.size >= 2 && 
                    response[0] == 0x23.toByte() && 
                    response[1] == 0x23.toByte()
                
                TestCommandResult(
                    success = true,
                    commandSent = command.size,
                    responseReceived = bytesRead,
                    responseHex = response.toHex(),
                    hasExpectedFormat = hasMagicBytes,
                    errorMessage = null
                )
            } else {
                TestCommandResult(
                    success = false,
                    commandSent = command.size,
                    responseReceived = 0,
                    responseHex = null,
                    hasExpectedFormat = false,
                    errorMessage = "No response from server"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send command: ${e.message}")
            TestCommandResult(
                success = false,
                commandSent = command.size,
                responseReceived = 0,
                responseHex = null,
                hasExpectedFormat = false,
                errorMessage = e.message
            )
        } finally {
            try {
                socket?.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }

    /**
     * Протестировать все найденные сервисы
     */
    fun testAllServices(services: List<TBoxServiceInfo>): List<TestResult> {
        val results = mutableListOf<TestResult>()
        
        Log.d(TAG, "🔍 Testing ${services.size} services...")
        
        services.forEach { service ->
            Log.d(TAG, "\n${"=".repeat(40)}")
            Log.d(TAG, "Testing: ${service.toUiString()}")
            Log.d(TAG, "${"=".repeat(40)}")
            
            val result = testService(service.host, service.port)
            results.add(result)
            
            Log.d(TAG, "Result: ${if (result.connected) "✅ CONNECTED" else "❌ FAILED"}")
            if (result.responded) {
                Log.d(TAG, "Response: ${result.responseBytes?.toHex()}")
            }
        }
        
        return results
    }

    // Extension functions
    private fun ByteArray.toHex(): String {
        return joinToString(" ") { String.format("%02X", it) }
    }

    data class TestCommandResult(
        val success: Boolean,
        val commandSent: Int,
        val responseReceived: Int,
        val responseHex: String?,
        val hasExpectedFormat: Boolean,
        val errorMessage: String?
    ) {
        fun toUiString(): String {
            val sb = StringBuilder()
            sb.appendLine("📤 Command Test Result:")
            sb.appendLine("  Success: ${if (success) "✅" else "❌"}")
            sb.appendLine("  Sent: $commandSent bytes")
            sb.appendLine("  Received: $responseReceived bytes")
            if (responseHex != null) {
                sb.appendLine("  Response: $responseHex")
            }
            sb.appendLine("  Expected format: ${if (hasExpectedFormat) "✅" else "❌"}")
            if (errorMessage != null) {
                sb.appendLine("  Error: $errorMessage")
            }
            return sb.toString()
        }
    }
}
