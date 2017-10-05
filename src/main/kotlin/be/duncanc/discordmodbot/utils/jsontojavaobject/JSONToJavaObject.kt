package be.duncanc.discordmodbot.utils.jsontojavaobject

import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Constructor

/**
 * This object provides the possibility to convert object from JSON to Java Objects using the JSONKey annotation.
 *
 * @see JSONKey
 */
object JSONToJavaObject {

    /**
     * Converts a java Object to a JSONObject.
     *
     * @param javaObject a java Object that has at least one getter or field with a JSONKey annotation
     * @see JSONKey
     */
    fun toJson(javaObject: Any): JSONObject {
        val jsonKeyMethods = javaObject::class.java.methods.filter { it.isAnnotationPresent(JSONKey::class.java) }
        val jsonKeyFields = javaObject::class.java.fields.filter { it.isAnnotationPresent(JSONKey::class.java) }
        val jsonObject = JSONObject()
        jsonKeyMethods.forEach {
            jsonObject.put(it.getAnnotation(JSONKey::class.java).jsonKey, it.invoke(javaObject))
        }
        jsonKeyFields.forEach {
            jsonObject.put(it.getAnnotation(JSONKey::class.java).jsonKey, it.get(javaObject))
        }
        if(jsonObject.length() < 1) {
            throw IllegalArgumentException("Couldn't fetch any fields or getters from the class.")
        }
        return jsonObject
    }

    /**
     * Converts a JSONObject to a java Object using the JSONKey annotation
     *
     * @param json a JSONObject that needs to be converted to a java Object.
     * @param clazz a class that the json needs to be converted to that has at least 1 constructor with all parameters annotated with JSONKey
     * @see JSONKey
     */
    fun toJavaObject(json: JSONObject, clazz: Class<*>): Any {
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
            }
        } catch(jsonException: JSONException) {
            throw IllegalArgumentException("The provided json is missing a " +  JSONKey::class.java.simpleName + ": " + jsonException.message , jsonException)
        }
        return longestConstructor.newInstance(*params)
    }
}