package be.duncanc.discordmodbot.utils.jsontojavaobject

/**
 * Provides the possibility to convert a JSON Object to a java Object
 *
 * @param jsonKey The JSON key that will be used to store or retrieve the value.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class JSONKey(val jsonKey: String)