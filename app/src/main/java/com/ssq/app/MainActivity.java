package com.ssq.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private PayManager payManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        payManager = new PayManager();
        webView.addJavascriptInterface(new NativeBridge(), "androidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // ====== 支付管理 ======
    private class PayManager {
        private static final String PREFS_NAME = "qlc_pay";
        private static final String KEY_FREE_REMAIN = "free_remain";
        private static final String KEY_IS_PAID = "is_paid";
        private static final int DEFAULT_FREE = 2;

        private SharedPreferences prefs;

        PayManager() {
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            if (!prefs.contains(KEY_FREE_REMAIN)) {
                prefs.edit().putInt(KEY_FREE_REMAIN, DEFAULT_FREE).apply();
            }
        }

        boolean consumeFree() {
            int remain = prefs.getInt(KEY_FREE_REMAIN, 0);
            if (remain > 0) {
                prefs.edit().putInt(KEY_FREE_REMAIN, remain - 1).apply();
                return true;
            }
            return false;
        }

        boolean needsPayment() {
            return getFreeRemain() <= 0 && !isPaid();
        }

        int getFreeRemain() {
            return prefs.getInt(KEY_FREE_REMAIN, 0);
        }

        boolean isPaid() {
            return prefs.getBoolean(KEY_IS_PAID, false);
        }

        void markPaid() {
            prefs.edit().putBoolean(KEY_IS_PAID, true).putInt(KEY_FREE_REMAIN, 0).apply();
        }
    }

    // Convert 7-digit period (2026049) to 5-digit (26049)
    private String toFiveDigitPeriod(String sevenDigitPeriod) {
        if (sevenDigitPeriod == null || sevenDigitPeriod.length() != 7) return sevenDigitPeriod;
        return sevenDigitPeriod.substring(2);
    }

    // Convert 5-digit period (26049) to 7-digit (2026049)
    private String toSevenDigitPeriod(String fiveDigitPeriod) {
        if (fiveDigitPeriod == null || fiveDigitPeriod.length() != 5) return fiveDigitPeriod;
        return "20" + fiveDigitPeriod;
    }

    private class NativeBridge {
        private String doHttpRequest(String urlStr) throws Exception {
            final String[] result = new String[1];
            final Exception[] error = new Exception[1];
            Thread t = new Thread(() -> {
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    conn.setRequestProperty("Accept", "text/html");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "GBK"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    result[0] = sb.toString();
                } catch (Exception e) {
                    error[0] = e;
                }
            });
            t.start();
            t.join(15000);
            if (error[0] != null) throw error[0];
            return result[0];
        }

        /**
         * Parse QLC HTML rows.
         * Format in HTML:
         * <td class="t_tr1">26060</td><td class="cfont2">02 09 16 19 22 28 29<span class="cBlue"> 03</span></td>
         * 7 basic numbers (space separated) + 1 special number (in cBlue span)
         */
        private List<String> parseQlcRows(String content, int maxRows) {
            // Pattern: find rows with period and number td
            Pattern rowPattern = Pattern.compile(
                "<td[^>]*class=\"t_tr1\"[^>]*>(\\d{5})</td>" +
                "\\s*<td[^>]*class=\"cfont2\"[^>]*>([\\d\\s]+)<span[^>]*class=\"cBlue\"[^>]*>\\s*(\\d{1,2})\\s*</span>"
            );
            Matcher matcher = rowPattern.matcher(content);

            List<String> results = new ArrayList<>();
            while (matcher.find() && results.size() < maxRows) {
                String period5 = matcher.group(1);
                String period7 = toSevenDigitPeriod(period5);
                String numbersStr = matcher.group(2).trim();
                String specialNum = matcher.group(3).trim();

                // Split the basic numbers (space separated)
                String[] nums = numbersStr.split("\\s+");
                if (nums.length != 7) continue; // Expect exactly 7 basic numbers

                StringBuilder sb = new StringBuilder(period7);
                for (String n : nums) {
                    sb.append(",").append(Integer.parseInt(n));
                }
                sb.append(",").append(Integer.parseInt(specialNum));
                results.add(sb.toString());
            }
            return results;
        }

        @JavascriptInterface
        public String fetchLatestDraws(int pageSize) {
            try {
                String content = doHttpRequest(
                    "https://datachart.500.com/qlc/history/newinc/history.php?start=25001&end=26099"
                );

                List<String> results = parseQlcRows(content, pageSize);

                if (!results.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (String r : results) sb.append(r).append(";");
                    return sb.toString();
                }
                return "ERROR_NO_DATA";
            } catch (Exception e) {
                return "ERROR:" + e.getClass().getSimpleName() + ":" + e.getMessage();
            }
        }

        @JavascriptInterface
        public String searchDraw(String period7) {
            try {
                String period5 = toFiveDigitPeriod(period7);
                String content = doHttpRequest(
                    "https://datachart.500.com/qlc/history/newinc/history.php?start=" + period5 + "&end=" + period5
                );

                // Use a stricter pattern for single period search
                Pattern rowPattern = Pattern.compile(
                    "<td[^>]*class=\"t_tr1\"[^>]*>(" + Pattern.quote(period5) + ")</td>" +
                    "\\s*<td[^>]*class=\"cfont2\"[^>]*>([\\d\\s]+)<span[^>]*class=\"cBlue\"[^>]*>\\s*(\\d{1,2})\\s*</span>"
                );
                Matcher matcher = rowPattern.matcher(content);
                if (matcher.find()) {
                    String numbersStr = matcher.group(2).trim();
                    String specialNum = matcher.group(3).trim();
                    String[] nums = numbersStr.split("\\s+");
                    if (nums.length == 7) {
                        StringBuilder sb = new StringBuilder(period7);
                        for (String n : nums) {
                            sb.append(",").append(Integer.parseInt(n));
                        }
                        sb.append(",").append(Integer.parseInt(specialNum));
                        return sb.toString();
                    }
                }
                return "ERROR_NOT_FOUND";
            } catch (Exception e) {
                return "ERROR:" + e.getClass().getSimpleName() + ":" + e.getMessage();
            }
        }

        // ====== 机选记录 + 分享功能 ======

        @JavascriptInterface
        public void saveDraw(String redsStr, String blueStr) {
            try {
                File recordsDir = new File(getFilesDir(), "records");
                if (!recordsDir.exists()) {
                    recordsDir.mkdirs();
                }
                File file = new File(recordsDir, "机选记录.txt");
                boolean isNew = !file.exists();
                String line = redsStr + " + " + blueStr + "\n";
                FileOutputStream fos = new FileOutputStream(file, true);
                OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
                if (isNew) {
                    writer.write("恭喜发财\n");
                }
                writer.write(line);
                writer.close();
                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void clearRecord() {
            try {
                File file = new File(getFilesDir(), "records/机选记录.txt");
                if (file.exists()) {
                    file.delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public String getRecordText() {
            try {
                File file = new File(getFilesDir(), "records/机选记录.txt");
                if (!file.exists()) {
                    return "暂无机选记录，快去机选一注吧！";
                }
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(openFileInput("records/机选记录.txt"), "UTF-8")
                );
                StringBuilder sb = new StringBuilder();
                sb.append("=== 福彩七乐彩 机选记录 ===\n\n");
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                return sb.toString();
            } catch (Exception e) {
                return "读取记录失败";
            }
        }

        @JavascriptInterface
        public void shareRecord() {
            try {
                File file = new File(getFilesDir(), "records/机选记录.txt");
                if (!file.exists()) {
                    return;
                }

                Uri contentUri = FileProvider.getUriForFile(
                    MainActivity.this,
                    getPackageName() + ".fileprovider",
                    file
                );

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                Intent chooser = Intent.createChooser(shareIntent, "分享机选记录到");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // ====== 支付接口 ======

        @JavascriptInterface
        public boolean consumeFree() {
            return payManager.consumeFree();
        }

        @JavascriptInterface
        public boolean needsPayment() {
            return payManager.needsPayment();
        }

        @JavascriptInterface
        public int getFreeRemain() {
            return payManager.getFreeRemain();
        }

        @JavascriptInterface
        public boolean isPaid() {
            return payManager.isPaid();
        }

        @JavascriptInterface
        public void startPay(final String channel) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 模拟支付遮罩弹窗
                    LinearLayout overlay = new LinearLayout(MainActivity.this);
                    overlay.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ));
                    overlay.setOrientation(LinearLayout.VERTICAL);
                    overlay.setBackgroundColor(0xCC000000);
                    overlay.setPadding(48, 0, 48, 0);
                    overlay.setGravity(android.view.Gravity.CENTER);

                    // 弹窗卡片
                    LinearLayout card = new LinearLayout(MainActivity.this);
                    card.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ));
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setBackgroundResource(android.R.color.transparent);
                    card.setPadding(24, 32, 24, 32);
                    card.setGravity(android.view.Gravity.CENTER);

                    android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
                    cardBg.setCornerRadius(16);
                    cardBg.setColor(0xFF1A0A0A);
                    cardBg.setStroke(1, 0x22FFD700);
                    card.setBackground(cardBg);

                    // 标题
                    TextView titleView = new TextView(MainActivity.this);
                    titleView.setText("支付解锁");
                    titleView.setTextSize(20);
                    titleView.setTextColor(0xFFFFD700);
                    titleView.setGravity(android.view.Gravity.CENTER);
                    titleView.setPadding(0, 0, 0, 8);

                    // 金额
                    TextView amountView = new TextView(MainActivity.this);
                    amountView.setText("¥1.00");
                    amountView.setTextSize(36);
                    amountView.setTextColor(0xFFFFD700);
                    amountView.setGravity(android.view.Gravity.CENTER);
                    amountView.setPadding(0, 0, 0, 4);
                    amountView.setTypeface(null, android.graphics.Typeface.BOLD);

                    // 说明
                    TextView descView = new TextView(MainActivity.this);
                    descView.setText("2次免费机会已用完，支付1元即可永久解锁");
                    descView.setTextSize(13);
                    descView.setTextColor(0x99FFD700);
                    descView.setGravity(android.view.Gravity.CENTER);
                    descView.setPadding(0, 0, 0, 24);

                    // 支付宝按钮
                    Button alipayBtn = new Button(MainActivity.this);
                    alipayBtn.setText("支付宝支付");
                    alipayBtn.setTextSize(15);
                    alipayBtn.setTextColor(0xFFFFFFFF);
                    alipayBtn.setPadding(0, 14, 0, 14);
                    alipayBtn.setAllCaps(false);
                    alipayBtn.setTypeface(null, android.graphics.Typeface.BOLD);
                    android.graphics.drawable.GradientDrawable alipayBg = new android.graphics.drawable.GradientDrawable();
                    alipayBg.setCornerRadius(10);
                    alipayBg.setColor(0xFF1677FF);
                    alipayBtn.setBackground(alipayBg);
                    LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    btnLp.setMargins(0, 0, 0, 10);
                    alipayBtn.setLayoutParams(btnLp);

                    // 微信按钮
                    Button wxBtn = new Button(MainActivity.this);
                    wxBtn.setText("微信支付");
                    wxBtn.setTextSize(15);
                    wxBtn.setTextColor(0xFFFFFFFF);
                    wxBtn.setPadding(0, 14, 0, 14);
                    wxBtn.setAllCaps(false);
                    wxBtn.setTypeface(null, android.graphics.Typeface.BOLD);
                    android.graphics.drawable.GradientDrawable wxBg = new android.graphics.drawable.GradientDrawable();
                    wxBg.setCornerRadius(10);
                    wxBg.setColor(0xFF07C160);
                    wxBtn.setBackground(wxBg);
                    LinearLayout.LayoutParams wxBtnLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    wxBtnLp.setMargins(0, 0, 0, 10);
                    wxBtn.setLayoutParams(wxBtnLp);

                    // 取消按钮
                    Button cancelBtn = new Button(MainActivity.this);
                    cancelBtn.setText("取消");
                    cancelBtn.setTextSize(14);
                    cancelBtn.setTextColor(0x88FFD700);
                    cancelBtn.setPadding(0, 10, 0, 10);
                    cancelBtn.setAllCaps(false);
                    android.graphics.drawable.GradientDrawable cancelBg = new android.graphics.drawable.GradientDrawable();
                    cancelBg.setCornerRadius(10);
                    cancelBg.setColor(0x22FFD700);
                    cancelBtn.setBackground(cancelBg);
                    cancelBtn.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ));

                    // —— 处理中遮罩 ——
                    final LinearLayout loadingOverlay = new LinearLayout(MainActivity.this);
                    loadingOverlay.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ));
                    loadingOverlay.setOrientation(LinearLayout.VERTICAL);
                    loadingOverlay.setGravity(android.view.Gravity.CENTER);
                    loadingOverlay.setBackgroundColor(0xEE000000);
                    loadingOverlay.setPadding(48, 0, 48, 0);

                    LinearLayout loadingCard = new LinearLayout(MainActivity.this);
                    loadingCard.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ));
                    loadingCard.setOrientation(LinearLayout.VERTICAL);
                    loadingCard.setGravity(android.view.Gravity.CENTER);
                    android.graphics.drawable.GradientDrawable lcBg = new android.graphics.drawable.GradientDrawable();
                    lcBg.setCornerRadius(16);
                    lcBg.setColor(0xFF1A0A0A);
                    loadingCard.setBackground(lcBg);
                    loadingCard.setPadding(40, 32, 40, 32);

                    TextView loadingTitle = new TextView(MainActivity.this);
                    loadingTitle.setText("支付处理中...");
                    loadingTitle.setTextSize(16);
                    loadingTitle.setTextColor(0xFFFFD700);
                    loadingTitle.setGravity(android.view.Gravity.CENTER);
                    loadingTitle.setPadding(0, 0, 0, 8);

                    TextView loadingDesc = new TextView(MainActivity.this);
                    String channelName = "alipay".equals(channel) ? "支付宝" : "微信";
                    loadingDesc.setText(channelName + "支付\n请在外部应用完成支付");
                    loadingDesc.setTextSize(13);
                    loadingDesc.setTextColor(0x88FFD700);
                    loadingDesc.setGravity(android.view.Gravity.CENTER);

                    loadingCard.addView(loadingTitle);
                    loadingCard.addView(loadingDesc);
                    loadingOverlay.addView(loadingCard);

                    // 组装卡片
                    card.addView(titleView);
                    card.addView(amountView);
                    card.addView(descView);
                    card.addView(alipayBtn);
                    card.addView(wxBtn);
                    card.addView(cancelBtn);
                    overlay.addView(card);

                    // 添加到activity
                    webView.addView(overlay);

                    // 事件
                    alipayBtn.setOnClickListener(new android.view.View.OnClickListener() {
                        @Override
                        public void onClick(android.view.View v) {
                            doPay("支付宝", overlay, loadingOverlay);
                        }
                    });
                    wxBtn.setOnClickListener(new android.view.View.OnClickListener() {
                        @Override
                        public void onClick(android.view.View v) {
                            doPay("微信支付", overlay, loadingOverlay);
                        }
                    });
                    cancelBtn.setOnClickListener(new android.view.View.OnClickListener() {
                        @Override
                        public void onClick(android.view.View v) {
                            webView.removeView(overlay);
                        }
                    });
                }
            });
        }

        private void doPay(final String channelName, final android.view.View overlay, final android.view.View loadingOverlay) {
            // 先替换为处理中遮罩
            webView.removeView(overlay);
            webView.addView(loadingOverlay);

            // 模拟2秒支付处理
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    webView.removeView(loadingOverlay);
                    payManager.markPaid();
                    // 通知JS
                    webView.evaluateJavascript("javascript:onPaySuccess()", null);
                }
            }, 2000);
        }
    }
}
