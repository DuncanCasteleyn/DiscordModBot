/*
 * MIT License
 *
 * Copyright (c) 2017 Duncan Casteleyn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package be.duncanc.discordmodbot.utils.jsontojavaobject

import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONToJavaObject.CONVERT_TO_JAVA_NOT_SUPPORTED
import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONToJavaObject.NO_JSON_CONVERT_REQUIRED
import net.dv8tion.jda.core.utils.SimpleLog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Constructor

/**
 * This object provides the possibility to convert object from JSON to Java Objects using JSONKey annotations.
 *
 * @property NO_JSON_CONVERT_REQUIRED Types that don't need to be converted to a JSONObject.
 * @property CONVERT_TO_JAVA_NOT_SUPPORTED Types that cannot be converted automatically to a Java Object because we can't retrieve the expected type to go inside it and most the times list are created in the class itself and not by a constructor.
 * @see JSONKey
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
object JSONToJavaObject {

    private val LOG = SimpleLog.getLog(JSONToJavaObject.javaClass)
    private val NO_JSON_CONVERT_REQUIRED = arrayOf(java.lang.Boolean::class.java, java.lang.Number::class.java, java.lang.Character::class.java, java.lang.String::class.java)
    private val CONVERT_TO_JAVA_NOT_SUPPORTED = arrayOf(java.util.Map::class.java, java.util.List::class.java)

    /**
     * Converts a java Object to a JSONObject using JSONKey annotations.
     *
     * @param javaObject a Java Object that has at least one public getter or field with a JSONKey annotation
     * @param jsonObject JSONObject to put the data of the Java Object in.
     * @see JSONKey
     */
    @JvmOverloads
    fun toJson(javaObject: Any, jsonObject: JSONObject = JSONObject()): JSONObject {
        val jsonKeyMethods = javaObject::class.java.methods.filter { it.isAnnotationPresent(JSONKey::class.java) }
        val jsonKeyFields = javaObject::class.java.fields.filter { it.isAnnotationPresent(JSONKey::class.java) }
        jsonKeyMethods.forEach {
            putInJSONObject(it.getAnnotation(JSONKey::class.java).jsonKey, it.invoke(javaObject), jsonObject)
        }
        jsonKeyFields.forEach {
            putInJSONObject(it.getAnnotation(JSONKey::class.java).jsonKey, it.get(javaObject), jsonObject)
        }
        if (jsonObject.length() < 1) {
            throw IllegalArgumentException("Couldn't fetch any fields or getters from the class.")
        }
        return jsonObject
    }

    private fun putInJSONObject(key: String, value: Any, jsonObject: JSONObject) {
        when {
            value::class.java in NO_JSON_CONVERT_REQUIRED -> {
                jsonObject.put(key, value)
            }
            value is java.util.List<*> -> {
                val jsonArray = JSONArray()
                value.forEach {
                    jsonArray.put(toJson(it))
                }
                jsonObject.put(key, jsonArray)
            }
            value is java.util.Map<*, *> -> {
                val hashMapJSON = JSONObject()
                value.forEach { mapKey, mapValue ->
                    hashMapJSON.put(mapKey.toString(), toJson(mapValue))
                }
                jsonObject.put(key, hashMapJSON)
            }
            else -> {
                jsonObject.put(key, toJson(value))
            }
        }
    }

    /**
     * Converts a JSONObject to a Java Object using the JSONKey annotations.
     *
     * @param json a JSONObject that needs to be converted to a Java Object.
     * @param clazz a class that the JSONObject needs to be converted to that has at least 1 constructor with all parameters annotated with JSONKey
     * @see JSONKey
     */
    fun <T> toJavaObject(json: JSONObject, clazz: Class<T>): T {
        val fullyAnnotatedConstructors = ArrayList<Constructor<*>>(clazz.constructors.filter { it.parameters.all { it.getAnnotation(JSONKey::class.java) != null } })

        if (fullyAnnotatedConstructors.isEmpty()) {
            throw IllegalArgumentException("The provided class doesn't have any constructors that are fully annotated with " + JSONKey::class.java.simpleName)
        }

        var lastThrow: Throwable? = null

        while (fullyAnnotatedConstructors.isNotEmpty()) {
            var amountOfParameters = 0
            var longestConstructor: Constructor<*>? = null

            for (annotatedConstructor in fullyAnnotatedConstructors) {
                if (annotatedConstructor.parameterCount > amountOfParameters) {
                    amountOfParameters = annotatedConstructor.parameterCount
                    longestConstructor = annotatedConstructor
                }
            }

            fullyAnnotatedConstructors.remove(longestConstructor)

            if (longestConstructor == null) {
                throw IllegalStateException("Could not retrieve the longest constructor of the class")
            }

            try {
                return createInstance(longestConstructor, json)
            } catch (throwable: Throwable) {
                lastThrow = throwable
                LOG.debug(throwable)
            }
        }

        throw IllegalStateException("All the constructors that are fully annotated throw an exception.", lastThrow)
    }

    private fun <T> createInstance(longestConstructor: Constructor<*>, json: JSONObject): T {
        val params = arrayOfNulls<Any?>(longestConstructor.parameterCount)
        val constructorParams = longestConstructor.parameters
        try {
            //todo fall back to smaller constructors if biggest one fails
            for (i in 0 until longestConstructor.parameterCount) {
                params[i] = json[(constructorParams[i].getAnnotation(JSONKey::class.java).jsonKey)]
                if (params[i]!!::class.java !in NO_JSON_CONVERT_REQUIRED) {
                    val expectedType = constructorParams[i].type as Class<*>
                    if (expectedType in CONVERT_TO_JAVA_NOT_SUPPORTED) {
                        throw UnsupportedOperationException("Lists and Maps need to be manually converted back due to Type Erasure.")
                    } else if (expectedType !in arrayOf(JSONArray::class.java, JSONObject::class.java)) {
                        params[i] = toJavaObject(params[i] as JSONObject, expectedType)
                    }
                }
            }
        } catch (jsonException: JSONException) {
            throw IllegalArgumentException("The provided json is missing a " + JSONKey::class.java.simpleName + ": " + jsonException.message, jsonException)
        }

        return longestConstructor.newInstance(*params) as T
    }

    /**
     * Converts a jsonArray to a typed list using JSONKey annotations.
     *
     * @param jsonArray JSONArray that needs to be converted to a typed list.
     * @param clazz a class that the JSONArray needs to be converted to that has at least 1 constructor with all parameters annotated with JSONKey.
     * @param E List type to create.
     * @return a typed List with the given E param as type.
     * @see JSONKey
     */
    fun <E> toTypedList(jsonArray: JSONArray, clazz: Class<E>): List<E> {
        val list = ArrayList<E>()
        jsonArray.forEach { list.add((toJavaObject(it as JSONObject, clazz))) }
        return list
    }

    /**
     * Converts a jsonArray to a typed list using JSONKey annotations.
     *
     * @param jsonObject JSONObject that needs to be converted to a typed list
     * @param valueClazz a class that the value of each key needs to be converted to that has at least 1 constructor with all parameters annotated with JSONKey
     * @param keyClazz a class that can be converted using keyConverter.
     * @param K Key type that will be used for the Map.
     * @param V Value type that will be used for the Map.
     * @return a typed Map with the given K param as key type and the given V param as value type.
     * @see JSONKey
     * @see keyConverter
     */
    fun <K, V> toTypedMap(jsonObject: JSONObject, keyClazz: Class<K>, valueClazz: Class<V>): Map<K, V> {
        val map = HashMap<K, V>()
        jsonObject.keys().forEach { map.put(keyConverter(it, keyClazz), toJavaObject(jsonObject[it] as JSONObject, valueClazz)) }
        return map
    }

    private fun <K> keyConverter(input: String, clazz: Class<K>): K {
        return when {
            clazz.isAssignableFrom(String::class.java) -> input as K
            clazz.isAssignableFrom(Long::class.java) -> java.lang.Long.valueOf(input) as K
            clazz.isAssignableFrom(Int::class.java) -> java.lang.Integer.valueOf(input) as K
            clazz.isAssignableFrom(Boolean::class.java) -> java.lang.Boolean.valueOf(input) as K
            clazz.isAssignableFrom(Double::class.java) -> java.lang.Double.valueOf(input) as K
            else -> throw IllegalArgumentException("Bad type.")
        }
    }
}