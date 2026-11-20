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

    public NumericPasswordDialog(@NonNull @NotNull Context context, String title) {
        super(context);
        setOwnerActivity((Activity) context);
        setContentView(R.layout.dialog_numeric_password);
        TextView titleView = findViewById(R.id.pwdTitle);
        if (title != null && !title.isEmpty()) {
            titleView.setText(title);
        }
        passwordView = findViewById(R.id.pwdValue);
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

    private void showPassword() {
        passwordView.setText(password.toString());
    }

    private void submit() {
        if (listener != null) {
            listener.onSubmit(password.toString());
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
        void onSubmit(String password);

        void onCancel();
    }
}
