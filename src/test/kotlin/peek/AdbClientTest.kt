package peek

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbClientTest {

    val adbClient: AdbClient = AdbClient()

    @Test
    fun makeCommand_ReturnWithLengthInHex() {
        val payload = "1234567890"
        assertEquals("000a1234567890", adbClient.prefixCommand(payload))
    }

    @Test
    fun givenResponse_ExtractPayload() {
        var response = "OKAY00040027"
        assertEquals("0027", response.payload())
        response = "FAIL001BSomething went really wrong"
        assertEquals("Something went really wrong", response.payload())
    }

    @Test
    fun givenResponse_ExtractPrefix() {
        var response = "OKAY00040027"
        assertEquals("OKAY", response.prefix())
        response = "FAIL001BSomething went really wrong"
        assertEquals("FAIL", response.prefix())
    }
}