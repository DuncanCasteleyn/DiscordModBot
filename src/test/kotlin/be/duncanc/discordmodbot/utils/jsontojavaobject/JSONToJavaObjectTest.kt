package be.duncanc.discordmodbot.utils.jsontojavaobject

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

object JSONToJavaObjectTest {

    /**
     * Test class that can be converted to Java Object, but also converted back to JSON.
     */
    @Test
    fun testGoodClass() {
        val testClazz = GoodClass(true, "hi")
        val json = JSONToJavaObject.toJson(testClazz)
        val something = JSONToJavaObject.toJavaObject(json, GoodClass::class.java)
        something as GoodClass
        System.out.println(something)
    }


    /**
     * Test class that only is used to convert to Java Object never to JSON
     */
    @Test
    fun testGoodClass2() {
        val json = JSONObject()
        json.put("bool", true)
        json.put("string", "hi")
        val something = JSONToJavaObject.toJavaObject(json, GoodClass2::class.java)
        something as GoodClass2
        System.out.println(something)
        assertThrows(IllegalArgumentException::class.java) {
            JSONToJavaObject.toJson(something)
        }
    }

    /**
     * Test class that only is used to convert to JSON never to a Java Object
     */
    @Test
    fun testGoodClass3() {
        val testClazz = GoodClass3(true, "hi")
        val json = JSONToJavaObject.toJson(testClazz)
        assertThrows(IllegalArgumentException::class.java) {
            val something = JSONToJavaObject.toJavaObject(json, GoodClass3::class.java)
            something as GoodClass3
            System.out.println(something)
        }
    }

    /**
     * Test class that doesn't have it's constructor fully annotated
     */
    @Test
    fun testBadClass() {
        assertThrows(IllegalArgumentException::class.java) {
            val testClazz = BadClass(true, "hi")
            val json = JSONToJavaObject.toJson(testClazz)
            val something = JSONToJavaObject.toJavaObject(json, BadClass::class.java)
            something as BadClass
            System.out.println(something)
        }
        assertThrows(IllegalArgumentException::class.java) {
            val json = JSONObject()
            json.put("bool", true)
            json.put("string", "hi")
            val something = JSONToJavaObject.toJavaObject(json, BadClass::class.java)
            something as BadClass
            System.out.println(something)
        }
    }

    @Suppress("unused")
    class GoodClass(@JSONKey("bool") boolean: Boolean, @JSONKey("string") string: String) {
        val string = string
            @JSONKey("string") get
        val boolean = boolean
            @JSONKey("bool") get
    }

    @Suppress("unused")
    class GoodClass2(@JSONKey("bool") val boolean: Boolean, @JSONKey("string") val string: String)

    @Suppress("unused")
    class GoodClass3(boolean: Boolean, string: String) {
        val string = string
            @JSONKey("string") get
        val boolean = boolean
            @JSONKey("bool") get
    }

    @Suppress("unused")
    class BadClass(@JSONKey("bool") val boolean: Boolean, val string: String)
}