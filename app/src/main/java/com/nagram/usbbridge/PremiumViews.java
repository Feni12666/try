package com.nagram.usbbridge;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

final class PremiumViews {
    private PremiumViews() {}

    static final class ArcProgressView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mainText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint smallText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float shown = 0f;
        private int target = 0;
        private ValueAnimator animator;

        ArcProgressView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeCap(Paint.Cap.ROUND);
            track.setColor(Color.rgb(27, 40, 59));
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            mainText.setColor(Color.WHITE);
            mainText.setTextAlign(Paint.Align.CENTER);
            mainText.setFakeBoldText(true);
            smallText.setColor(Color.rgb(210, 220, 235));
            smallText.setTextAlign(Paint.Align.CENTER);
        }

        void setProgressAnimated(int value) {
            value = Math.max(0, Math.min(100, value));
            if (target == value && animator != null && animator.isRunning()) return;
            target = value;
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(shown, value);
            animator.setDuration(520L);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener(a -> {
                shown = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float stroke = Math.max(12f, Math.min(w, h) * 0.085f);
            track.setStrokeWidth(stroke);
            arc.setStrokeWidth(stroke);
            float pad = stroke * 1.25f;
            RectF r = new RectF(pad, pad, w - pad, h - pad);
            canvas.drawArc(r, -90f, 360f, false, track);
            SweepGradient gradient = new SweepGradient(w / 2f, h / 2f,
                    new int[]{Color.rgb(21, 87, 232), Color.rgb(37, 186, 255), Color.rgb(21, 87, 232)},
                    new float[]{0f, .55f, 1f});
            arc.setShader(gradient);
            arc.setShadowLayer(stroke * .7f, 0, 0, Color.argb(130, 25, 133, 255));
            canvas.drawArc(r, -90f, 360f * shown / 100f, false, arc);
            arc.clearShadowLayer();
            arc.setShader(null);

            mainText.setTextSize(Math.min(w, h) * .30f);
            smallText.setTextSize(Math.min(w, h) * .13f);
            float base = h / 2f - (mainText.ascent() + mainText.descent()) / 2f - h * .03f;
            canvas.drawText(String.valueOf(Math.round(shown)), w * .46f, base, mainText);
            canvas.drawText("%", w * .70f, base + h * .03f, smallText);
        }
    }

    static final class ShieldPulseView extends View {
        static final int GREEN = 0;
        static final int BLUE = 1;
        static final int AMBER = 2;
        static final int RED = 3;

        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shield = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint check = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float phase = 0f;
        private int state = GREEN;
        private final ValueAnimator pulse;

        ShieldPulseView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            ring.setStyle(Paint.Style.STROKE);
            shield.setStyle(Paint.Style.STROKE);
            shield.setStrokeJoin(Paint.Join.ROUND);
            shield.setStrokeCap(Paint.Cap.ROUND);
            check.setStyle(Paint.Style.STROKE);
            check.setStrokeJoin(Paint.Join.ROUND);
            check.setStrokeCap(Paint.Cap.ROUND);
            pulse = ValueAnimator.ofFloat(0f, 1f);
            pulse.setDuration(1800L);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
            pulse.start();
        }

        void setState(int state) {
            this.state = state;
            invalidate();
        }

        private int color() {
            switch (state) {
                case BLUE: return Color.rgb(47, 140, 255);
                case AMBER: return Color.rgb(255, 177, 27);
                case RED: return Color.rgb(255, 78, 96);
                default: return Color.rgb(88, 235, 75);
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int c = color();
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
            float min = Math.min(w, h);
            ring.setStrokeWidth(min * .012f);
            ring.setColor(Color.argb((int)(60 + phase * 90), Color.red(c), Color.green(c), Color.blue(c)));
            ring.setShadowLayer(min * .06f, 0, 0, c);
            canvas.drawCircle(cx, cy, min * (.38f + phase * .025f), ring);
            ring.clearShadowLayer();
            ring.setColor(Color.argb(85, Color.red(c), Color.green(c), Color.blue(c)));
            canvas.drawCircle(cx, cy, min * .31f, ring);

            shield.setStrokeWidth(min * .035f);
            shield.setColor(c);
            shield.setShadowLayer(min * .055f, 0, 0, c);
            Path p = new Path();
            p.moveTo(cx, cy - min * .20f);
            p.lineTo(cx + min * .18f, cy - min * .12f);
            p.lineTo(cx + min * .15f, cy + min * .10f);
            p.quadTo(cx + min * .08f, cy + min * .23f, cx, cy + min * .28f);
            p.quadTo(cx - min * .08f, cy + min * .23f, cx - min * .15f, cy + min * .10f);
            p.lineTo(cx - min * .18f, cy - min * .12f);
            p.close();
            canvas.drawPath(p, shield);
            shield.clearShadowLayer();

            check.setStrokeWidth(min * .035f);
            check.setColor(c);
            Path q = new Path();
            q.moveTo(cx - min * .08f, cy + min * .02f);
            q.lineTo(cx - min * .015f, cy + min * .09f);
            q.lineTo(cx + min * .11f, cy - min * .07f);
            canvas.drawPath(q, check);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            pulse.cancel();
        }
    }
}
