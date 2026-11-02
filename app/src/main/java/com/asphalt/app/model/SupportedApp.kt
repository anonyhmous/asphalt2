package com.asphalt.app.model

/**
 * The only three supported ride-hailing apps as requested.
 * No other application is inspected.
 */
enum class SupportedApp(
    val titleArabic: String,
    val packageName: String,
    val defaultCommissionPct: Double,
    val packageAliases: List<String> = emptyList()
) {
    UBER("أوبر", "com.ubercab.driver", 25.0, listOf("com.ubercab")),
    DIDI("ديدي", "com.didiglobal.driver", 22.0, listOf("com.didiglobal.passenger", "com.didiglobal.driver.latam")),
    INDRIVER("إن درايف", "sinet.startup.inDriver", 12.0, listOf("com.indrive", "com.indriver"));

    companion object {
        fun fromPackage(pkg: String?): SupportedApp? {
            if (pkg == null) return null
            return entries.firstOrNull { 
                it.packageName.equals(pkg, ignoreCase = true) ||
                it.packageAliases.any { alias -> alias.equals(pkg, ignoreCase = true) }
            }
        }
    }
}
