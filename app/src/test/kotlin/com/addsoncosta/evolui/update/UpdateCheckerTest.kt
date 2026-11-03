package com.addsoncosta.evolui.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun newerVersionCodeIsAvailable() {
        val xml = "<result><appId>com.evolui.myapp</appId><resultCode>2</resultCode><versionCode>3</versionCode><versionName>1.2.0</versionName></result>"
        val r = UpdateChecker.parse(xml, installedCode = 1)
        assertTrue(r is UpdateResult.Available)
        assertEquals("1.2.0", (r as UpdateResult.Available).versionName)
    }

    @Test
    fun sameVersionIsUpToDate() {
        val xml = "<result><resultCode>1</resultCode><versionCode>1</versionCode></result>"
        assertEquals(UpdateResult.UpToDate, UpdateChecker.parse(xml, installedCode = 1))
    }

    @Test
    fun garbageIsUnknown() {
        assertEquals(UpdateResult.Unknown, UpdateChecker.parse("<html>erro</html>", installedCode = 1))
    }
}
