package com.mazda.control

import android.util.Log

/**
 * Реальный контроллер для работы со спойлером Mazda через Shizuku UserService.
 *
 * Использует ShellExecutorService (UserService) для выполнения shell команд.
 * Это заменяет deprecated Shizuku.newProcess() и не требует сложного AIDL.
 *
 * Работает ТОЛЬКО на Head Unit с запущенным Shizuku
 */
object RealMegaController : IMegaController {

    private const val TAG = "RealMegaController"

    // Spoiler property IDs (из HuronCarController)
    // Control property - use this to set spoiler position
    const val BODYINFO_HU_SPOILERSWITCH = 0x660000c3

    // Status properties (read only)
    const val BODYINFO_PTS_SPOILERPOSITION = 0x660000c4
    const val BODYINFO_PTS_SPOILERMOVEMENT = 0x660000c5

    // Spoiler values (из CustomInputEventController)
    const val SPOILER_OPEN = 0
    const val SPOILER_CLOSE = 2
    const val SPOILER_STOP = 1

    // Service info
    const val CAR_SERVICE = "com.mega.car.CarService"

    // Area ID (usually 0 for single-zone properties)
    const val AREA_ID = 0

    private var callCount = 0

    /**
     * Check if Shizuku is available
     */
    fun isShizukuAvailable(): Boolean {
        return ShizukuShellExecutor.isShizukuAvailable()
    }

    /**
     * Check if ShellExecutorService is bound
     */
    fun isServiceBound(): Boolean {
        return ShizukuShellExecutor.isServiceBound()
    }

    /**
     * Bind to ShellExecutorService.
     * Must be called before using setSpoiler.
     *
     * @param callback Called when binding completes
     */
    fun bind(callback: ((Boolean) -> Unit)? = null) {
        ShizukuShellExecutor.bind(MazdaControlApp.instance, callback)
    }

    /**
     * Unbind from ShellExecutorService.
     */
    fun unbind() {
        ShizukuShellExecutor.unbind()
    }

    /**
     * Set spoiler position via service call.
     *
     * Uses: service call com.mega.car.CarService <transaction> <propertyId> <areaId> <value>
     *
     * @param position 0 = OPEN, 2 = CLOSE
     * @return true if successful
     */
    override fun setSpoiler(position: Int): Boolean {
        callCount++
        Log.d(TAG, "setSpoiler #$callCount: position=$position")

        if (!isShizukuAvailable()) {
            Log.e(TAG, "Shizuku not available")
            return false
        }

        if (!isServiceBound()) {
            Log.e(TAG, "ShellExecutorService not bound. Call bind() first.")
            return false
        }

        // Build the service call command
        // ICarProperty.setProperty transaction = 0x5 (5)
        // Parameters: propertyId (int32), areaId (int32), value (int32)
        val command = buildServiceCallCommand(position)

        Log.d(TAG, "Executing: $command")

        val result = ShizukuShellExecutor.execute(command)

        if (result.success) {
            Log.i(TAG, "setSpoiler successful: position=$position, output=${result.output}")
            return true
        } else {
            Log.e(TAG, "setSpoiler failed: exitCode=${result.exitCode}, error=${result.errorOutput}")
            return false
        }
    }

    /**
     * Build service call command for setting a property.
     *
     * The command format for CarPropertyService is:
     * service call <service> <transaction_code> <arg1_type:arg1> <arg2_type:arg2> ...
     */
    private fun buildServiceCallCommand(value: Int): String {
        // Transaction code 0x5 = setProperty for ICarProperty
        // Format: service call <service> <code> <propertyId> <areaId> <value>
        return "service call $CAR_SERVICE 5 i32 ${BODYINFO_HU_SPOILERSWITCH} i32 $AREA_ID i32 $value"
    }

    /**
     * Get spoiler position.
     *
     * @return Position value, or -1 on error
     */
    fun getSpoilerPosition(): Int {
        if (!isServiceBound()) {
            Log.e(TAG, "Not bound to ShellExecutorService")
            return -1
        }

        // ICarProperty.getProperty transaction = 0x4 (4)
        val command = "service call $CAR_SERVICE 4 i32 ${BODYINFO_PTS_SPOILERPOSITION} i32 $AREA_ID"

        Log.d(TAG, "Executing: $command")

        val result = ShizukuShellExecutor.execute(command)

        if (result.success) {
            // Parse the output
            // Format: Result: Parcel(......)
            val position = parseParcelIntValue(result.output)
            Log.d(TAG, "Spoiler position: $position")
            return position
        } else {
            Log.e(TAG, "Failed to get spoiler position: ${result.errorOutput}")
            return -1
        }
    }

