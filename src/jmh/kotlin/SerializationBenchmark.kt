package visitor

import Add
import MediumPayload
import Increment
import Operation
import ShortPayload
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.GsonBuilder
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@SuppressWarnings("unused")
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 3, time = 240, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class SerializationBenchmark {
    companion object {
        const val SIZE = 100
        private val r = Random.Default
    }

    private val jacksonPolymorphic = jacksonObjectMapper()
    private val jacksonSimple = jacksonObjectMapper()


    private val gsonPolymorphic = GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory
                .of(Operation::class.java, "op")
                .registerSubtype(Add::class.java, "add")
                .registerSubtype(Increment::class.java, "inc")
        ).create()

    private val gsonSimple = GsonBuilder()
        .disableHtmlEscaping()
        .create()

    private val kotlinxJsonPolymorphic = Json(JsonConfiguration.Stable.copy(classDiscriminator = "op"))
    private val kotlinxJsonSimple = Json(JsonConfiguration.Stable)

    private val moshiPolymorphic = Moshi.Builder()
        .add(
            PolymorphicJsonAdapterFactory.of(Operation::class.java, "op")
                .withSubtype(Add::class.java, "add")
                .withSubtype(Increment::class.java, "inc")
        )
        .build()
        .adapter(Operation::class.java)

    private val moshiMedium = Moshi.Builder()
        .build().adapter(MediumPayload::class.java)
    private val moshiShort = Moshi.Builder()
        .build().adapter(ShortPayload::class.java)

    init {
        val typeValidator = BasicPolymorphicTypeValidator.builder().build()
        jacksonPolymorphic.activateDefaultTyping(typeValidator)
    }

    @State(Scope.Benchmark)
    open class BenchmarkState {
        val polymorphicString = Array(SIZE) {
            if (r.nextBoolean())
                """{"op":"inc","operand":${r.nextInt(100)}}"""
            else
                """{"op":"add","left":${r.nextInt(100)},"right":${r.nextInt(100)}}"""
        }
        val mediumString = Array(SIZE) {
            """{"number":${it},"origin":"${UUID.randomUUID()}","agent":${r.nextBoolean()},"firstName":"${UUID.randomUUID()}","lastName":"${UUID.randomUUID()}","birthPlace":"${UUID.randomUUID()}","age":${r.nextInt(200)},"passport":"${UUID.randomUUID()}"}"""
        }
        val shortString = Array(SIZE) {
            """{"number":${it},"name":"${UUID.randomUUID()}"}"""
        }

        val mediumObjects = Array(SIZE) {
            MediumPayload(number=it, agent= r.nextBoolean(),
                firstName = UUID.randomUUID().toString(), lastName = UUID.randomUUID().toString(), birthPlace = UUID.randomUUID().toString(),
                age = r.nextInt(200),origin = UUID.randomUUID().toString(), passport = UUID.randomUUID().toString()
            )
        }
        val shortObjects = Array(SIZE) {
            ShortPayload(number=it, name = UUID.randomUUID().toString())
        }

        val polymorphicObjects = Array(SIZE) {
            if (r.nextBoolean())
                Add(r.nextInt(100), r.nextInt(100))
            else
                Increment(r.nextInt(100))
        }

    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicRead")
    fun polymorphicReadKotlinx(sink: Blackhole, state: BenchmarkState) = state.polymorphicString.forEach {
        sink.consume(kotlinxJsonPolymorphic.parse(Operation.serializer(), it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicRead")
    fun polymorphicReadMoshi(sink: Blackhole, state: BenchmarkState) = state.polymorphicString.forEach {
        sink.consume(moshiPolymorphic.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicRead")
    fun polymorphicReadGson(sink: Blackhole, state: BenchmarkState) = state.polymorphicString.forEach {
        sink.consume(gsonPolymorphic.fromJson(it, Operation::class.java))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicRead")
    fun polymorphicReadJackson(sink: Blackhole, state: BenchmarkState) = state.polymorphicString.forEach {
        sink.consume(jacksonPolymorphic.readerFor(Operation::class.java).readValue<Operation>(it))
    }


    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumRead")
    fun mediumReadKotlinx(sink: Blackhole, state: BenchmarkState) = state.mediumString.forEach {
        sink.consume(kotlinxJsonSimple.parse(MediumPayload.serializer(),it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumRead")
    fun mediumReadMoshi(sink: Blackhole, state: BenchmarkState) = state.mediumString.forEach {
        sink.consume(moshiMedium.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumRead")
    fun mediumReadGson(sink: Blackhole, state: BenchmarkState) = state.mediumString.forEach {
        sink.consume(gsonSimple.fromJson<Operation>(it, MediumPayload::class.java))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumRead")
    fun mediumReadJackson(sink: Blackhole, state: BenchmarkState) = state.mediumString.forEach {
        sink.consume(jacksonSimple.readerFor(MediumPayload::class.java).readValue<MediumPayload>(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortRead")
    fun shortReadKotlinx(sink: Blackhole, state: BenchmarkState) = state.shortString.forEach {
        sink.consume(kotlinxJsonSimple.parse(ShortPayload.serializer(),it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortRead")
    fun shortReadMoshi(sink: Blackhole, state: BenchmarkState) = state.shortString.forEach {
        sink.consume(moshiShort.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortRead")
    fun shortReadGson(sink: Blackhole, state: BenchmarkState) = state.shortString.forEach {
        sink.consume(gsonSimple.fromJson<Operation>(it, ShortPayload::class.java))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortRead")
    @GroupThreads(1)
    fun shortReadJackson(sink: Blackhole, state: BenchmarkState) = state.shortString.forEach {
        sink.consume(jacksonSimple.readerFor(ShortPayload::class.java).readValue<ShortPayload>(it))
    }


    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicWrite")
    fun polymorphicWriteKotlinx(sink: Blackhole, state: BenchmarkState) = state.polymorphicObjects.forEach {
        sink.consume(kotlinxJsonPolymorphic.stringify(Operation.serializer(),it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicWrite")
    fun polymorphicWriteMoshi(sink: Blackhole, state: BenchmarkState) = state.polymorphicObjects.forEach {
        sink.consume(moshiPolymorphic.toJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicWrite")
    fun polymorphicWriteGson(sink: Blackhole, state: BenchmarkState) = state.polymorphicObjects.forEach {
        sink.consume(gsonPolymorphic.toJson(it, Operation::class.java))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicWrite")
    fun polymorphicWriteJackson(sink: Blackhole, state: BenchmarkState) = state.polymorphicObjects.forEach {
        sink.consume(jacksonPolymorphic.writerFor(Operation::class.java).writeValueAsString(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumWrite")
    fun mediumWriteKotlinx(sink: Blackhole, state: BenchmarkState) = state.mediumObjects.forEach {
        sink.consume(kotlinxJsonSimple.stringify(MediumPayload.serializer(), it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumWrite")
    fun mediumWriteMoshi(sink: Blackhole, state: BenchmarkState) = state.mediumObjects.forEach {
        sink.consume(moshiMedium.toJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumWrite")
    fun mediumWriteGson(sink: Blackhole, state: BenchmarkState) = state.mediumObjects.forEach {
        sink.consume(gsonSimple.toJson(it, MediumPayload::class.java))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumWrite")
    fun mediumWriteJackson(sink: Blackhole, state: BenchmarkState) = state.mediumObjects.forEach {
        sink.consume(jacksonSimple.writerFor(MediumPayload::class.java).writeValueAsString(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortWrite")
    fun shortWriteKotlinx(sink: Blackhole, state: BenchmarkState) = state.shortObjects.forEach {
        sink.consume(kotlinxJsonSimple.stringify(ShortPayload.serializer(), it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortWrite")
    fun shortWriteMoshi(sink: Blackhole, state: BenchmarkState) = state.shortObjects.forEach {
        sink.consume(moshiShort.toJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortWrite")
    fun shortWriteGson(sink: Blackhole, state: BenchmarkState) = state.shortObjects.forEach {
        sink.consume(gsonSimple.toJson(it, ShortPayload::class.java))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortWrite")
    @GroupThreads(1)
    fun shortWriteJackson(sink: Blackhole, state: BenchmarkState) = state.shortObjects.forEach {
        sink.consume(jacksonSimple.writerFor(ShortPayload::class.java).writeValueAsString(it))
    }


}

@ImplicitReflectionSerializer
fun main() {
    val opt = OptionsBuilder().include(SerializationBenchmark::class.java.simpleName)
        .forks(1)
        .build()
    Runner(opt).run()
}
