## 任务：修复七乐彩App缺陷

### 项目目录
C:\Users\admin\.openclaw\workspace\qlc-app

### 源文件
- HTML: app/src/main/assets/index.html
- Java: app/src/main/java/com/ssq/app/MainActivity.java

### 编译命令
cd C:\Users\admin\.openclaw\workspace\qlc-app && gradlew.bat assembleDebug

### 修复清单
参照 BUGS.md 文件中的缺陷逐项修复。

### 约束
1. 不破坏任何现有功能（机选、支付、分享等）
2. 不改动应用包名 com.ssq.app
3. 不改动版本号
4. 不改动gradle配置
5. 不改动res图标资源

### 验收标准
- [ ] 编译通过（gradlew.bat assembleDebug）
- [ ] APK 打包成功
- [ ] 代码无结构性错误

### 输出
完成后输出：改了什么文件，改了什么内容
