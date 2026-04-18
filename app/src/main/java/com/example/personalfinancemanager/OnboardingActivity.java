package com.example.personalfinancemanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

/**
 * One-time first-run welcome carousel. Three slides:
 * <ol>
 *   <li>What WealthFlow does (dashboard summary).</li>
 *   <li>How SMS auto-tracking works (and that it's on-device only).</li>
 *   <li>Privacy / security posture (biometric lock, encrypted credentials).</li>
 * </ol>
 *
 * <p>Flow: LockActivity → (if not yet onboarded) OnboardingActivity → LockActivity
 * again (which now proceeds to lock prompt / MainActivity). The onboarding
 * flag is persisted via {@link CredentialManager#markOnboardingCompleted()} so
 * it only ever shows once per install.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final Slide[] SLIDES = new Slide[]{
            new Slide("\uD83D\uDCCA",
                    R.string.onboarding_slide1_title,
                    R.string.onboarding_slide1_body),
            new Slide("\uD83D\uDCE9",
                    R.string.onboarding_slide2_title,
                    R.string.onboarding_slide2_body),
            new Slide("\uD83D\uDD12",
                    R.string.onboarding_slide3_title,
                    R.string.onboarding_slide3_body),
    };

    private ViewPager2 pager;
    private TextView btnNext;
    private View[] dots;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        pager = findViewById(R.id.pager);
        btnNext = findViewById(R.id.btnNext);
        TextView btnSkip = findViewById(R.id.btnSkip);
        dots = new View[]{
                findViewById(R.id.dot0),
                findViewById(R.id.dot1),
                findViewById(R.id.dot2)
        };

        pager.setAdapter(new SlideAdapter());
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                syncUiForPage(position);
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());
        btnNext.setOnClickListener(v -> {
            int pos = pager.getCurrentItem();
            if (pos < SLIDES.length - 1) {
                pager.setCurrentItem(pos + 1, true);
            } else {
                finishOnboarding();
            }
        });

        syncUiForPage(0);
    }

    private void syncUiForPage(int position) {
        for (int i = 0; i < dots.length; i++) {
            boolean active = (i == position);
            dots[i].setBackgroundResource(active
                    ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
            int size = active ? dpToPx(8) : dpToPx(6);
            dots[i].getLayoutParams().width = size;
            dots[i].getLayoutParams().height = size;
            dots[i].requestLayout();
        }
        boolean isLast = (position == SLIDES.length - 1);
        btnNext.setText(isLast ? R.string.action_get_started : R.string.action_next);
    }

    private void finishOnboarding() {
        new CredentialManager(this).markOnboardingCompleted();
        // Route through LockActivity so the standard launch flow (biometric
        // prompt if enabled, else pass-through to MainActivity) runs exactly
        // as it would on every subsequent launch.
        Intent i = new Intent(this, LockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---------- Data + Adapter ----------

    private static final class Slide {
        final String icon;
        final int titleRes;
        final int bodyRes;
        Slide(String icon, int titleRes, int bodyRes) {
            this.icon = icon;
            this.titleRes = titleRes;
            this.bodyRes = bodyRes;
        }
    }

    private static final class SlideAdapter extends RecyclerView.Adapter<SlideAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_slide, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Slide s = SLIDES[position];
            h.icon.setText(s.icon);
            h.title.setText(s.titleRes);
            h.body.setText(s.bodyRes);
        }

        @Override
        public int getItemCount() { return SLIDES.length; }

        static class VH extends RecyclerView.ViewHolder {
            TextView icon, title, body;
            VH(@NonNull View v) {
                super(v);
                icon  = v.findViewById(R.id.slideIcon);
                title = v.findViewById(R.id.slideTitle);
                body  = v.findViewById(R.id.slideBody);
            }
        }
    }
}
