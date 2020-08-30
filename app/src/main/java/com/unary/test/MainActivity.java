package com.unary.test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

import com.unary.circularseekbar.CircularSeekBar;

public class MainActivity extends AppCompatActivity {

    private CircularSeekBar mSeekBar;
    private TextView mTextView;
    private int mTextColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSeekBar = findViewById(R.id.seek_bar);
        mTextView = findViewById(R.id.text_view);

        mTextColor = mTextView.getCurrentTextColor();

        // Change the drawable colors
        //mSeekBar.getThumbDrawable().setTintList(mSeekBar.getProgressColor());
        //((RippleDrawable) mSeekBar.getBackground()).setColor(mSeekBar.getProgressColor());

        // Change the thumb drawable
        //mSeekBar.setThumbDrawableResource(R.drawable.ic_android_black_24dp);

        // Remove the thumb and ripple
        //mSeekBar.setThumbDrawable(null);
        //mSeekBar.setBackground(null);

        mSeekBar.setOnProgressChangeListener(new CircularSeekBar.OnProgressChangeListener() {
            @Override
            public boolean onProgressChanging(@NonNull CircularSeekBar seekBar, int progress) {
                return true;
            }

            @Override
            public void onProgressChanged(@NonNull CircularSeekBar seekBar, int progress, boolean finished) {
                int[] state = {android.R.attr.state_pressed};
                int statefulColor = mSeekBar.getProgressColor().getColorForState(state, mSeekBar.getProgressColor().getDefaultColor());

                mTextView.setTextColor(!mSeekBar.isPressed() ? mTextColor : statefulColor);
                mTextView.setText(String.valueOf(progress));
            }
        });
    }
}