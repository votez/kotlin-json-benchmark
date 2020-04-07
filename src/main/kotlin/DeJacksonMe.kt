import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

fun main() {
    val mapper = jacksonObjectMapper()
    val typeValidator = BasicPolymorphicTypeValidator.builder().build()
    mapper.activateDefaultTyping(typeValidator)
    val operation = mapper.readValue<Operation>(serializedOperation)
    val array = mapper.readValue<Array<Operation>>(serializedArray)
    println(operation)
    println(array.toList())
}
