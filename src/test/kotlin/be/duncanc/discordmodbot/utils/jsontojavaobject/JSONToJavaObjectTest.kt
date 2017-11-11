package be.duncanc.discordmodbot.utils.jsontojavaobject

import org.json.JSONArray
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
        val testClazz2 = ClassContainingOtherClass(testClazz)
        val json = JSONToJavaObject.toJson(testClazz2)
        val something = JSONToJavaObject.toJavaObject(json, testClazz2::class.java)
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
            System.out.println(something)
        }
        assertThrows(IllegalArgumentException::class.java) {
            val json = JSONObject()
            json.put("bool", true)
            json.put("string", "hi")
            val something = JSONToJavaObject.toJavaObject(json, BadClass::class.java)
            System.out.println(something)
        }
    }

    @Test
    fun testListAndHasMap() {
        val testClazz = GoodWithMapAndHashMap()
        val testClazz2 = GoodClass(true, "yolo")
        (testClazz.list).add(testClazz2)
        (testClazz.list).add(testClazz2)
        (testClazz.list).add(testClazz2)
        (testClazz.map).put("test", testClazz2)
        testClazz.map.put("test2", testClazz2)
        (testClazz.map).put("test3", testClazz2)
        val json = JSONToJavaObject.toJson(testClazz)
        JSONToJavaObject.toJavaObject(json, GoodWithMapAndHashMap::class.java)
    }

    @Suppress("unused")
    class GoodClass(@JSONKey("bool") boolean: Boolean, @JSONKey("string") string: String) {
        val string = string
            @JSONKey("string") get
        val boolean = boolean
            @JSONKey("bool") get

        override fun toString(): String {
            return "GoodClass($boolean $string)"
        }
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
    class ClassContainingOtherClass(@JSONKey("otherClass") someClass: GoodClass) {
        val someClass = someClass
            @JSONKey("otherClass") get
    }

    @Suppress("unused")
    class BadClass(@JSONKey("bool") val boolean: Boolean, val string: String)

    @Suppress("unused")
    class GoodWithMapAndHashMap constructor(list: ArrayList<GoodClass> = ArrayList(), map: HashMap<String, GoodClass> = HashMap()) {

        @Suppress("UNUSED_PARAMETER")
        constructor(@JSONKey("list") jsonList: JSONArray, @JSONKey("map") jsonMap: JSONObject, @JSONKey("map") thisConsturctorDoesntWork: JSONObject) : this() {
            println(GoodWithMapAndHashMap::class.java.simpleName + ": Fake constructor was called.")
            throw Exception("Testing.")
        }

        constructor(@JSONKey("list") jsonList: JSONArray, @JSONKey("map") jsonMap: JSONObject) : this() {
            println(GoodWithMapAndHashMap::class.java.simpleName + ": Good constructor was called.")
            println(jsonList.toString())
            println(jsonMap.toString())
            //jsonList.forEach { list.add(JSONToJavaObject.toJavaObject(it as JSONObject, GoodClass::class.java)) }
            //jsonMap.keys().forEach { map.put(it, JSONToJavaObject.toJavaObject(jsonMap[it] as JSONObject, GoodClass::class.java)) }
            list.addAll(JSONToJavaObject.toTypedList(jsonList, GoodClass::class.java))
            map.putAll(JSONToJavaObject.toTypedMap(jsonMap, GoodClass::class.java))
            JSONToJavaObject.toTypedMap(jsonMap, GoodClass::class.java)
        }

        @Suppress("UNUSED_PARAMETER")
        constructor(@JSONKey("list") jsonList: JSONArray, @JSONKey("doesntexist") jsonMap: Any) : this()


        val list = list
            @JSONKey("list") get
        val map = map
            @JSONKey("map") get
    }
}