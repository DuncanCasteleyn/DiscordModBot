package be.duncanc.discordmodbot.utils.jsontojavaobject

import org.json.JSONObject
import java.lang.reflect.Constructor

//todo test and documentation
object JSONToJavaObject {
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
        return jsonObject
    }

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
        for (i in 0 until longestConstructor.parameterCount) {
            params[i] = json[(constructorParams[i].getAnnotation(JSONKey::class.java).jsonKey)]
        }
        return longestConstructor.newInstance(params)
    }
}