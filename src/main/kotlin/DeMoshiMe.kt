import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory

fun main() {
    val moshiPolymorphic = Moshi.Builder()
        .add(
            PolymorphicJsonAdapterFactory.of(Operation::class.java, "op")
                .withSubtype(Add::class.java, "add")
                .withSubtype(Increment::class.java, "inc")
        )
        .build()
    val adapter = moshiPolymorphic.adapter<Operation>()
    val operation = adapter.fromJson(serializedOperation)
    println(operation)
}

inline fun <reified T> Moshi.adapter(): JsonAdapter<T> = adapter(T::class.java)