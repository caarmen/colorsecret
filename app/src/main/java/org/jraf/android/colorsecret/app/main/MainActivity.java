/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2011 Benoit 'BoD' Lubek (BoD@JRAF.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jraf.android.colorsecret.app.main;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AbsoluteLayout;
import android.widget.AbsoluteLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.jraf.android.colorsecret.BuildConfig;
import org.jraf.android.colorsecret.Constants;
import org.jraf.android.colorsecret.R;
import org.jraf.android.colorsecret.model.CodePeg;
import org.jraf.android.colorsecret.model.Game;
import org.jraf.android.colorsecret.model.Game.GuessResult;
import org.jraf.android.colorsecret.model.HintPeg;
import org.jraf.android.colorsecret.util.IoUtil;
import org.jraf.android.colorsecret.util.PegUtil;
import org.jraf.android.colorsecret.util.SoundUtil;
import org.jraf.android.colorsecret.util.StringUtil;
import org.jraf.android.colorsecret.util.UiUtil;
import org.jraf.android.util.about.AboutActivityIntentBuilder;

public class MainActivity extends AppCompatActivity {
    private static final int DIALOG_PICK_PEG = 0;
    private static final int DIALOG_GAME_OVER = 1;
    private static final int DIALOG_YOU_WON = 2;
    private static final int DIALOG_CONFIRM_EXIT = 4;
    private static final int DIALOG_HELP = 5;

    private int mNbHoles;
    private int mNbRows;

    private Game mGame;

    private ViewGroup mRootView;
    private int[] mRootXy = new int[2];
    private ViewGroup mBoardView;
    private LayoutInflater mLayoutInflater;

    protected int mCurrentRowIndex;
    protected int mSelectedPegHoleIndex;
    protected int mPrevSelectedPegHoleIndex;
    protected View mSelectedPegView;

    protected CodePeg mDraggingPeg;
    private boolean mDragging;
    private View mDraggingPegView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean soundEnabled = sharedPreferences.getBoolean(Constants.PREF_SOUND_ENABLED, true);
        setVolumeControlStream(soundEnabled ? AudioManager.STREAM_MUSIC : AudioManager.USE_DEFAULT_STREAM_TYPE);
        SoundUtil.setEnabled(soundEnabled);

        boolean firstUse = sharedPreferences.getBoolean(Constants.PREF_FIRST_USE, true);
        if (firstUse) {
            showDialog(DIALOG_HELP);
            sharedPreferences.edit().putBoolean(Constants.PREF_FIRST_USE, false).commit();
        }

        mLayoutInflater = LayoutInflater.from(this);
        newGame();
    }


    /*
     * New game.
     */

    private DialogInterface.OnClickListener mNewGameOnClickListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            removeDialog(DIALOG_GAME_OVER);
            removeDialog(DIALOG_YOU_WON);
            newGame();
        }
    };


    private void newGame() {
        SoundUtil.play(this, R.raw.newgame0);

        mNbHoles = Constants.DEFAULT_NB_HOLES;
        mNbRows = Constants.DEFAULT_NB_ROWS;

        mGame = new Game(mNbHoles, mNbRows);
        mGame.setRandomSecret();

        mRootView = (ViewGroup) findViewById(R.id.root);

        mDraggingPegView = mRootView.findViewById(R.id.draggingPeg);

        createPegPicker();
        mBoardView = (ViewGroup) findViewById(R.id.board);
        mBoardView.removeAllViews();
        createRows();
        mCurrentRowIndex = 0;
        setRowActive(mCurrentRowIndex);

        ((ScrollView) mRootView.findViewById(R.id.scrollView)).smoothScrollTo(0, 0);

        refreshScore();
    }


    /*
     * Layout.
     */

    private void createRows() {
        for (int i = 0; i < mNbRows; i++) {
            View row = createRow(i);
            mBoardView.addView(row);
        }
    }

    private View createRow(int rowIndex) {
        LinearLayout res = (LinearLayout) mLayoutInflater.inflate(R.layout.row, null, false);

        LinearLayout containerCodePegs = (LinearLayout) res.findViewById(R.id.container_codePegs);
        createCodePegs(containerCodePegs);

        LinearLayout containerHintPegs = (LinearLayout) res.findViewById(R.id.container_hintPegs);
        createHintPegs(containerHintPegs);

        res.findViewById(R.id.button_ok).setOnClickListener(mOkOnClickListener);

        return res;
    }

    private void createCodePegs(LinearLayout containerCodePegs) {
        for (int i = 0; i < mNbHoles; i++) {
            View peg = mLayoutInflater.inflate(R.layout.peg, containerCodePegs, false);
            containerCodePegs.addView(peg);
        }
    }

    private void createHintPegs(LinearLayout containerHintPegs) {
        LinearLayout containerHintPegs1 = (LinearLayout) containerHintPegs.findViewById(R.id.container_hintPegs1);
        LinearLayout containerHintPegs2 = (LinearLayout) containerHintPegs.findViewById(R.id.container_hintPegs2);
        LinearLayout container;
        for (int i = 0; i < mNbHoles; i++) {
            if (i < mNbHoles / 2) {
                container = containerHintPegs1;
            } else {
                container = containerHintPegs2;
            }
            View peg = mLayoutInflater.inflate(R.layout.peg_hint, container, false);
            ((ImageView) peg.findViewById(R.id.peg)).setImageResource(R.drawable.peg_hint_empty);
            container.addView(peg);
        }
    }

    private void createPegPicker() {
        ViewGroup pegPicker = (ViewGroup) mRootView.findViewById(R.id.pegPicker);
        pegPicker.removeAllViews();
        for (final CodePeg codePeg : CodePeg.values()) {
            View pegView = mLayoutInflater.inflate(R.layout.peg, pegPicker, false);
            LinearLayout.LayoutParams pegLayoutParams = (android.widget.LinearLayout.LayoutParams) pegView.getLayoutParams();
            pegLayoutParams.weight = 1;
            pegView.setLayoutParams(pegLayoutParams);

            ImageView pegImageView = (ImageView) pegView.findViewById(R.id.peg);
            pegImageView.setImageResource(PegUtil.getDrawable(codePeg));

            LinearLayout.LayoutParams pegImageLayoutParams = (android.widget.LinearLayout.LayoutParams) pegImageView
                    .getLayoutParams();
            pegImageLayoutParams.leftMargin = 1;
            pegImageLayoutParams.topMargin = 1;
            pegImageLayoutParams.bottomMargin = 1;
            pegImageLayoutParams.rightMargin = 1;
            pegImageView.setLayoutParams(pegImageLayoutParams);

            pegPicker.addView(pegView);

            pegView.setOnTouchListener(new OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    v.setBackgroundResource(R.drawable.peg_code_bg_dragging);
                    mDraggingPeg = codePeg;
                    handleDragEvent(event);
                    return true;
                }
            });
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean pickerShown = sharedPreferences.getBoolean(Constants.PREF_PICKER_SHOWN, true);
        pegPicker.setVisibility(pickerShown ? View.VISIBLE : View.GONE);
    }

    private void refreshScore() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int totalGames = sharedPreferences.getInt(Constants.PREF_TOTAL_GAMES, 0);
        int totalWon = sharedPreferences.getInt(Constants.PREF_TOTAL_WON, 0);
        int totalScore = sharedPreferences.getInt(Constants.PREF_TOTAL_SCORE, 0);
        ((TextView) mRootView.findViewById(R.id.totalGames)).setText(getString(R.string.score_totalGames, totalGames));
        ((TextView) mRootView.findViewById(R.id.totalWon)).setText(getString(R.string.score_totalWon, totalWon));
        ((TextView) mRootView.findViewById(R.id.totalScore)).setText(getString(R.string.score_totalScore, totalScore));
    }


    /*
     * Drag and drop.
     */

    protected void handleDragEvent(MotionEvent event) {
        int eventX = (int) event.getRawX();
        int eventY = (int) event.getRawY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                setRowReceivingDrag(mCurrentRowIndex, true);
                mDragging = true;
                mDraggingPegView.setVisibility(View.VISIBLE);
                ImageView pegImageView = (ImageView) mDraggingPegView.findViewById(R.id.peg);
                pegImageView.setImageResource(PegUtil.getDrawable(mDraggingPeg));
                pegImageView.getDrawable().setAlpha(127);
                mRootView.getLocationOnScreen(mRootXy);
                SoundUtil.play(this, R.raw.pick0);
                break;

            case MotionEvent.ACTION_UP:
                setRowReceivingDrag(mCurrentRowIndex, false);
                mDragging = false;
                if (mSelectedPegHoleIndex != -1) {
                    ((ImageView) mSelectedPegView.findViewById(R.id.peg)).setImageResource(PegUtil.getDrawable(mDraggingPeg));
                    mGame.setGuess(mCurrentRowIndex, mSelectedPegHoleIndex, mDraggingPeg);
                    updateOkButton();
                }
                mDraggingPegView.setVisibility(View.GONE);
                moveDraggingPegView(-mDraggingPegView.getWidth(), -mDraggingPegView.getHeight());
                if (mSelectedPegHoleIndex != -1) {
                    SoundUtil.play(this, R.raw.drop0);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (mDragging) {
                    int newX = eventX - mRootXy[0] - mDraggingPegView.getWidth() / 2;
                    int newY = eventY - mRootXy[1] - mDraggingPegView.getHeight() / 2;
                    moveDraggingPegView(newX, newY);

                    ViewGroup row = (ViewGroup) mBoardView.getChildAt(mCurrentRowIndex);
                    LinearLayout containerCodePegs = (LinearLayout) row.findViewById(R.id.container_codePegs);
                    int childCount = containerCodePegs.getChildCount();
                    int[] pegXy = new int[2];
                    mSelectedPegHoleIndex = -1;
                    for (int i = 0; i < childCount; i++) {
                        View pegView = containerCodePegs.getChildAt(i);
                        pegView.getLocationOnScreen(pegXy);
                        int pegX = pegXy[0];
                        int pegY = pegXy[1];
                        int pegWidth = pegView.getWidth();
                        int pegHeight = pegView.getHeight();
                        if (pegX < eventX && eventX < pegX + pegWidth && pegY < eventY && eventY < pegY + pegHeight) {
                            pegView.setBackgroundResource(R.drawable.peg_code_bg_dragging);
                            mSelectedPegHoleIndex = i;
                            mSelectedPegView = pegView;
                        } else {
                            pegView.setBackgroundResource(0);
                        }
                    }
                    if (mSelectedPegHoleIndex != -1) {
                        if (mPrevSelectedPegHoleIndex != mSelectedPegHoleIndex) {
                            SoundUtil.play(this, R.raw.detect0);
                        }
                    }
                    mPrevSelectedPegHoleIndex = mSelectedPegHoleIndex;
                }
                break;
        }
    }


    @SuppressWarnings("deprecation")
    // I know AbsoluteLayout is deprecated, but in this case, it makes sense to use it
    private void moveDraggingPegView(int newX, int newY) {
        AbsoluteLayout.LayoutParams layoutParams = (LayoutParams) mDraggingPegView.getLayoutParams();
        layoutParams.x = newX;
        layoutParams.y = newY;
        mDraggingPegView.setLayoutParams(layoutParams);
    }


    /*
     * Active / inactive rows.
     */

    private void setRowActive(int rowIndex) {
        ViewGroup row = (ViewGroup) mBoardView.getChildAt(rowIndex);
        row.setBackgroundResource(R.drawable.row_bg_active);
        LinearLayout containerCodePegs = (LinearLayout) row.findViewById(R.id.container_codePegs);
        LinearLayout containerHintPegs = (LinearLayout) row.findViewById(R.id.container_hintPegs);

        // make holes focusable, clickable
        int childCount = containerCodePegs.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View codePegView = containerCodePegs.getChildAt(i);
            codePegView.setFocusable(true);
            codePegView.setOnFocusChangeListener(new OnFocusChangeListener() {
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        SoundUtil.play(MainActivity.this, R.raw.detect0);
                    }
                }
            });
            codePegView.setClickable(true);
            final int selectingPegIndex = i;
            codePegView.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    SoundUtil.play(MainActivity.this, R.raw.pick0);
                    mSelectedPegHoleIndex = selectingPegIndex;
                    mSelectedPegView = codePegView;
                    showDialog(DIALOG_PICK_PEG);
                }
            });
        }

        // hide hint pegs
        UiUtil.setInvisible(containerHintPegs);

        // show the OK button
        UiUtil.setVisible(row.findViewById(R.id.button_ok));
    }

    private void setRowInactive(int rowIndex) {
        ViewGroup row = (ViewGroup) mBoardView.getChildAt(rowIndex);
        row.setBackgroundResource(R.drawable.row_bg_inactive);
        LinearLayout containerCodePegs = (LinearLayout) row.findViewById(R.id.container_codePegs);

        // make holes not focusable, not clickable
        int childCount = containerCodePegs.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View codePegView = containerCodePegs.getChildAt(i);
            codePegView.setFocusable(false);
            codePegView.setOnClickListener(null);
            codePegView.setClickable(false);
        }
    }

    private void setRowReceivingDrag(int rowIndex, boolean receiving) {
        ViewGroup row = (ViewGroup) mBoardView.getChildAt(rowIndex);
        if (receiving) {
            row.setBackgroundResource(R.drawable.row_bg_dragging);
        } else {
            row.setBackgroundResource(R.drawable.row_bg_active);
            // reset all the row holes / pegs to default bg
            LinearLayout containerCodePegs = (LinearLayout) row.findViewById(R.id.container_codePegs);
            int childCount = containerCodePegs.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View codePegView = containerCodePegs.getChildAt(i);
                codePegView.setBackgroundResource(R.drawable.peg_bg);
            }

            // reset all the dragging pegs to default bg
            ViewGroup pegPicker = (ViewGroup) mRootView.findViewById(R.id.pegPicker);
            childCount = pegPicker.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View codePegView = pegPicker.getChildAt(i);
                codePegView.setBackgroundResource(0);
            }
        }
    }


    /*
     * Dialog.
     */

    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        switch (id) {
            case DIALOG_PICK_PEG:
                builder.setTitle(R.string.dialog_pickPeg_title);
                builder.setSingleChoiceItems(new PegListAdapter(this), -1, mPickPegOnClickListener);
                builder.setNegativeButton(android.R.string.cancel, null);
                break;

            case DIALOG_GAME_OVER:
                builder.setTitle(R.string.dialog_gameOver_title);
                View dialogContents = mLayoutInflater.inflate(R.layout.dialog_game_over, null, false);
                LinearLayout container = (LinearLayout) dialogContents.findViewById(R.id.container_codePegs);
                for (CodePeg codePeg : mGame.getSecret()) {
                    View pegView = mLayoutInflater.inflate(R.layout.peg, container, false);
                    ((ImageView) pegView.findViewById(R.id.peg)).setImageResource(PegUtil.getDrawable(codePeg));
                    container.addView(pegView);
                }
                builder.setView(dialogContents);
                builder.setPositiveButton(R.string.dialog_gameOver_positive, mNewGameOnClickListener);
                builder.setCancelable(false);
                break;

            case DIALOG_YOU_WON:
                builder.setTitle(R.string.dialog_youWon_title);
                builder.setMessage(getString(R.string.dialog_youWon_message, mGame.getCurrentGuess() + 1));
                builder.setPositiveButton(R.string.dialog_youWon_positive, mNewGameOnClickListener);
                builder.setCancelable(false);
                break;

            case DIALOG_CONFIRM_EXIT:
                builder.setTitle(android.R.string.dialog_alert_title);
                builder.setMessage(R.string.dialog_confirmExit_message);
                builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
                builder.setNegativeButton(android.R.string.no, null);
                break;

            case DIALOG_HELP:
                builder.setTitle(R.string.dialog_help_title);
                WebView webView = new WebView(this);
                int padding = getResources().getDimensionPixelSize(R.dimen.webview_padding);
                webView.setPadding(padding, padding, padding, padding);
                String html;
                try {
                    html = IoUtil.inputStreamToString(getResources().openRawResource(R.raw.help));
                } catch (IOException e) {
                    // should never happen
                    throw new AssertionError("Could not read eula file");
                }
                html = StringUtil.reworkForWebView(html);
                webView.loadData(html, "text/html", "utf-8");
                builder.setView(webView);
                builder.setPositiveButton(android.R.string.ok, null);
                break;

        }
        return builder.create();
    }

    private DialogInterface.OnClickListener mPickPegOnClickListener = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            SoundUtil.play(MainActivity.this, R.raw.drop0);
            dialog.dismiss();
            CodePeg codePeg = CodePeg.values()[which];
            ((ImageView) mSelectedPegView.findViewById(R.id.peg)).setImageResource(PegUtil.getDrawable(codePeg));
            mGame.setGuess(mCurrentRowIndex, mSelectedPegHoleIndex, codePeg);
            updateOkButton();
        }
    };

    private void updateOkButton() {
        ViewGroup row = (ViewGroup) mBoardView.getChildAt(mCurrentRowIndex);
        row.findViewById(R.id.button_ok).setEnabled(mGame.isRowComplete(mCurrentRowIndex));
    }

    private OnClickListener mOkOnClickListener = new OnClickListener() {
        public void onClick(View v) {
            v.setEnabled(false); // avoid double clicking
            GuessResult guessResult = mGame.validateGuess();
            switch (guessResult) {
                case YOU_WON:
                    SoundUtil.play(MainActivity.this, R.raw.win0);
                    showDialog(DIALOG_YOU_WON);
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                    int totalGames = sharedPreferences.getInt(Constants.PREF_TOTAL_GAMES, 0);
                    int totalWon = sharedPreferences.getInt(Constants.PREF_TOTAL_WON, 0);
                    int totalScore = sharedPreferences.getInt(Constants.PREF_TOTAL_SCORE, 0);
                    totalGames++;
                    totalWon++;
                    totalScore += (mNbRows - mCurrentRowIndex) * 10;
                    Editor editor = sharedPreferences.edit();
                    editor.putInt(Constants.PREF_TOTAL_GAMES, totalGames);
                    editor.putInt(Constants.PREF_TOTAL_WON, totalWon);
                    editor.putInt(Constants.PREF_TOTAL_SCORE, totalScore);
                    editor.commit();
                    break;

                case GAME_OVER:
                    // we have 3 'lost' sounds. Pick one randomly
                    switch (new Random().nextInt(3)) {
                        case 0:
                            SoundUtil.play(MainActivity.this, R.raw.lost0);
                            break;
                        case 1:
                            SoundUtil.play(MainActivity.this, R.raw.lost1);
                            break;
                        case 2:
                            SoundUtil.play(MainActivity.this, R.raw.lost2);
                            break;
                    }

                    List<HintPeg> hints = mGame.getHints(mCurrentRowIndex);
                    showHints(hints);
                    setRowInactive(mCurrentRowIndex);
                    showDialog(DIALOG_GAME_OVER);
                    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                    totalGames = sharedPreferences.getInt(Constants.PREF_TOTAL_GAMES, 0);
                    totalGames++;
                    editor = sharedPreferences.edit();
                    editor.putInt(Constants.PREF_TOTAL_GAMES, totalGames);
                    editor.commit();
                    break;

                case TRY_AGAIN:
                    final List<HintPeg> hints2 = mGame.getHints(mCurrentRowIndex);
                    if (hints2.size() == 0) {
                        SoundUtil.play(MainActivity.this, R.raw.nohint0);
                    } else {
                        new Thread() {
                            @Override
                            public void run() {
                                for (HintPeg hintPeg : hints2) {
                                    if (hintPeg == HintPeg.COLOR_AND_POSITION) {
                                        SoundUtil.play(MainActivity.this, R.raw.redhint0);
                                    } else {
                                        SoundUtil.play(MainActivity.this, R.raw.whitehint0);
                                    }
                                    try {
                                        sleep(200);
                                    } catch (InterruptedException e) {
                                        // do nothing
                                    }
                                }
                            }
                        }.start();
                    }
                    showHints(hints2);
                    setRowInactive(mCurrentRowIndex);
                    mCurrentRowIndex++;
                    setRowActive(mCurrentRowIndex);
                    break;
            }
        }
    };


    protected void showHints(List<HintPeg> hints) {
        ViewGroup row = (ViewGroup) mBoardView.getChildAt(mCurrentRowIndex);

        // hide ok button
        UiUtil.setInvisible(row.findViewById(R.id.button_ok));

        // show hints container and fill it
        LinearLayout containerHintPegs = (LinearLayout) row.findViewById(R.id.container_hintPegs);
        UiUtil.setVisible(containerHintPegs);

        LinearLayout containerHintPegs1 = (LinearLayout) containerHintPegs.findViewById(R.id.container_hintPegs1);
        LinearLayout containerHintPegs2 = (LinearLayout) containerHintPegs.findViewById(R.id.container_hintPegs2);
        LinearLayout container;
        int i = 0;
        for (HintPeg hintPeg : hints) {
            if (i < mNbHoles / 2) {
                container = containerHintPegs1;
            } else {
                container = containerHintPegs2;
            }
            View hintPegView = container.getChildAt(i % 2);
            ((ImageView) hintPegView.findViewById(R.id.peg)).setImageResource(PegUtil.getDrawable(hintPeg));
            i++;
        }
    }


    /*
     * Menu.
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(this).inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean pickerShown = sharedPreferences.getBoolean(Constants.PREF_PICKER_SHOWN, true);
        menu.findItem(R.id.menu_showPicker).setTitle(pickerShown ? R.string.menu_hidePicker : R.string.menu_showPicker);

        boolean soundEnabled = sharedPreferences.getBoolean(Constants.PREF_SOUND_ENABLED, true);
        menu.findItem(R.id.menu_soundOnOff).setTitle(soundEnabled ? R.string.menu_soundOff : R.string.menu_soundOn)
                .setIcon(soundEnabled ? R.drawable.ic_menu_sound_off : R.drawable.ic_menu_sound_on);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_about:
                onAboutClicked();
                break;

            case R.id.menu_showPicker:
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                boolean pickerShown = sharedPreferences.getBoolean(Constants.PREF_PICKER_SHOWN, true);
                pickerShown = !pickerShown;
                sharedPreferences.edit().putBoolean(Constants.PREF_PICKER_SHOWN, pickerShown).commit();
                View picker = mRootView.findViewById(R.id.pegPicker);
                if (pickerShown) {
                    UiUtil.setVisible(picker);
                } else {
                    UiUtil.setGone(picker);
                }
                break;

            case R.id.menu_soundOnOff:
                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                boolean soundEnabled = sharedPreferences.getBoolean(Constants.PREF_SOUND_ENABLED, true);
                soundEnabled = !soundEnabled;
                sharedPreferences.edit().putBoolean(Constants.PREF_SOUND_ENABLED, soundEnabled).commit();
                setVolumeControlStream(soundEnabled ? AudioManager.STREAM_MUSIC : AudioManager.USE_DEFAULT_STREAM_TYPE);
                SoundUtil.setEnabled(soundEnabled);
                break;

            case R.id.menu_help:
                showDialog(DIALOG_HELP);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void onAboutClicked() {
        AboutActivityIntentBuilder builder = new AboutActivityIntentBuilder();
        builder.setAppName(getString(R.string.app_name));
        builder.setBuildDate(BuildConfig.BUILD_DATE);
        builder.setGitSha1(BuildConfig.GIT_SHA1);
        builder.setAuthorCopyright(getString(R.string.about_authorCopyright));
        builder.setLicense(getString(R.string.about_License));
        builder.setShareTextSubject(getString(R.string.about_shareText_subject));
        builder.setShareTextBody(getString(R.string.about_shareText_body));
        builder.addLink(getString(R.string.about_email_uri), getString(R.string.about_email_text));
        builder.addLink(getString(R.string.about_web_uri), getString(R.string.about_web_text));
        builder.addLink(getString(R.string.about_sources_uri), getString(R.string.about_sources_text));
        builder.setIsLightIcons(false);
        startActivity(builder.build(this));
    }

    /*
     * Intercept back key.
     * Cf: http://android-developers.blogspot.com/2009/12/back-and-other-hard-keys-three-stories.html
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ECLAIR && keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0) {
            onBackPressed();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        showDialog(DIALOG_CONFIRM_EXIT);
    }
}
