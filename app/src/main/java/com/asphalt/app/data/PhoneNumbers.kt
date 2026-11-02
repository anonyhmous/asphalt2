package com.asphalt.app.data

/** Single place for Egyptian phone normalisation (was duplicated in the sync repository). */
object PhoneNumbers {
    /** "+20 101 234 5678" / "01012345678" / Arabic digits  ->  "201012345678" */
    fun normalize(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
            }
        }
        var d = sb.toString()
        if (d.startsWith("0020")) d = d.substring(2)
        else if (d.startsWith("01") && d.length == 11) d = "2$d"
        else if (!d.startsWith("20") && d.startsWith("1") && d.length == 10) d = "20$d"
        return d
    }

    /** E.164 form required by Firebase Phone Auth: "+201012345678"; null if it does not look valid. */
    fun toE164(input: String): String? {
        val n = normalize(input)
        return if (n.startsWith("20") && n.length == 12) "+$n" else null
    }
}
