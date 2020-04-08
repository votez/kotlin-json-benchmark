package jsonify

import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import jsonify.*
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
@Measurement(iterations = 3, time = 200,timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class SerializationBenchmark {
    companion object {
        const val SIZE = 300
        private val r = Random.Default
    }

    private val kotlinxJsonPolymorphic = Json(JsonConfiguration.Stable.copy(classDiscriminator = "op"))
    private val kotlinxJsonSimple = Json(JsonConfiguration.Stable)

    private val moshiOperation = Moshi.Builder()
        .add(
            PolymorphicJsonAdapterFactory.of(Operation::class.java, "op")
                .withSubtype(Add::class.java, "add")
                .withSubtype(Substract::class.java, "sub")
                .withSubtype(Increment::class.java, "inc")
                .withSubtype(Decrement::class.java, "dec")
        )
        .build()
        .adapter(Operation::class.java)

    private val moshiMedium = Moshi.Builder()
        .build().adapter(MediumPayload::class.java)
    private val moshiShort = Moshi.Builder()
        .build().adapter(ShortPayload::class.java)

    private val jacksonOperationReader: ObjectReader
    private val jacksonMediumPayloadReader: ObjectReader
    private val jacksonShortPayloadReader: ObjectReader

    private val jacksonOperationWriter: ObjectWriter
    private val jacksonMediumPayloadWriter: ObjectWriter
    private val jacksonShortPayloadWriter: ObjectWriter

    private val gsonOperation: TypeAdapter<Operation>
    private val gsonShort: TypeAdapter<ShortPayload>
    private val gsonMedium: TypeAdapter<MediumPayload>

    init {
        val jacksonPolymorphic = jacksonObjectMapper()
        val jacksonSimple = jacksonObjectMapper()
        val typeValidator = BasicPolymorphicTypeValidator.builder().build()
        jacksonPolymorphic.activateDefaultTyping(typeValidator)
        jacksonOperationReader = jacksonPolymorphic.readerFor(Operation::class.java)
        jacksonMediumPayloadReader = jacksonSimple.readerFor(MediumPayload::class.java)
        jacksonShortPayloadReader = jacksonSimple.readerFor(ShortPayload::class.java)
        jacksonOperationWriter = jacksonPolymorphic.writerFor(Operation::class.java)
        jacksonMediumPayloadWriter = jacksonSimple.writerFor(MediumPayload::class.java)
        jacksonShortPayloadWriter = jacksonSimple.writerFor(ShortPayload::class.java)

        val gsonPolymorphic = GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapterFactory(
                RuntimeTypeAdapterFactory
                    .of(Operation::class.java, "op")
                    .registerSubtype(Add::class.java, "add")
                    .registerSubtype(Substract::class.java, "sub")
                    .registerSubtype(Decrement::class.java, "dec")
                    .registerSubtype(Increment::class.java, "inc")
            ).create()

        val gsonSimple = GsonBuilder()
            .disableHtmlEscaping()
            .create()

        gsonOperation = gsonPolymorphic.getAdapter(Operation::class.java)
        gsonShort = gsonSimple.getAdapter(ShortPayload::class.java)
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
            """{"number":${it},"origin":"${UUID.randomUUID()}","agent":${r.nextBoolean()},"firstName":"${UUID.randomUUID()}","lastName":"${UUID.randomUUID()}","birthPlace":"${UUID.randomUUID()}","age":${r.nextInt(
                200
            )},"passport":"${UUID.randomUUID()}"}"""
        }
        val shortString = Array(SIZE) {
            """{"number":${it},"name":"${UUID.randomUUID()}"}"""
        }

        val mediumObjects = Array(SIZE) {
            MediumPayload(
                number = it,
                agent = r.nextBoolean(),
                firstName = UUID.randomUUID().toString(),
                lastName = UUID.randomUUID().toString(),
                birthPlace = UUID.randomUUID().toString(),
                age = r.nextInt(200),
                origin = UUID.randomUUID().toString(),
                passport = UUID.randomUUID().toString()
            )
        }
        val shortObjects = Array(SIZE) {
            ShortPayload(number = it, name = UUID.randomUUID().toString())
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
    fun polymorphicReadMoshi__(sink: Blackhole, state: BenchmarkState) = state.polymorphicString.forEach {
        sink.consume(moshiOperation.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicRead")
    fun polymorphicReadGson___(sink: Blackhole, state: BenchmarkState) = state.polymorphicString.forEach {
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
    fun mediumReadMoshi__(sink: Blackhole, state: BenchmarkState) = state.mediumString.forEach {
        sink.consume(moshiMedium.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumRead")
    fun mediumReadGson___(sink: Blackhole, state: BenchmarkState) = state.mediumString.forEach {
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
        sink.consume(kotlinxJsonSimple.parse(ShortPayload.serializer(), it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortRead")
    fun shortReadMoshi__(sink: Blackhole, state: BenchmarkState) = state.shortString.forEach {
        sink.consume(moshiShort.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortRead")
    fun shortReadGson___(sink: Blackhole, state: BenchmarkState) = state.shortString.forEach {
        sink.consume(gsonShort.fromJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortRead")
    @GroupThreads(1)
    fun shortReadJackson(sink: Blackhole, state: BenchmarkState) = state.shortString.forEach {
        sink.consume(jacksonShortPayloadReader.readValue<ShortPayload>(it))
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
    fun polymorphicWriteMoshi__(sink: Blackhole, state: BenchmarkState) = state.polymorphicObjects.forEach {
        sink.consume(moshiOperation.toJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("polymorphicWrite")
    fun polymorphicWriteGson___(sink: Blackhole, state: BenchmarkState) = state.polymorphicObjects.forEach {
        sink.consume(gsonOperation.toJson(it))
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
    fun mediumWriteMoshi__(sink: Blackhole, state: BenchmarkState) = state.mediumObjects.forEach {
        sink.consume(moshiMedium.toJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("mediumWrite")
    fun mediumWriteGson___(sink: Blackhole, state: BenchmarkState) = state.mediumObjects.forEach {
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
        sink.consume(kotlinxJsonSimple.stringify(ShortPayload.serializer(), it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortWrite")
    fun shortWriteMoshi__(sink: Blackhole, state: BenchmarkState) = state.shortObjects.forEach {
        sink.consume(moshiShort.toJson(it))
    }

    @Benchmark
    @OperationsPerInvocation(SIZE)
    @Group("shortWrite")
    fun shortWriteGson___(sink: Blackhole, state: BenchmarkState) = state.shortObjects.forEach {
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
