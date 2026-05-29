# 福彩双色球 🎱

红金经典风格双色球选号APP，支持Android平台。

## 功能

1. **🎲 机选一注** — 一键随机生成6个红球+1个蓝球，3D球动画
2. **📊 最新3期一等奖号码** — 联网从数据源拉取实时开奖数据，支持手动刷新
3. **🔍 期号查询** — 输入期号（如2026049）查询指定开奖号码

## 技术架构

- 原生Android应用（WebView加载本地HTML/JS/CSS资源）
- 应用名：**福彩双色球**
- 包名：`com.ssq.app`
- 编译SDK：34 | 最低支持：Android 5.0 (API 21)
- 网络请求通过 Android `@JavascriptInterface` 桥接，在子线程执行

## 快速构建

任何 `push` 到 `main` 分支都会触发 GitHub Actions 自动构建APK：

1. 提交代码并推送到 main
2. 进入 GitHub → Actions 页面查看进度
3. 构建完成后下载 `福彩双色球` 工件
4. 解压即得APK，可直接安装

## 发布到应用市场

### 1. 配置正式签名（一次配置，永久使用）

要将APK发布到华为/小米/OPPO/vivo等应用市场，需要有签名证书。

**方式一：自动生成（推荐，CI自动处理）**
在 GitHub 仓库的 Settings → Secrets and variables → Actions 中添加：

| Secret 名称 | 说明 | 如何获取 |
|------------|------|---------|
| `KEYSTORE_BASE64` | 签名证书文件(base64编码) | 见下方"生成签名" |
| `KEYSTORE_PASSWORD` | 证书密码 | 生成时设置的密码 |
| `KEY_ALIAS` | 别名 | 生成时的alias |
| `KEY_PASSWORD` | 密钥密码 | 同证书密码 |

设置后，每次构建会自动生成 **debug + release** 两个版本。

### 2. 生成签名证书

```bash
# 安装 Java 17
sudo apt install openjdk-17-jdk-headless

# 生成签名证书
keytool -genkey -v -keystore ssq-release.keystore \
  -alias ssq -keyalg RSA -keysize 2048 -validity 36500

# Base64编码（用于GitHub Secrets）
base64 -w0 ssq-release.keystore

# 输出结果粘贴到 GitHub → Settings → Secrets → KEYSTORE_BASE64
# 密码和alias分别填入 KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
```

### 3. 各市场上架要求

| 市场 | 注册 | 审核周期 | 主要材料 |
|------|------|----------|---------|
| **华为** | 个人免费 | 1-3天 | 隐私政策 |
| **小米** | 个人免费 | 1-3天 | 应用截图+描述 |
| **OPPO** | 个人免费 | 1-3天 | 隐私政策 |
| **vivo** | 个人免费 | 1-3天 | 隐私政策 |

### 4. 隐私政策（上架必填）

建议使用 GitHub Pages 托管一个简单的隐私声明页面，内容如：
> "本应用仅用于查询中国福利彩票双色球的开奖信息，不会收集、存储或上传用户的任何个人信息。"

## 开发

```bash
# 修改UI/功能 → 编辑
android/app/src/main/assets/index.html

# 提交并触发自动构建
git add -A && git commit -m "feat: xxx" && git push origin main
```
