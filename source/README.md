# Ag:reborn source

The [repository README](../README.md) covers build instructions and snapshot scope. This is the `0.81.5-city-a3.82` source for Minecraft 1.21.1, NeoForge 21.1.252 and Java 21. Gradle builds the embedded ACA bio HUM 0.9.1, Fauna 0.1.18, Mech 0.4.5, Omnitrix 0.8.4, and Infinity 0.5.4 modules from `src/hum`, `src/fauna`, `src/mech`, `src/omnitrix`, and `src/infinity`.

## Local QA on the 16 GiB Mac

Ordinary QA jobs may run together. The runner checks macOS memory pressure, estimated available memory, actual CPU load and outstanding reservations before admitting a job. It counts JDK processes started outside the runner as well. Frame-time comparisons take an exclusive group lock. The three roles of a dedicated session use all three local process slots.

| Job on this 12-core M6 | JDK slots | Expected resident MiB | CPU allowance | Scheduling |
|---|---:|---:|---:|---|
| Small regression JVM | 1 | 384 | 1 | Shared |
| Normal single-player benchmark | 1 | 4608 | 4 | Exclusive |
| Dedicated server and two clients | 3 | 6144 | 6 | Whole process budget |
| Gradle build | 3 | 2560 | 2 | Whole process budget |

Run from this directory. For example, the local build command is:

```sh
python3 qa/local_java_scenario.py --jvms 3 --memory-mib 2560 --cores 2 -- ./gradlew --no-parallel --max-workers=2 --no-daemon jar
```

Wrap a game or test launch command the same way. Use `--exclusive --quiet-java` for frame timings and `--log <file.jsonl>` to record sampled JDK process counts and resident memory. The log distinguishes this job’s JVMs from independently started external processes. Both builds of the single-player benchmark use a 4096 MiB heap and four visible CPUs. The dedicated checks use a 1536 MiB server heap and a 2048 MiB heap on each client, with two visible CPUs per process. The memory reservations include native allocations beyond the heap. Select limits based on available memory and CPU on other hosts.
