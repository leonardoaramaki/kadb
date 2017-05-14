kadb
====

An adb client written 100% in [Kotlin][1] that runs on terminal. Remembering that it's still 
a WIP project so bear with me on that. Also this is more like a PoC and study project than a 
full-fledged standalone utility endeavour by itself but anyway there're some plans and many 
more features I want to put my efforts on.

What it does
-------------

 * List all attached devices.
 * Run shell commands directly on device or emulator (no interactive shell **yet**).
 * Pull files from device/emulator.
 * Push files from host.
 * Can queryAdb from the command line using the jar/gradle.
 
For the moment, in order to queryAdb this project you need to clone and build it first. The project 
uses gradle as its build system so it should be simple enough.

The project can now generate a jar file which you can use it as a cli utility, just like adb.

In order to queryAdb the sample

```
./gradlew launch -q
```

This should show the usage screen.

To list all the devices using gradle

```
./gradlew launch -q -Pparams=--devices
```

Okay, that's ugly as hell. Just find the generated jar sitting on the build/libs/ folder and you're 
good to go

```
java -jar build/libs/kadb-all-0.1.0.jar --devices
```

The jar is not there?

```
./gradlew fatJar
```

Example usage
-------------

Listing all connected devices

```
java -jar build/libs/kadb-all-0.1.0.jar --devices
```

Listing files on device's /sdcard/ folder

```
java -jar build/libs/kadb-all-0.1.0.jar -s emulator-5554 -sh 'ls -la /sdcard/'
```

Pull a remote file from device
```
java -jar build/libs/kadb-all-0.1.0.jar -s emulator-5554 --pull /sdcard/some_picture.png

# OR

java -jar build/libs/kadb-all-0.1.0.jar -s emulator-5554 --pull /sdcard/some_picture.png ~/Pictures/

```

Push a local file to device
```
java -jar build/libs/kadb-all-0.1.0.jar --push my_song.ogg /sdcard/

```

Note that you may omit the -s argument if a single device is connected to the host when
therefore kadb shall automatically switch to that device.

LICENSE
-------

    Copyright 2017 Leonardo Aramaki

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[1]: https://kotlinlang.org/