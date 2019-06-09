# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.4.1)

add_library(native-lib SHARED
            src/main/cpp/natives.cpp)

# find_library(log-lib
#              log)

target_link_libraries(native-lib)
