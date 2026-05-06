# MaoqiuPlayer

MaoqiuPlayer，中文名“毛球播放器”，是一款跨平台本地媒体播放器，支持常见视频、图片格式播放，并支持专用媒体包格式 `.mqp`。

软件界面以普通本地播放器为主：左侧导航、顶部搜索、媒体库、最近播放、本地视频、本地图片、播放列表和设置。高级媒体包能力放在设置中的高级工具里，不作为首页主功能展示。

版本：0.1.0  
GitHub 仓库名：`maoqiu-player`

## 主要功能

- 本地视频播放：播放、暂停、进度、音量、全屏和倍速预留。
- 图片查看：缩放、适应窗口、上一张、下一张和旋转预留。
- 媒体库：导入本地视频和图片，支持搜索、分类和排序。
- 最近播放：记录本机最近打开的媒体。
- 播放列表：为后续多列表管理预留页面结构。
- 浅色/深色主题：默认深色主题，设置中可切换浅色主题。

## 高级功能

高级功能入口位于：

```text
设置 -> 高级设置 -> 高级工具 -> 媒体包管理
```

高级工具支持：

- 生成专用媒体包
- 导入媒体包
- 查看媒体包信息
- 校验媒体包
- 缓存清理
- 数据库维护

媒体包默认后缀为 `.mqp`，自定义文件头 Magic 为：

```text
MAOQIU_PLAYER_ENC_V1
```

媒体包数据采用加密存储，适合本地媒体整理和私有化管理。界面文案统一使用“媒体包”“私有媒体包”“导出包”等播放器语境表达。

## 安装

建议使用 Python 3.11+。

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## 运行

```bash
python main.py
```

## 页面结构

左侧导航栏：

- 首页
- 最近播放
- 本地视频
- 本地图片
- 播放列表
- 设置

设置页面：

- 常规
- 播放
- 媒体库
- 缓存
- 高级设置
- 关于

高级设置页面：

- 高级工具
- 媒体包管理
- 清理缓存
- 数据库维护
- 文件校验

媒体包管理页面：

- 生成媒体包
- 导入媒体包
- 查看媒体包信息
- 校验媒体包

## 安全说明

`.mqp` 是 MaoqiuPlayer 的专用媒体包格式。底层格式使用 `MAOQIU_PLAYER_ENC_V1` 作为文件识别头，并通过标准加密库保护包内媒体数据。

本项目不会尝试绕过任何平台、服务或第三方媒体的访问控制。媒体包功能仅用于用户自有本地媒体的整理、导出和私有化管理。

## 测试

```bash
pytest
```

## 在线打包

推送到 GitHub `main` 分支后，GitHub Actions 会自动运行 `Package MaoqiuPlayer` 工作流，构建并上传临时 artifacts。推送 `v*` 标签后，工作流会创建 GitHub Release 并上传：

- `MaoqiuPlayer-macOS`
- `MaoqiuPlayer-Windows`
- `MaoqiuPlayer-Linux`

示例：

```bash
git tag v0.1.0
git push origin v0.1.0
```

也可以在 GitHub Actions 页面手动触发 `workflow_dispatch` 重新打包，手动触发只上传 artifacts，不创建 Release。
