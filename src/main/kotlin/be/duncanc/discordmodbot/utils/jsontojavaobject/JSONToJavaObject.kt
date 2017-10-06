package be.duncanc.discordmodbot.utils.jsontojavaobject

import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONToJavaObject.CONVERT_TO_JAVA_NOT_SUPPORTED
import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONToJavaObject.NO_JSON_CONVERT_REQUIRED
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.ParameterizedType

/**
 * This object provides the possibility to convert object from JSON to Java Objects using the JSONKey annotation.
 *
 * @property NO_JSON_CONVERT_REQUIRED Types that don't need to be converted to a JSONObject.
 * @property CONVERT_TO_JAVA_NOT_SUPPORTED Types that cannot be converted automatically to a Java Object because we can't retrieve the expected type to go inside it and most the times list are created in the class itself and not by a constructor.
 * @see JSONKey
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
object JSONToJavaObject {

    private val NO_JSON_CONVERT_REQUIRED = arrayOf(java.lang.Boolean::class.java, java.lang.Number::class.java, java.lang.Character::class.java, java.lang.String::class.java)
    private val CONVERT_TO_JAVA_NOT_SUPPORTED = arrayOf(java.util.Map::class.java, java.util.List::class.java)

    /**
     * Converts a java Object to a JSONObject.
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
            value is java.util.Map<*,*> -> {
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
     * Converts a JSONObject to a Java Object using the JSONKey annotation
     *
     * @param json a JSONObject that needs to be converted to a Java Object.
     * @param clazz a class that the JSONObject needs to be converted to that has at least 1 constructor with all parameters annotated with JSONKey
     * @see JSONKey
     */
    fun <T> toJavaObject(json: JSONObject, clazz: Class<T>): T {
        val fullyAnnotatedConstructors = clazz.constructors.filter { it.parameters.all { it.getAnnotation(JSONKey::class.java) != null } }

        if (fullyAnnotatedConstructors.isEmpty()) {
            throw IllegalArgumentException("The provided class doesn't have any constructors that are fully annotated with " + JSONKey::class.java.simpleName)
        }

        var amountOfParameters = 0
        var longestConstructor: Constructor<*>? = null

        for (annotatedConstructor in fullyAnnotatedConstructors) {
            if (annotatedConstructor.parameterCount > amountOfParameters) {
                amountOfParameters = annotatedConstructor.parameterCount
                longestConstructor = annotatedConstructor
            }
        }

        if (longestConstructor == null) {
            throw IllegalStateException("Could not retrieve the longest constructor of the class")
        }

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
                    } else if(expectedType !in arrayOf(JSONArray::class.java, JSONObject::class.java )) {
                        params[i] = toJavaObject(params[i] as JSONObject, expectedType)
                    }
                }
            }
        } catch (jsonException: JSONException) {
            throw IllegalArgumentException("The provided json is missing a " + JSONKey::class.java.simpleName + ": " + jsonException.message, jsonException)
        }

        @Suppress("UNCHECKED_CAST")
        return longestConstructor.newInstance(*params) as T
    }
}