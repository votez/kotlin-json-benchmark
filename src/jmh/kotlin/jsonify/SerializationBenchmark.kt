package jsonify

import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.module.afterburner.AfterburnerModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
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
@Warmup(iterations = 6)
@Measurement(iterations = 3, time = 240, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class SerializationBenchmark {
    companion object {
        const val SIZE = 500
        private val r = Random.Default
    }

    private val kotlinxJsonPolymorphic = Json(JsonConfiguration.Stable.copy(classDiscriminator = "op"))
    private val kotlinxJsonSimple = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true))

    private val moshiOperation = Moshi.Builder()
        .add(
            PolymorphicJsonAdapterFactory.of(Operation::class.java, "op")
                .withSubtype(Add::class.java, "add")
                .withSubtype(Substract::class.java, "sub")
                .withSubtype(Increment::class.java, "inc")
                .withSubtype(Decrement::class.java, "dec")
        )
        .build()

    private val moshiOperationAdapter = moshiOperation.adapter(Operation::class.java)

    private val moshiMedium = Moshi.Builder()
        .build().adapter(MediumPayload::class.java)
    private val moshiShort = Moshi.Builder()
        .build().adapter(Substract::class.java)

    private val jacksonOperationReader: ObjectReader
    private val jacksonMediumPayloadReader: ObjectReader
    private val jacksonShortPayloadReader: ObjectReader

    private val jacksonOperationWriter: ObjectWriter
    private val jacksonMediumPayloadWriter: ObjectWriter
    private val jacksonShortPayloadWriter: ObjectWriter

    private val gsonOperation: TypeAdapter<Operation>
    private val gsonShort: TypeAdapter<Substract>
    private val gsonMedium: TypeAdapter<MediumPayload>

    private val gsonPolymorphic = GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory
                .of(Operation::class.java, "op")
                .registerSubtype(Add::class.java, "add")
                .registerSubtype(Substract::class.java, "sub")
                .registerSubtype(Decrement::class.java, "dec")
                .registerSubtype(Increment::class.java, "inc")
        ).create()

    init {
        val jacksonPolymorphic = jacksonObjectMapper()
        val jacksonSimple = jacksonObjectMapper()
        val typeValidator = BasicPolymorphicTypeValidator.builder().build()
        jacksonPolymorphic.activateDefaultTyping(typeValidator)
        jacksonPolymorphic.registerModule(AfterburnerModule())
        jacksonSimple.registerModule(AfterburnerModule())
        jacksonOperationReader = jacksonPolymorphic.readerFor(Operation::class.java)
        jacksonMediumPayloadReader = jacksonSimple.readerFor(MediumPayload::class.java)
        jacksonShortPayloadReader = jacksonSimple.readerFor(Substract::class.java)
        jacksonOperationWriter = jacksonPolymorphic.writerFor(Operation::class.java)
        jacksonMediumPayloadWriter = jacksonSimple.writerFor(MediumPayload::class.java)
        jacksonShortPayloadWriter = jacksonSimple.writerFor(Substract::class.java)


        val gsonSimple = GsonBuilder()
            .disableHtmlEscaping()
            .create()

        gsonOperation = gsonPolymorphic.getAdapter(Operation::class.java)
        gsonShort = gsonSimple.getAdapter(Substract::class.java)
        gsonMedium = gsonSimple.getAdapter(MediumPayload::class.java)

    }

    @State(Scope.Benchmark)
    open class BenchmarkState {
        val polymorphicString = Array(SIZE) {
            when (r.nextInt(0, 4)) {
                0 -> """{"op":"inc","operand":${r.nextInt(100)}}"""
                1 -> """{"op":"dec","operand":${r.nextInt(100)}}"""
                2 -> """{"op":"add","left":${r.nextInt(100)},"right":${r.nextInt(100)}}"""
                3 -> """{"op":"sub","left":${r.nextInt(100)},"right":${r.nextInt(100)}}"""
                else -> """{"op":"inc","operand":${r.nextInt(100)}}"""
            }
        }
        val mediumString = Array(SIZE) {
            """{"number":${it},"origin":"${UUID.randomUUID()}","agent":${r.nextBoolean()},"first":"${UUID.randomUUID()}","last":"${UUID.randomUUID()}","birth":"${UUID.randomUUID()}","age":${r.nextInt(
                200
            )},"passport":"${UUID.randomUUID()}"}"""
        }
        val shortString = Array(SIZE) {
            """{"op":"sub","left":${r.nextInt(100)},"right":${r.nextInt(100)}}"""
        }

        val mediumObjects = Array(SIZE) {
            MediumPayload(
                number = it,
                agent = r.nextBoolean(),
                first = UUID.randomUUID().toString(),
                last = UUID.randomUUID().toString(),
                birth = UUID.randomUUID().toString(),
                age = r.nextInt(200),
                origin = UUID.randomUUID().toString(),
                passport = UUID.randomUUID().toString()
            )
        }
        val shortObjects = Array(SIZE) {
            Substract(left = 5, right = 4)
        }

        val polymorphicObjects = Array(SIZE) {
            when (r.nextInt(0, 4)) {
                0 -> Add(r.nextInt(100), r.nextInt(100))
                1 -> Substract(r.nextInt(100), r.nextInt(100))
                2 -> Increment(r.nextInt(100))
                3 -> Decrement(r.nextInt(100))
                else ->
                    Increment(r.nextInt(100))
            }

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
    fun polymorphicReadMoshiii(sink: Blackhole, state: BenchmarkState) = state.polymorphicString.forEach {
        sink.consume(moshiOperationAdapter.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicRead")
    fun polymorphicReadGsonnnn(sink: Blackhole, state: BenchmarkState) = state.polymorphicString.forEach {
        sink.consume(gsonOperation.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicRead")
    fun polymorphicReadJackson(sink: Blackhole, state: BenchmarkState) = state.polymorphicString.forEach {
        sink.consume(jacksonOperationReader.readValue<Operation>(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumRead")
    fun mediumReadKotlinx(sink: Blackhole, state: BenchmarkState) = state.mediumString.forEach {
        sink.consume(kotlinxJsonSimple.parse(MediumPayload.serializer(), it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumRead")
    fun mediumReadMoshiii(sink: Blackhole, state: BenchmarkState) = state.mediumString.forEach {
        sink.consume(moshiMedium.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumRead")
    fun mediumReadGsonnn(sink: Blackhole, state: BenchmarkState) = state.mediumString.forEach {
        sink.consume(gsonMedium.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumRead")
    fun mediumReadJackson(sink: Blackhole, state: BenchmarkState) = state.mediumString.forEach {
        sink.consume(jacksonMediumPayloadReader.readValue<MediumPayload>(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortRead")
    fun shortReadKotlinx(sink: Blackhole, state: BenchmarkState) = state.shortString.forEach {
        sink.consume(kotlinxJsonSimple.parse(Substract.serializer(), it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortRead")
    fun shortReadMoshiii(sink: Blackhole, state: BenchmarkState) = state.shortString.forEach {
        sink.consume(moshiShort.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortRead")
    fun shortReadGsonnn(sink: Blackhole, state: BenchmarkState) = state.shortString.forEach {
        sink.consume(gsonShort.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortRead")
    @GroupThreads(1)
    fun shortReadJackson(sink: Blackhole, state: BenchmarkState) = state.shortString.forEach {
        sink.consume(jacksonShortPayloadReader.readValue<Substract>(it))
    }


    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicWrite")
    fun polymorphicWriteKotlinx(sink: Blackhole, state: BenchmarkState) = state.polymorphicObjects.forEach {
        sink.consume(kotlinxJsonPolymorphic.stringify(Operation.serializer(), it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicWrite")
    fun polymorphicWriteMoshiii(sink: Blackhole, state: BenchmarkState) = state.polymorphicObjects.forEach {
        sink.consume(moshiOperation.adapter(it.javaClass).toJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicWrite")
    fun polymorphicWriteGsonnnn(sink: Blackhole, state: BenchmarkState) = state.polymorphicObjects.forEach {
        sink.consume(gsonPolymorphic.toJson(it, it.javaClass))
    }


    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicWrite")
    fun polymorphicWriteJackson(sink: Blackhole, state: BenchmarkState) = state.polymorphicObjects.forEach {
        sink.consume(jacksonOperationWriter.writeValueAsString(it))
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
    fun mediumWriteMoshiii(sink: Blackhole, state: BenchmarkState) = state.mediumObjects.forEach {
        sink.consume(moshiMedium.toJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumWrite")
    fun mediumWriteGsonnn(sink: Blackhole, state: BenchmarkState) = state.mediumObjects.forEach {
        sink.consume(gsonMedium.toJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumWrite")
    fun mediumWriteJackson(sink: Blackhole, state: BenchmarkState) = state.mediumObjects.forEach {
        sink.consume(jacksonMediumPayloadWriter.writeValueAsString(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortWrite")
    fun shortWriteKotlinx(sink: Blackhole, state: BenchmarkState) = state.shortObjects.forEach {
        sink.consume(kotlinxJsonSimple.stringify(Substract.serializer(), it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortWrite")
    fun shortWriteMoshiii(sink: Blackhole, state: BenchmarkState) = state.shortObjects.forEach {
        sink.consume(moshiShort.toJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortWrite")
    fun shortWriteGsonnn(sink: Blackhole, state: BenchmarkState) = state.shortObjects.forEach {
        sink.consume(gsonShort.toJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortWrite")
    @GroupThreads(1)
    fun shortWriteJackson(sink: Blackhole, state: BenchmarkState) = state.shortObjects.forEach {
        sink.consume(jacksonShortPayloadWriter.writeValueAsString(it))
    }


}

@ImplicitReflectionSerializer
fun main() {
    val opt = OptionsBuilder().include(SerializationBenchmark::class.java.simpleName)
        .forks(1)
        .build()
    Runner(opt).run()
}
