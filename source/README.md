# Ag:reborn source

The [repository README](../README.md) covers build instructions and snapshot scope. This is the `0.81.5-city-a3.82` source for Minecraft 1.21.1, NeoForge 21.1.252 and Java 21. Gradle builds the embedded ACA bio HUM 0.9.1, Fauna 0.1.18, Mech 0.4.5, Omnitrix 0.8.4, and Infinity 0.5.4 modules from `src/hum`, `src/fauna`, `src/mech`, `src/omnitrix`, and `src/infinity`.

## Local QA on the 16 GiB Mac

Start one scenario at a time from this directory. The runner shares a per-user lock, counts running JDK processes, and waits for normal macOS memory pressure before starting. Use the same runner for game sessions and local builds.

```sh
python3 qa/local_java_scenario.py --jvms 1 --max-jvms 3 -- <single-player command>
python3 qa/local_java_scenario.py --jvms 3 --max-jvms 3 -- <dedicated server and both clients command>
python3 qa/local_java_scenario.py --jvms 3 --max-jvms 3 -- ./gradlew --no-parallel --max-workers=2 --no-daemon jar
```

On the 12-core M6, use a 1536 MiB server heap and two 2048 MiB client heaps for the dedicated checks, with `-XX:ActiveProcessorCount=2` per process. The paired single-player performance runs use the same 4096 MiB heap and four visible CPUs for both builds. Add `--log <file.jsonl>` before the command separator to record sampled process counts and resident memory. Select limits from the available memory on other hosts.
