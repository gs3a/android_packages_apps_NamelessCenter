/*
 * <!--
 *    Copyright (C) 2014 Alexander "Evisceration" Martinz
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * -->
 */

package org.namelessrom.center;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.special.ResideMenu.ResideMenu;
import com.special.ResideMenu.ResideMenuItem;

import org.namelessrom.center.database.DatabaseHandler;
import org.namelessrom.center.fragments.HomeFragment;
import org.namelessrom.center.fragments.updates.RomUpdateFragment;
import org.namelessrom.center.interfaces.OnBackPressedListener;
import org.namelessrom.center.interfaces.OnFragmentLoadedListener;
import org.namelessrom.center.services.UpdateCheckService;
import org.namelessrom.center.utils.AnimationHelper;
import org.namelessrom.center.utils.DrawableHelper;
import org.namelessrom.center.utils.PreferenceHelper;

import java.util.ArrayList;

public class MainActivity extends Activity implements OnFragmentLoadedListener,
        View.OnClickListener {

    public static final String ACTION_UPDATES = "org.namelessrom.center.UPDATES";

    private ResideMenu mResideMenu;

    private Fragment mCurrentFragment;
    private Toast    mToast;
    private long     backPressed;

    private static final Object[] MENU_LEFT_ICONS = new Object[]{
            DrawableHelper.getSvgDrawable(R.raw.svg_home),
            DrawableHelper.getSvgDrawable(R.raw.svg_updates),
            DrawableHelper.getSvgDrawable(R.raw.svg_preferences),
    };

    private static final int[] MENU_LEFT_TITLES = new int[]{
            R.string.home,
            R.string.updates,
            R.string.preferences
    };

    private static final int[] MENU_LEFT_IDS = new int[]{
            Constants.MENU_ID_HOME,
            Constants.MENU_ID_UPDATES,
            Constants.MENU_ID_PREFERENCES
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupActionBar();

        mResideMenu = setupMenu();

        // Set the initial fragment
        mCurrentFragment = processIntent(getIntent());
        loadFragment();

        if (PreferenceHelper.getInt(Constants.UPDATE_CHECK_PREF, Constants.UPDATE_FREQ_WEEKLY)
                == Constants.UPDATE_FREQ_AT_APP_START) {
            final Intent i = new Intent(this, UpdateCheckService.class);
            i.setAction(UpdateCheckService.ACTION_CHECK);
            this.startService(i);
        }
    }

    @Override protected void onDestroy() {
        DatabaseHandler.tearDown();
        super.onDestroy();
    }

    private Fragment processIntent(final Intent intent) {
        if (intent == null || intent.getAction() == null) return new HomeFragment();

        final String action = intent.getAction();
        if (action.isEmpty()) return new HomeFragment();

        if (ACTION_UPDATES.equals(action)) {
            return new RomUpdateFragment();
        }

        return new HomeFragment();
    }

    private void setupActionBar() {
        final ActionBar actionBar = getActionBar();
        if (actionBar == null) return;

        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    private ResideMenu setupMenu() {
        final ResideMenu resideMenu = new ResideMenu(this);

        resideMenu.setBackground(R.drawable.main_background);
        resideMenu.attachToActivity(this);
        resideMenu.setScaleValue(0.6f);

        // Disable the right menu
        resideMenu.setSwipeDirectionDisable(ResideMenu.DIRECTION_RIGHT);

        resideMenu.setMenuItems(buildMenuLeft(), ResideMenu.DIRECTION_LEFT);

        return resideMenu;
    }

    private ArrayList<ResideMenuItem> buildMenuLeft() {
        final ArrayList<ResideMenuItem> menuItems = new ArrayList<ResideMenuItem>(1);

        ResideMenuItem item;
        for (int i = 0; i < MENU_LEFT_ICONS.length; i++) {
            item = new ResideMenuItem(this, MENU_LEFT_ICONS[i], MENU_LEFT_TITLES[i]);
            item.setMenuId(MENU_LEFT_IDS[i]);
            item.setOnClickListener(this);
            menuItems.add(item);
        }

        return menuItems;
    }

    @Override public void onClick(final View v) {
        if (!(v instanceof ResideMenuItem)) return;

        final ResideMenuItem item = ((ResideMenuItem) v);
        final int id = item.getMenuId();

        // get the view we want to animate
        final View view = item.getIcon();
        // save its current layertype to restore it later. this is needed as, in this case, the view
        // is a circularimageview, which uses a software layer to render the borders and shadow
        final int layerType = view.getLayerType();
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        final ObjectAnimator animator = AnimationHelper.scaleX(view, 0.0f, 1.0f, 150);
        animator.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationStart(final Animator animation) { }

            @Override
            public void onAnimationEnd(final Animator animation) {
                view.setLayerType(layerType, null);
                switch (id) {
                    default:
                    case Constants.MENU_ID_HOME:
                        mCurrentFragment = new HomeFragment();
                        break;
                    case Constants.MENU_ID_UPDATES:
                        mCurrentFragment = new RomUpdateFragment();
                        break;
                    case Constants.MENU_ID_PREFERENCES:
                        final Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                        startActivity(i);
                        overridePendingTransition(R.anim.enter_left, R.anim.enter_right);
                        return;
                }

                // replace the content with mCurrentFragment
                loadFragment();
            }

            @Override public void onAnimationCancel(final Animator animation) { }

            @Override public void onAnimationRepeat(final Animator animation) { }
        });
        animator.start();
    }

    @Override public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                if (!mResideMenu.isOpened()) mResideMenu.openMenu(ResideMenu.DIRECTION_LEFT);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override public void onBackPressed() {
        if (mResideMenu != null && mResideMenu.isOpened()) {
            mResideMenu.closeMenu();
        } else if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStack();
        } else if (mCurrentFragment != null && mCurrentFragment instanceof OnBackPressedListener
                && ((OnBackPressedListener) mCurrentFragment).onBackPressed()) {
            Logger.v(this, "onBackPressed()");
        } else {
            if ((backPressed + 2000) > System.currentTimeMillis()) {
                if (mToast != null) { mToast.cancel(); }
                finish();
            } else {
                mToast = Toast.makeText(getBaseContext(), getString(R.string.action_press_again),
                        Toast.LENGTH_SHORT);
                mToast.show();
            }
            backPressed = System.currentTimeMillis();
        }
    }

    @Override public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        return mResideMenu.dispatchTouchEvent(ev);
    }

    private void loadFragment() {
        mResideMenu.clearIgnoredViewList();
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, mCurrentFragment)
                .setTransitionStyle(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
    }

    @Override public void onFragmentLoaded() {
        if (mResideMenu.isOpened()) mResideMenu.closeMenu();
    }

}
