import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory

fun main() {
    val typeFactory = RuntimeTypeAdapterFactory
        .of(Operation::class.java, "op")
        .registerSubtype(Add::class.java, "add")
        .registerSubtype(Increment::class.java, "inc")

    val gson = GsonBuilder().registerTypeAdapterFactory(typeFactory).create()

    val operation = gson.fromJson<Operation>(serializedOperation)
    val operations = gson.fromJson<Array<Operation>>(serializedArray)
    println(operation)
    println(operations.toList())
}

inline fun <reified T> Gson.fromJson(serialized: String) : T = fromJson(serialized, T::class.java)