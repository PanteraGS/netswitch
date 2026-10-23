package com.netswitch.widget

enum class NetMode(val label: String, val mask: Long) {
    G3("3G", MASK_2G or MASK_3G),
    G4("4G", MASK_2G or MASK_3G or MASK_4G),
    G5("5G", MASK_2G or MASK_3G or MASK_4G or MASK_5G);

    companion object {
        fun fromMask(mask: Long): NetMode? = when {
            mask < 0 -> null
            mask and MASK_5G != 0L -> G5
            mask and MASK_4G != 0L -> G4
            mask and (MASK_3G or MASK_2G) != 0L -> G3
            else -> null
        }
    }
}

// Бит типа сети = 1 << (TelephonyManager.NETWORK_TYPE_* - 1)
private fun bit(networkType: Int) = 1L shl (networkType - 1)

private val MASK_2G = bit(16) or bit(1) or bit(2) or bit(4) or bit(7) // GSM, GPRS, EDGE, CDMA, 1xRTT
private val MASK_3G = bit(3) or bit(8) or bit(9) or bit(10) or bit(15) or bit(17) or // UMTS, HSDPA, HSUPA, HSPA, HSPA+, TD-SCDMA
    bit(5) or bit(6) or bit(12) or bit(14) // EVDO 0/A/B, eHRPD
private val MASK_4G = bit(13) or bit(19) // LTE, LTE-CA
private val MASK_5G = bit(20) // NR
