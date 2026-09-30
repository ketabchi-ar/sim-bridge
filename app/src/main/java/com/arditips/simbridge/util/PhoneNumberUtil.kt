package com.arditips.simbridge.util

object PhoneNumberUtil {
    fun normalize(rawNumber: String?): String {
        if (rawNumber == null) return ""
        // Remove spaces, dashes, parentheses and special chars
        val clean = rawNumber.replace("[^0-9+]".toRegex(), "")
        
        return when {
            clean.startsWith("+98") -> "0" + clean.substring(3)
            clean.startsWith("0098") -> "0" + clean.substring(4)
            clean.startsWith("98") && clean.length > 10 -> "0" + clean.substring(2)
            clean.length == 10 && clean.startsWith("9") -> "0$clean"
            else -> clean
        }
    }

    fun isSame(num1: String?, num2: String?): Boolean {
        val n1 = normalize(num1)
        val n2 = normalize(num2)
        if (n1.isEmpty() || n2.isEmpty()) return false
        if (n1 == n2) return true
        
        // Match last 10 digits (Iranian mobile numbers without leading zero)
        val tail1 = if (n1.length >= 10) n1.takeLast(10) else n1
        val tail2 = if (n2.length >= 10) n2.takeLast(10) else n2
        return tail1 == tail2
    }
}
