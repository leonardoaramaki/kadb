PEEK
====

An adb client written 100% in [Kotlin][1] that runs on terminal. Remembering that it's still 
a WIP project so bear with me on that. Also this is more like a PoC and study project than a 
full-fledged standalone utility endeavour by itself but anyway there're some plans and many 
more features I want to put my efforts on.

What it does
-------------

 * List all attached devices.
 * Run shell commands directly on device or emulator.
 
Future support intended
------------------------

 * Push files from host.
 * Pull files from device/emulator.
 * Wrap everything up as an utility by its own.
 
For the moment, in order to run this project you need to clone and build it first. The project 
uses gradle as its build system so it should be simple enough.

Also notice that by saying "run" I mean it will execute the sample that comes with it since 
it's not a library nor an utility __yet__.

That being said, run the sample with

```
./gradlew run
```

License
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