package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.SQLException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.activity.DialogActivity;
import com.RobinNotBad.BiliClient.activity.ShowTextActivity;
import com.RobinNotBad.BiliClient.event.SnackEvent;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.snackbar.SnackbarContentLayout;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class MsgUtil {
    private static Toast toast;

    /**
     * 当前对话框关闭后要执行的动作（只保留一个）。
     *
     * 用途：把「免责声明 → 夜深了」这类连续启动通知串起来。两者各自是一个独立的
     * {@code DialogActivity}，若同时 startActivity 会叠在一起——后弹的压住先弹的，
     * 用户得连关两次，且先弹的那个根本看不见。
     */
    private static Runnable dialogClosedAction = null;

    /**
     * 登记一个「当前对话框关掉之后再执行」的动作；后登记的覆盖先登记的。
     * 由 {@link #onDialogActivityClosed()} 触发。
     */
    public static synchronized void runAfterDialogClosed(Runnable action) {
        dialogClosedAction = action;
    }

    /**
     * 由 {@code DialogActivity} 在关闭时回调，执行 {@link #runAfterDialogClosed} 登记的后续弹窗。
     * 幂等：重复调用只会触发一次。
     */
    public static void onDialogActivityClosed() {
        Runnable action;
        synchronized (MsgUtil.class) {
            action = dialogClosedAction;
            dialogClosedAction = null;
        }
        // 在锁外执行，避免后续 startActivity 再回调进来造成死锁
        if (action != null) action.run();
    }

    public static void showMsg(String str) {
        Logu.i(str);
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE, true)) {
            CenterThreadPool.runOnUiThread(() -> EventBus.getDefault().postSticky(new SnackEvent(str)));
        } else {
            toast(str);
        }
    }

    public static void showMsgLong(String str) {
        Logu.i("long", str);
        if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE, true)) {
            CenterThreadPool.runOnUiThread(() -> EventBus.getDefault().postSticky(new SnackEvent(str, Snackbar.LENGTH_LONG)));
        } else {
            toastLong(str);
        }
    }

    public static void toast(String str) {
        CenterThreadPool.runOnUiThread(() -> toastInternal(str, BiliTerminal.context));
    }

    public static void toastLong(String str) {
        CenterThreadPool.runOnUiThread(() -> toastLongInternal(str, BiliTerminal.context));
    }

    private static void toastInternal(String str, Context context) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(context, str, Toast.LENGTH_SHORT);
        toast.show();
    }

    private static void toastLongInternal(String str, Context context) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(context, str, Toast.LENGTH_LONG);
        toast.show();
    }

    public static void processSnackEvent(SnackEvent snackEvent, View view) {
        if (view == null || view.getContext() == null) {
            return;
        }
        try {
            long currentTime = System.currentTimeMillis();

            int duration;
            if (snackEvent.getDuration() > 0) duration = snackEvent.getDuration();
            else if (snackEvent.getDuration() == Snackbar.LENGTH_SHORT) duration = 1950;
            else if (snackEvent.getDuration() == Snackbar.LENGTH_INDEFINITE)
                duration = Integer.MAX_VALUE;
            else duration = 2750;

            long endTime = snackEvent.getStartTime() + duration;
            if (currentTime >= endTime) {
                EventBus.getDefault().removeStickyEvent(snackEvent);
            } else {
                // 显示后立即移除 sticky 事件：否则同一次 onStart 注册收到的这条事件，
                // 还会被 BaseActivity.onResume 再取一次，导致同一个页面弹两次。
                EventBus.getDefault().removeStickyEvent(snackEvent);
                createSnack(view, snackEvent.getMessage(), (int) (endTime - currentTime))
                        .show();
            }
        } catch (Exception e) {
            Log.e("MsgUtil", "Error processing snack event", e);
        }
    }

    public static void snackText(View view, CharSequence text) {
        if (view != null) {
            try {
                createSnack(view, text).show();
            } catch (Exception e) {
                Log.e("MsgUtil", "Error showing snack text", e);
            }
        }
    }

    public static void snackTextLong(View view, CharSequence text) {
        if (view != null) {
            try {
                createSnack(view, text, Snackbar.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e("MsgUtil", "Error showing snack text long", e);
            }
        }
    }

    public static Snackbar createSnack(View view, CharSequence text) {
        return createSnack(view, text, Snackbar.LENGTH_SHORT);
    }

    public static Snackbar createSnack(View view, CharSequence text, int duration) {
        return createSnack(view, text, duration, null);
    }

    @SuppressLint({"ClickableViewAccessibility", "RestrictedApi"})
    public static Snackbar createSnack(View view, CharSequence text, int duration, Action action) {
        Snackbar snackbar;
        snackbar = Snackbar.make(view, text, duration);
        snackbar.setBackgroundTint(Color.argb(0x85, 0x80, 0x80, 0x80));
        snackbar.setTextColor(ColorScheme.INSTANCE.getTEXT_PRIMARY()); // 原写死 #ebe0e2（经典终端的暖白）
        View snackBarView = snackbar.getView();
        snackBarView.setOnTouchListener((v, event) -> false);
        try {
            snackBarView.setPadding(ToolsUtil.dp2px(6), 0, 0, 0);
        } catch (Exception e) {
            Log.e("MsgUtil", "Error setting snackbar padding", e);
        }
        SnackbarContentLayout contentLayout = ((SnackbarContentLayout) ((FrameLayout) snackBarView).getChildAt(0));

        if (action != null) snackbar.setAction(action.getText(), action.getOnClickListener());
        else if (duration == Snackbar.LENGTH_INDEFINITE || duration >= 5000) {
            snackbar.setAction("x", (view1 -> snackbar.dismiss()));
            Button actionView = contentLayout.getActionView();
            //actionView.setTextSize(ToolsUtil.sp2px(13));
            try {
                actionView.setMinWidth(ToolsUtil.dp2px(30));
                actionView.setMinimumWidth(ToolsUtil.dp2px(30));
                actionView.setMaxWidth(ToolsUtil.dp2px(48));
                actionView.setPadding(0, 0, ToolsUtil.dp2px(4), 0);
            } catch (Exception e) {
                Log.e("MsgUtil", "Error setting snackbar action view", e);
            }
        }

        TextView msgView = contentLayout.getMessageView();
        msgView.setMaxLines(16);
        msgView.setTextSize(13);
        msgView.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        try {
            msgView.setPadding(0, 0, ToolsUtil.dp2px(4), 0);
        } catch (Exception e) {
            Log.e("MsgUtil", "Error setting snackbar message view padding", e);
        }

        return snackbar;
    }

    public static void err(Throwable e) {
        err(null, e);
    }

    public static void err(String desc, Throwable e) {
        if (desc != null) Log.e("debug-error", desc);
        e.printStackTrace();

        StringBuilder output = new StringBuilder(TextUtils.isEmpty(desc) ? "" : desc + "\n");

        String e_str = e.toString();
        Context context = BiliTerminal.context;

        if (context != null) {
            if (e instanceof IOException) {
                // 证书「尚未生效 / 已过期」几乎都是设备时间错了，单说「网络错误」用户无从下手
                if (ClockSanity.isCertTimeError(e)) {
                    reportCertTimeError(context);
                    return;
                }
                showMsg(context.getString(R.string.err_network));
                return;
            } else if (e instanceof JSONException) {
                output.append(context.getString(R.string.err_json));

                if (SharedPreferencesUtil.getBoolean("dev_jsonerr_detailed", false)) {
                    Writer writer = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(writer);
                    e.printStackTrace(printWriter);
                    showText(output.toString(), writer.toString());
                    return;
                } else if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0) {
                    output.append(context.getString(R.string.err_login_first));
                } else if (e_str.contains("-352") || e_str.contains("22015") || e_str.contains("65056"))
                    output.append(context.getString(R.string.err_rejected));
                else {
                    output.append(e_str.replace("org.json.JSONException:", ""));
                }
            } else if (e instanceof IndexOutOfBoundsException) {
                // 不是所有 IndexOutOfBoundsException 都是 RecyclerView/Adapter 的锅。
                // #84：私信接口在空 uidList 上抛的是 StringIndexOutOfBoundsException（substring(0,-1)），
                // 旧实现一律显示「遇到Adapter错误…无需上报」，既误导用户，也把真实问题藏了。
                // 现在只有异常信息里真带 RecyclerView / Adapter 特征时才按列表报错提示，
                // 其余按普通错误显示原文（用户反馈时能直接抄给开发者，也允许上报）。
                boolean adapterError = AdapterErrorClassifier.looksLikeAdapterError(e_str);
                if (adapterError && SharedPreferencesUtil.getBoolean("dev_recyclererr_detailed", false)) {
                    Writer writer = new StringWriter();
                    PrintWriter printWriter = new PrintWriter(writer);
                    e.printStackTrace(printWriter);
                    showText(desc + context.getString(R.string.err_adapter_title), writer.toString());
                    return;
                } else if (adapterError) {
                    output.append(context.getString(R.string.err_adapter));
                } else {
                    output.append(context.getString(R.string.err_generic));
                    output.append(e_str);
                }
            } else if (e instanceof SQLException) output.append(context.getString(R.string.err_sql));
            else {
                output.append(context.getString(R.string.err_generic));
                output.append(e_str);
            }
        } else {
            output.append("错误：");
            output.append(e_str);
        }

        showMsgLong(output.toString());
    }

    /** 上次去问服务器时间的时刻（elapsedRealtime，不受系统时间影响）。 */
    private static final long SERVER_TIME_CHECK_INTERVAL_MS = 60_000L;
    private static long lastServerTimeCheck = -SERVER_TIME_CHECK_INTERVAL_MS;

    /**
     * 证书时间错误的提示：先立即给通用提示，再去后台走明文 http 问一次服务器时间
     * （{@link com.RobinNotBad.BiliClient.api.ServerTimeApi}），差得离谱就补一条带具体日期的提示。
     *
     * <p>一个页面常常并发好几个请求、同时失败，60 秒内只问一次，免得刷屏和白跑流量。
     * 服务器时间只用于显示，不参与任何校验。
     */
    private static void reportCertTimeError(Context context) {
        showMsgLong(context.getString(R.string.err_cert_time));
        synchronized (MsgUtil.class) {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastServerTimeCheck < SERVER_TIME_CHECK_INTERVAL_MS) return;
            lastServerTimeCheck = now;
        }
        CenterThreadPool.run(() -> {
            Long server = com.RobinNotBad.BiliClient.api.ServerTimeApi.fetchServerMillis();
            long device = System.currentTimeMillis();
            if (server == null || !ClockSanity.isSkewed(device, server)) return;
            java.text.DateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
            String msg = context.getString(R.string.err_cert_time_skew,
                    fmt.format(new java.util.Date(device)), fmt.format(new java.util.Date(server)));
            // 四行带两个日期，LENGTH_LONG（2.75 秒）在手表上读不完；8 秒并自带「x」关闭（见 createSnack）
            if (SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.SNACKBAR_ENABLE, true)) {
                Logu.i("long", msg);
                CenterThreadPool.runOnUiThread(() -> EventBus.getDefault().postSticky(new SnackEvent(msg, 8000)));
            } else {
                toastLong(msg);
            }
        });
    }

    public static void showText(String title, String text) {
        showText(title, text, false);
    }

    /**
     * @param relayed true 表示这是从上游（原版哔哩终端）审核透传的公告，
     *                正文页顶部会多一行来源说明
     */
    public static void showText(String title, String text, boolean relayed) {
        Context context = BiliTerminal.context;
        if (context != null) {
            Intent testIntent = new Intent()
                    .setClass(context, ShowTextActivity.class)
                    .putExtra("title", title)
                    .putExtra("content", text)
                    .putExtra("relay_source", relayed)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(testIntent);
        }
    }

    public static void showDialog(String title, String content) {
        Context context = BiliTerminal.context;
        if (context != null) {
            Intent intent = new Intent(context, DialogActivity.class);
            intent.putExtra("title", title);
            intent.putExtra("content", content);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void showDialog(String title, String content, int wait_time) {
        Context context = BiliTerminal.context;
        if (context != null) {
            Intent intent = new Intent(context, DialogActivity.class);
            intent.putExtra("title", title);
            intent.putExtra("content", content);
            intent.putExtra("wait_time", wait_time);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static class Action {
        private String text;
        private View.OnClickListener onClickListener;

        public Action(String text, View.OnClickListener onClickListener) {
            this.text = text;
            this.onClickListener = onClickListener;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public View.OnClickListener getOnClickListener() {
            return onClickListener;
        }

        public void setOnClickListener(View.OnClickListener onClickListener) {
            this.onClickListener = onClickListener;
        }
    }

}
