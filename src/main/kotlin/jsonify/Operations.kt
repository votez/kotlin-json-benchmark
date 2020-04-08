package jsonify

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include= JsonTypeInfo.As.PROPERTY, property="op")
@JsonSubTypes(
    JsonSubTypes.Type(value= Add::class, name="add"),
    JsonSubTypes.Type(value= Substract::class, name="sub"),
    JsonSubTypes.Type(value= Decrement::class, name="dec"),
    JsonSubTypes.Type(value= Increment::class, name="inc"))
sealed class Operation

@Serializable
@SerialName("add")
@JsonClass(generateAdapter = true)
data class Add(val left: Int, val right: Int): Operation()
@Serializable
@SerialName("inc")
@JsonClass(generateAdapter = true)
data class Increment(val operand: Int): Operation()
@Serializable
@SerialName("sub")
@JsonClass(generateAdapter = true)
data class Substract(val left: Int, val right: Int): Operation()
@Serializable
@SerialName("dec")
@JsonClass(generateAdapter = true)
data class Decrement(val operand: Int): Operation()

val serializedArray = """[
    {
     "op" : "add",
     "left" : 1,
     "right" : 2
    },
    {
     "op" : "inc",
     "operand" : 3
    }
    ]
""".trimIndent()

val serializedOperation = """
    {
     "op" : "add",
     "left" : 1,
     "right" : 2
    }
""".trimIndent()
val serializedSimple = """
    {
     "number" : 10
    }
""".trimIndent()

@Serializable
@JsonClass(generateAdapter = true)
data class MediumPayload(val number: Int, val agent: Boolean,
                         val firstName: String, val lastName: String, val birthPlace: String,
                         val age: Int, val origin: String, val passport: String
)

@Serializable
@JsonClass(generateAdapter = true)
data class ShortPayload(val number: Int, val name: String)