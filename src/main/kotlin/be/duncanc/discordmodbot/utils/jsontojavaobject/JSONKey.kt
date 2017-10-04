package be.duncanc.discordmodbot.utils.jsontojavaobject

//todo documentation
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class JSONKey(val jsonKey: String)