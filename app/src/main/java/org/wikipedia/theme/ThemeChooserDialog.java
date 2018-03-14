package org.wikipedia.theme;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.wikipedia.R;
import org.wikipedia.WikipediaApp;
import org.wikipedia.activity.FragmentUtil;
import org.wikipedia.analytics.AppearanceChangeFunnel;
import org.wikipedia.events.WebViewInvalidateEvent;
import org.wikipedia.page.ExtendedBottomSheetDialogFragment;
import org.wikipedia.settings.Prefs;
import org.wikipedia.util.DimenUtil;
import org.wikipedia.util.FeedbackUtil;
import org.wikipedia.util.ResourceUtil;
import org.wikipedia.views.DiscreteSeekBar;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;
import butterknife.Unbinder;

public class ThemeChooserDialog extends ExtendedBottomSheetDialogFragment {
    @BindView(R.id.buttonDecreaseTextSize) TextView buttonDecreaseTextSize;
    @BindView(R.id.buttonIncreaseTextSize) TextView buttonIncreaseTextSize;
    @BindView(R.id.text_size_percent) TextView textSizePercent;
    @BindView(R.id.text_size_seek_bar) DiscreteSeekBar textSizeSeekBar;
    @BindView(R.id.button_theme_light) TextView buttonThemeLight;
    @BindView(R.id.button_theme_dark) TextView buttonThemeDark;
    @BindView(R.id.button_theme_black) TextView buttonThemeBlack;
    @BindView(R.id.button_theme_light_highlight) View buttonThemeLightHighlight;
    @BindView(R.id.button_theme_dark_highlight) View buttonThemeDarkHighlight;
    @BindView(R.id.button_theme_black_highlight) View buttonThemeBlackHighlight;
    @BindView(R.id.theme_chooser_dark_mode_dim_images_switch) SwitchCompat dimImagesSwitch;
    @BindView(R.id.font_change_progress_bar) ProgressBar fontChangeProgressBar;

    public interface Callback {
        void onToggleDimImages();
        void onCancel();
    }

    private enum FontSizeAction { INCREASE, DECREASE, RESET }

    private WikipediaApp app;
    private Unbinder unbinder;
    private AppearanceChangeFunnel funnel;

    private boolean updatingFont = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.dialog_theme_chooser, container);
        unbinder = ButterKnife.bind(this, rootView);
        buttonDecreaseTextSize.setOnClickListener(new FontSizeButtonListener(FontSizeAction.DECREASE));
        buttonIncreaseTextSize.setOnClickListener(new FontSizeButtonListener(FontSizeAction.INCREASE));
        FeedbackUtil.setToolbarButtonLongPressToast(buttonDecreaseTextSize, buttonIncreaseTextSize);
        buttonThemeLight.setOnClickListener(new ThemeButtonListener(Theme.LIGHT));
        buttonThemeDark.setOnClickListener(new ThemeButtonListener(Theme.DARK));
        buttonThemeBlack.setOnClickListener(new ThemeButtonListener(Theme.BLACK));

        textSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                int currentMultiplier = Prefs.getTextSizeMultiplier();
                boolean changed = app.setFontSizeMultiplier(textSizeSeekBar.getValue());
                if (changed) {
                    updatingFont = true;
                    updateFontSize();
                    funnel.logFontSizeChange(currentMultiplier, Prefs.getTextSizeMultiplier());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        updateComponents();
        disableBackgroundDim();
        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = WikipediaApp.getInstance();
        app.getBus().register(this);
        funnel = new AppearanceChangeFunnel(app, app.getWikiSite());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        app.getBus().unregister(this);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        if (callback() != null) {
            // noinspection ConstantConditions
            callback().onCancel();
        }
    }

    @Subscribe public void on(WebViewInvalidateEvent event) {
        updatingFont = false;
        updateComponents();
    }

    @OnCheckedChanged(R.id.theme_chooser_dark_mode_dim_images_switch)
    void onToggleDimImages(boolean enabled) {
        if (enabled == Prefs.shouldDimDarkModeImages()) {
            return;
        }
        Prefs.setDimDarkModeImages(enabled);
        if (callback() != null) {
            // noinspection ConstantConditions
            callback().onToggleDimImages();
        }
    }

    private void updateComponents() {
        updateFontSize();
        updateThemeButtons();
        updateDimImagesSwitch();
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private void updateFontSize() {
        int mult = Prefs.getTextSizeMultiplier();
        textSizeSeekBar.setValue(mult);
        String percentStr = getString(R.string.text_size_percent,
                (int) (100 * (1 + mult * DimenUtil.getFloat(R.dimen.textSizeMultiplierFactor))));
        textSizePercent.setText(mult == 0
                ? getString(R.string.text_size_percent_default, percentStr) : percentStr);
        if (updatingFont) {
            fontChangeProgressBar.setVisibility(View.VISIBLE);
        } else {
            fontChangeProgressBar.setVisibility(View.GONE);
        }
    }

    private void updateThemeButtons() {
        buttonThemeLightHighlight.setVisibility(app.getCurrentTheme() == Theme.LIGHT ? View.VISIBLE : View.GONE);
        buttonThemeLight.setClickable(app.getCurrentTheme() != Theme.LIGHT);
        buttonThemeDarkHighlight.setVisibility(app.getCurrentTheme() == Theme.DARK ? View.VISIBLE : View.GONE);
        buttonThemeDark.setClickable(app.getCurrentTheme() != Theme.DARK);
        buttonThemeBlackHighlight.setVisibility(app.getCurrentTheme() == Theme.BLACK ? View.VISIBLE : View.GONE);
        buttonThemeBlack.setClickable(app.getCurrentTheme() != Theme.BLACK);
    }

    private void updateDimImagesSwitch() {
        dimImagesSwitch.setChecked(Prefs.shouldDimDarkModeImages());
        dimImagesSwitch.setEnabled(app.getCurrentTheme().isDark());
        dimImagesSwitch.setTextColor(dimImagesSwitch.isEnabled()
                ? ResourceUtil.getThemedColor(getContext(), R.attr.section_title_color)
                : ContextCompat.getColor(getContext(), R.color.black26));
    }

    private final class ThemeButtonListener implements View.OnClickListener {
        private Theme theme;

        private ThemeButtonListener(Theme theme) {
            this.theme = theme;
        }

        @Override
        public void onClick(View v) {
            if (app.getCurrentTheme() != theme) {
                app.setCurrentTheme(theme);
                funnel.logThemeChange(app.getCurrentTheme(), theme);
            }
        }
    }

    private final class FontSizeButtonListener implements View.OnClickListener {
        private FontSizeAction action;

        private FontSizeButtonListener(FontSizeAction action) {
            this.action = action;
        }

        @Override
        public void onClick(View view) {
            boolean changed = false;
            int currentMultiplier = Prefs.getTextSizeMultiplier();
            if (action == FontSizeAction.INCREASE) {
                changed = app.setFontSizeMultiplier(Prefs.getTextSizeMultiplier() + 1);
            } else if (action == FontSizeAction.DECREASE) {
                changed = app.setFontSizeMultiplier(Prefs.getTextSizeMultiplier() - 1);
            } else if (action == FontSizeAction.RESET) {
                changed = app.setFontSizeMultiplier(0);
            }
            if (changed) {
                updatingFont = true;
                updateFontSize();
                funnel.logFontSizeChange(currentMultiplier, Prefs.getTextSizeMultiplier());
            }
        }
    }

    @Nullable
    public Callback callback() {
        return FragmentUtil.getCallback(this, Callback.class);
    }
}