    /**
     * Get spoiler movement state.
     *
     * @return Movement state (0 = stopped, etc.), or -1 on error
     */
    fun getSpoilerMovement(): Int {
        if (!isServiceBound()) {
            Log.e(TAG, "Not bound to ShellExecutorService")
            return -1
        }

        val command = "service call $CAR_SERVICE 4 i32 ${BODYINFO_PTS_SPOILERMOVEMENT} i32 $AREA_ID"

        Log.d(TAG, "Executing: $command")

        val result = ShizukuShellExecutor.execute(command)

        if (result.success) {
            return parseParcelIntValue(result.output)
        } else {
            Log.e(TAG, "Failed to get spoiler movement: ${result.errorOutput}")
            return -1
        }
    }

    /**
     * Parse int value from service call Parcel output.
     *
     * Service call output format:
     * Result: Parcel(00000000 000000c4 00000000 00000000 00000000 00000065)
     * The value is usually at position 4 (0x65 = 101 means "fully open")
     */
    private fun parseParcelIntValue(output: String): Int {
        try {
            // Find the parcel content in parentheses
            val startIdx = output.indexOf('(')
            val endIdx = output.indexOf(')')
            if (startIdx == -1 || endIdx == -1) {
                Log.w(TAG, "No parcel found in: $output")
                return -1
            }

            val parcelContent = output.substring(startIdx + 1, endIdx).trim()
            Log.d(TAG, "Parcel content: $parcelContent")

            // Parse hex values
            val parts = parcelContent.split(' ')
                .filter { it.isNotEmpty() }
                .map { it.toInt(16) }

            if (parts.size >= 5) {
                // The value is typically at index 4 (after propertyId, areaId, status, timestamp)
                val value = parts[4]
                Log.d(TAG, "Parsed int value: $value (0x${value.toString(16)})")
                return value
            }

            Log.w(TAG, "Could not parse int from: $parcelContent")
            return -1
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing int value", e)
            return -1
        }
    }

    override fun callService(code: Int, propId: Int, value: Int): Boolean {
        Log.w(TAG, "callService is deprecated. Use setSpoiler() directly.")
        return setSpoiler(value)
    }

    override fun getProperty(propId: Int): Int {
        Log.d(TAG, "getProperty: propId=0x${propId.toString(16)}")

        if (!isServiceBound()) {
            Log.e(TAG, "ShellExecutorService not bound")
            return 0
        }

        // ICarProperty.getProperty transaction = 0x4 (4)
        val command = "service call $CAR_SERVICE 4 i32 $propId i32 $AREA_ID"

        val result = ShizukuShellExecutor.execute(command)

        return if (result.success) {
            parseParcelIntValue(result.output)
        } else {
            Log.e(TAG, "Failed to get property: ${result.errorOutput}")
            0
        }
    }

    override fun setProperty(propId: Int, value: Int): Boolean {
        Log.d(TAG, "setProperty: propId=0x${propId.toString(16)}, value=$value")

        if (!isServiceBound()) {
            Log.e(TAG, "ShellExecutorService not bound")
            return false
        }

        // ICarProperty.setProperty transaction = 0x5 (5)
        val command = "service call $CAR_SERVICE 5 i32 $propId i32 $AREA_ID i32 $value"

        val result = ShizukuShellExecutor.execute(command)

        if (!result.success) {
            Log.e(TAG, "Failed to set property: ${result.errorOutput}")
        }

        return result.success
    }

    override fun getStats(): Map<String, Any> {
        return mapOf(
            "callCount" to callCount,
            "shizukuRunning" to isShizukuAvailable(),
            "serviceBound" to isServiceBound(),
            "serviceName" to CAR_SERVICE,
            "spoilerPropertyId" to "0x${BODYINFO_HU_SPOILERSWITCH.toString(16)}",
            "spoilerPositionPropertyId" to "0x${BODYINFO_PTS_SPOILERPOSITION.toString(16)}",
            "spoilerMovementPropertyId" to "0x${BODYINFO_PTS_SPOILERMOVEMENT.toString(16)}",
            "spoilerOpenValue" to SPOILER_OPEN,
            "spoilerCloseValue" to SPOILER_CLOSE,
            "mode" to "REAL_SHELL_SERVICE"
        )
    }

    fun reset() {
        callCount = 0
        Log.d(TAG, "Reset")
    }
}
