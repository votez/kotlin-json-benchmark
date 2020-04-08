package jsonify

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.parse
import kotlinx.serialization.parseList

@ImplicitReflectionSerializer
fun main() {
    val json = Json(JsonConfiguration.Stable.copy(classDiscriminator = "op"))
    println(json.parse<Operation>(serializedOperation))
    println(json.parseList<Operation>(serializedArray))
}