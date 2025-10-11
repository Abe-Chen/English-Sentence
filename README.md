<p align="center"><img src="fastlane/metadata/android/en-US/images/icon.png" width="150"></p>
<h1 align="center"><b>English Sentence</b></h1>
<h4 align="center">将电影对话转化为“专辑 → 台词 → 歌词”的英语学习体验。</h4>
<p align="center">
    <a href="https://github.com/oxygencobalt/Auxio/releases/tag/v4.0.9">
        <img alt="Latest Version" src="https://img.shields.io/static/v1?label=tag&message=v4.0.9&color=64B5F6&style=flat">
    </a>
    <a href="https://github.com/oxygencobalt/Auxio/releases/">
        <img alt="Releases" src="https://img.shields.io/github/downloads/OxygenCobalt/Auxio/total.svg?color=4B95DE&style=flat">
    </a>
    <a href="https://www.gnu.org/licenses/gpl-3.0">
        <img src="https://img.shields.io/badge/license-GPL%20v3-2B6DBE.svg?style=flat">
    </a>
    <img alt="Minimum SDK Version" src="https://img.shields.io/badge/API-24%2B-1450A8?style=flat">
</p>
<h4 align="center"><a href="/CHANGELOG.md">Changelog</a> | <a href="https://github.com/OxygenCobalt/Auxio/wiki">Wiki</a> | <a href="https://github.com/OxygenCobalt/Auxio#Donate">Donate</a></h4>
<p align="center">
    <a href="https://f-droid.org/app/org.oxycblt.auxio"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" width="250"></a>
    <a href="https://accrescent.app/app/org.oxycblt.auxio">
        <img alt="Get it on Accrescent" src="https://accrescent.app/badges/get-it-on.png" width="250">
    </a>
</p>
<p align="center">
    <a href="https://hosted.weblate.org/engage/auxio/"><img height=64 src="https://hosted.weblate.org/widgets/auxio/-/strings/287x66-grey.png" alt="Translation status" /></a>
</p>

## About

English Sentence 将 Auxio 的音乐播放架构延伸至英语学习场景。用户可以导入本地电影视频，应用会自动提取台词音频与字幕，生成一个学习专辑：

- 每部电影 = 一个专辑
- 每句台词 = 一首“歌曲”
- 台词文本 = 歌词

结合倍速播放、单句循环和后台播放能力，用户可以在熟悉的听歌流程中完成“听、读、跟读”练习。欲了解完整的产品定位与功能规划，请查看 [English Sentence 产品需求文档](docs/EnglishSentence_PRD.md)。

**The default branch is the development version of the repository. For a stable version, see the master branch.**

## Screenshots

<p align="center">
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot0.png" width=250>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot1.png" width=250>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot2.png" width=250>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot3.png" width=250>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot4.png" width=250>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/shot5.png" width=250>
</p>


## Features

- 📁 **导入电影视频**：支持 MP4/MKV，自动提取音轨及内嵌字幕，或手动匹配 `.srt` 文件。
- ✂️ **台词切分**：按字幕时间轴生成句级音频片段并写入专辑、曲目元数据。
- 🎧 **学习专辑**：电影被整理成专辑，台词成为曲目，并以 “Dialog Study” 作为专辑演出者区分。
- 📜 **同步字幕**：播放时显示完整台词文本，并支持自动滚动与单句循环。
- 🕒 **倍速与循环**：保留后台播放、通知栏控制，并提供 0.8x~1.5x 的倍速选择。
- 📚 **媒体库集成**：生成内容存储于 `/Music/EnglishDialog/`，并同步写入系统媒体库与本地数据库。
- ✅ **离线学习**：全部流程离线完成，无需任何云端服务。

## Permissions

- Storage (`READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`) 用于读取与管理生成的学习音频文件
- Services (`FOREGROUND_SERVICE`, `WAKE_LOCK`) 确保音频提取与学习播放在前台服务中稳定运行
- Notifications (`POST_NOTIFICATION`) 用于显示生成任务与持续播放的通知

## Donate

You can support Auxio's development through [my Github Sponsors page](https://github.com/sponsors/OxygenCobalt). Get the ability to prioritize features and have your profile added to the README, Release Changelogs, and even the app itself!

<p align="center"><b>$8/month supporters:</b></p>

<p align="center">
    <a href="https://github.com/alanorth"><img src="https://avatars.githubusercontent.com/u/191754?v=4" width=50 /></a>
</p>

## Building

Auxio relies on a patched version of Media3 that enables some extra playback features, alongside taglib for metadata
parsing. This adds some caveats to the build process:
1. `cmake` and `ninja-build` must be installed before building the project.
2. The project uses submodules, so when cloning initially, use `git clone --recurse-submodules` to properly
download the external code.
3. You are **unable** to build this project on windows, as the custom Media3 build runs shell scripts that
will only work on unix-based systems.

## Contributing

Auxio accepts most contributions as long as they follow the [Contribution Guidelines](/.github/CONTRIBUTING.md).

However, feature additions and major UI changes are less likely to be accepted. See
[Why Are These Features Missing?](https://github.com/OxygenCobalt/Auxio/wiki/Why-Are-These-Features-Missing%3F)
for more information.



## License

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

Auxio is Free Software: You can use, study share and improve it at your
will. Specifically you can redistribute and/or modify it under the terms of the
[GNU General Public License](https://www.gnu.org/licenses/gpl.html) as
published by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

More information can be found [here](https://github.com/OxygenCobalt/Auxio/wiki/Licenses).
