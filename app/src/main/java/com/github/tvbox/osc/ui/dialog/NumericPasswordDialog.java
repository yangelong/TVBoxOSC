package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * 电视遥控器可直接操作的数字密码框：数字键盘 + 清空 + 确定。
 */
public class NumericPasswordDialog extends BaseDialog {
    private static final int MAX_LENGTH = 6;

    private final StringBuilder password = new StringBuilder();
    private TextView passwordView;
    private int normalTextColor = 0xFFFFFFFF;

    public NumericPasswordDialog(@NonNull @NotNull Context context, String title) {
        super(context);
        setOwnerActivity((Activity) context);
        setContentView(R.layout.dialog_numeric_password);
        TextView titleView = findViewById(R.id.pwdTitle);
        if (title != null && !title.isEmpty()) {
            titleView.setText(title);
        }
        passwordView = findViewById(R.id.pwdValue);
        if (passwordView != null) {
            normalTextColor = passwordView.getCurrentTextColor();
        }
        int[] digitIds = {R.id.key1, R.id.key2, R.id.key3, R.id.key4, R.id.key5,
                R.id.key6, R.id.key7, R.id.key8, R.id.key9};
        for (int i = 0; i < digitIds.length; i++) {
            final String digit = String.valueOf(i + 1);
            findViewById(digitIds[i]).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    appendDigit(digit);
                }
            });
        }
        findViewById(R.id.key0).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendDigit("0");
            }
        });
        findViewById(R.id.keyClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                password.setLength(0);
                showPassword();
            }
        });
        findViewById(R.id.keyOk).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        findViewById(R.id.key1).requestFocus();
    }

    private void appendDigit(String digit) {
        if (password.length() >= MAX_LENGTH) {
            return;
        }
        password.append(digit);
        showPassword();
    }

    /** 隐私显示：已输入的位数用星号代替，不显示真实数字 */
    private void showPassword() {
        if (passwordView == null) {
            return;
        }
        passwordView.setTextColor(normalTextColor);
        if (password.length() == 0) {
            passwordView.setText("");
            return;
        }
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < password.length(); i++) {
            if (i > 0) {
                masked.append(' ');
            }
            masked.append('*');
        }
        passwordView.setText(masked.toString());
    }

    /** 密码错误时：不关闭对话框，清空已输入内容并红字提示，让用户直接重输 */
    public void showError(String message) {
        password.setLength(0);
        if (passwordView == null) {
            return;
        }
        passwordView.setText(message == null ? "密码错误，请重新输入" : message);
        passwordView.setTextColor(0xFFFF5F00);
    }

    private void submit() {
        if (listener == null) {
            dismiss();
            return;
        }
        // 返回 false 表示密码不对：保持对话框打开，方便直接在键盘上重输
        if (!listener.onSubmit(password.toString())) {
            showError("密码错误，请重新输入");
            return;
        }
        dismiss();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (listener != null) {
            listener.onCancel();
        }
        dismiss();
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    private OnListener listener = null;

    public interface OnListener {
        /** @return true 表示密码正确（对话框关闭）；false 表示密码错误（对话框保持打开并清空输入） */
        boolean onSubmit(String password);

        void onCancel();
    }
}
