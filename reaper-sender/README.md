# StageLink Sender VST3

JUCE/CMake VST3 sender. Insert it on a stereo REAPER monitor bus. The plug-in is transparent to the bus audio and copies samples into a lock-free FIFO for a separate UDP thread.

Requirements:
- CMake 3.22+
- C++20 compiler
- Git (CMake FetchContent downloads JUCE 9.0.1)
- REAPER project/audio device at 48 kHz

Build:

```powershell
cmake -S . -B build -G "Visual Studio 17 2022" -A x64
cmake --build build --config Release
```

JUCE licensing: JUCE is dual-licensed (AGPL/commercial). Review JUCE licensing before distributing a closed-source/commercial build.
