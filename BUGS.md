# 七乐彩App 缺陷清单

## 已发现（需要修复的）

### Bug 1: 搜索框提示文字错误
- **位置**: `index.html` line ~465 (search-input placeholder)
- **问题**: placeholder 可能写的是双色球的期号提示（如"输入2026049"），需要确认是七乐彩格式
- **预期**: 提示应为"输入期号 如 2026049"（七乐彩期号同样7位格式）

### Bug 2: 玩法规则页面背景与phone容器背景冲突
- **位置**: CSS `.rule-page` + HTML 结构
- **问题**: `rule-page` 使用 `position:fixed` 但rule-pageDiv DOM被放在 `</div>`（phone容器）之后，position:fixed会定位到整个页面（不是.phone容器范围），可能在某些屏幕尺寸下看不全
- **验证方法**: 真机点击"玩法规则"，看弹出层是否覆盖全屏
  
### Bug 3: MainActivity.java 中七乐彩数据解析
- **位置**: MainActivity.java `parseQlcRows` 方法
- **问题**: 500.com 的七乐彩HTML格式可能和双色球略有不同（已确认格式正确，但需要实际验证）
- **验证**: 需要真机联网测试，看最近25期能否正常加载

### Bug 4: 分享记录文件名
- **位置**: MainActivity.java `saveDraw` / `getRecordText`
- **问题**: 文件中"恭喜发财"标题下记录格式是否对齐七乐彩（7个号码+特别号）

### Bug 5: 开奖数据更新频率
- **位置**: MainActivity.java fetchLatestDraws 的 URL: `start=25001&end=26099`
- **问题**: 这个范围可能偏大，端点的26099可能不是最新期号。应该动态获取最新期号
- **建议**: 把end改成更大的值如26999，或者让Java层动态计算

## 需验证（真机测试才能知道）

### V1: 数据能否正常加载
- 打开App后，最近25期是否会显示"Android桥接不可用"？
- 用真机安装后检查"最近25期"区域能否正常加载七乐彩历史数据

### V2: 玩法规则能否正常关闭
- 点击✕和右滑是否正常关闭玩法规则页面

### V3: 支付流程
- 前2次免费机选 → 第3次弹出支付 → 支付成功后永久解锁

### V4: 机选号码范围
- 是否生成7个1-30范围内的号码
- 特别号是否合理显示

### V5: 搜索功能
- 输入七乐彩期号能否查到结果

## 已知OK的功能（已验证代码层面正确）
- ✅ genNums(count=7) 生成7个1-30号码
- ✅ genSpecial() 生成1个特别号
- ✅ ball-purple 样式正确（紫金色渐变）
- ✅ 所有HTML元素: rule-link, rule-page, ruleClose, refreshBtn, payOverlay, genBtn, shareBtn, clearBtn, searchBtn, periodInput
- ✅ touch事件: 右滑关闭规则页面（touchstart + touchmove 带 passive:true）
- ✅ fetchLatestPeriods / doSearch 逻辑完整
- ✅ parseDrawData 解析9字段（1期号+7号码+1特别号）
- ✅ 支付逻辑完整（免费次数、模拟支付、onPaySuccess回调）
- ✅ CSS: rule-page position:fixed, rule-link -webkit-tap-highlight-color
- ✅ #latestPeriods height:165px 固定高度
- ✅ keydown Enter 搜索
