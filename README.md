# kotlin-json-benchmark
Compare performance of GSON, Moshi, Jackson and KotlinX.Serialization using JMH

Compare read/write performance for simple data classes and sealed (polymorphic) data classes.
(Byte)code generation is used for KotlinX and Moshi, Jackson utilizes Afterburner module.
