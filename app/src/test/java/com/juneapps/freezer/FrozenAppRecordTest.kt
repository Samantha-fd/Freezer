package com.juneapps.freezer

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class FrozenAppRecordTest {
    @Test fun legacyRecordDefaultsToDisabled() {
        val record = Json.decodeFromString<FrozenAppRecord>("""{"packageName":"example.app","appName":"Example","frozenAt":1}""")
        assertEquals("disabled", record.method)
    }

    @Test fun suspendedMethodSurvivesSerialization() {
        val record = FrozenAppRecord("example.app", "Example", 1, "suspended")
        assertEquals(record, Json.decodeFromString<FrozenAppRecord>(Json.encodeToString(record)))
    }
}
