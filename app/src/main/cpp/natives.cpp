// vim: set sts=4 sw=4 tw=99 et:
//
// Copyright (C) 2019 AlliedModders LLC
// Copyright (C) 2019 David Anderson
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

#include <jni.h>

#include <codecvt>
#include <locale>
#include <string>
#include <utility>
#include <vector>

static inline int CodePointSize(jbyte* bytes, int pos, int len) {
    if ((bytes[pos] & 0x80) == 0)
        return 1;

    int cp_length;
    if ((bytes[pos] & 0xf8) == 0xf0) {
        cp_length = 4;
    } else if ((bytes[pos] & 0xf0) == 0xe0) {
        cp_length = 3;
    } else if ((bytes[pos] & 0xe0) == 0xc0) {
        cp_length = 2;
    } else {
        return -1;
    }
    if (pos + cp_length > len)
        return -1;

    for (int i = 1; i < cp_length; i++) {
        if ((bytes[pos + i] & 0xc0) != 0x80)
            return -1;
    }
    return cp_length;
}

class Utf16Cvt : public std::codecvt<char16_t, char, std::mbstate_t>
{
  public:
    ~Utf16Cvt() {}
};

extern "C" jstring
Java_net_alliedmods_stocks_Utilities_fastDecodeUtf8(
        JNIEnv* env,
        jclass type,
        jbyteArray byteArray)
{
    jsize len = env->GetArrayLength(byteArray);
    jbyte* bytes = env->GetByteArrayElements(byteArray, nullptr);

    std::vector<jchar> chars;
    chars.reserve(len);

    std::wstring_convert<Utf16Cvt, char16_t> conv16("utf-8 error", {});

    jsize i = 0;
    while (i < len) {
        if ((bytes[i] & 0x80) != 0x80) {
            chars.emplace_back(bytes[i++]);
        } else {
            int cp_length = CodePointSize(bytes, i, len);
            auto str = conv16.from_bytes(
                    reinterpret_cast<char*>(bytes),
                    reinterpret_cast<char*>(bytes + cp_length));
            for (const auto& c : str)
                chars.emplace_back(c);
        }
    }
    env->ReleaseByteArrayElements(byteArray, bytes, JNI_ABORT);

    return env->NewString(chars.data(), chars.size());
}

extern "C" jobjectArray
Java_net_alliedmods_stocks_Utilities_fastSplitLines(
        JNIEnv* env,
        jclass type,
        jstring text)
{
    jsize len = env->GetStringLength(text);
    const jchar* chars = env->GetStringChars(text, nullptr);

    std::vector<std::pair<size_t, size_t>> extents;

    std::u16string_view cv(reinterpret_cast<const char16_t*>(chars), len);
    size_t last_pos = std::string::npos;
    for (size_t i = 0; i < cv.size(); i++) {
        if (cv[i] == '\r' || cv[i] == '\n') {
            if (last_pos != std::string::npos) {
                extents.emplace_back(std::make_pair(last_pos, i));
                last_pos = std::string::npos;
            }
            continue;
        }

        if (last_pos == std::string::npos)
            last_pos = i;
    }
    if (last_pos != std::string::npos)
        extents.emplace_back(std::make_pair(last_pos, cv.size()));

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(extents.size(), stringClass, nullptr);
    for (size_t i = 0; i < extents.size(); i++) {
        const auto& extent = extents[i];

        jstring line = env->NewString(chars + extent.first, extent.second - extent.first);
        env->SetObjectArrayElement(array, i, line);
        env->DeleteLocalRef(line);
    }
    return array;
}
