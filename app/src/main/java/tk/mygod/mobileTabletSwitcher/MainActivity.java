package tk.mygod.mobileTabletSwitcher;

import java.io.*;
import java.net.URL;
import java.util.Properties;

import android.app.*;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v13.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

public class MainActivity extends Activity implements ActionBar.TabListener {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v13.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    private boolean su(String command, boolean remount) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            if (remount) os.writeBytes("mount -o remount,rw -t yaffs2 /dev/block/mtdblock4 /system\n");
            os.writeBytes(command);
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
            return true;
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), getString(R.string.error) + e.getMessage(), Toast.LENGTH_LONG)
                    .show();
            return false;
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                process.destroy();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), getString(R.string.error) + e.getMessage(), Toast.LENGTH_LONG)
                        .show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        tempFile = getCacheDir() + "/build.prop";
        if (!su("cp -f /system/build.prop " + tempFile + "\nchmod 777 " + tempFile + '\n', true)) finish();
        prop = new Properties();
        try {
            prop.load(new FileInputStream(tempFile));
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), getString(R.string.error) + e.getMessage(), Toast.LENGTH_LONG)
                    .show();
            finish();
        }
        currentDpi = (int) Math.floor(Double.parseDouble(prop.getProperty("ro.sf.lcd_density")));
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        if ((defaultDpi = preferences.getInt("defaultValue", 0)) == 0)
        {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("defaultValue", defaultDpi = currentDpi);
            editor.commit();
        }
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        deviceMinWidth = metrics.widthPixels < metrics.heightPixels ? metrics.widthPixels : metrics.heightPixels;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the action bar.
        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }
    }

    private static Properties prop;
    private static String tempFile;
    private static int currentDpi, defaultDpi, deviceMinWidth;

    private MainFragment mainFragment;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId())
        {
            case R.id.action_save_changes:
                int newDpi;
                switch (mainFragment == null ? -1 : mainFragment.Radios.getCheckedRadioButtonId())
                {
                    case R.id.radioDefault:
                        newDpi = defaultDpi;
                        break;
                    case R.id.radioSmallPhone:
                        newDpi = deviceMinWidth / 2;            // 320dp
                        break;
                    case R.id.radioLargePhone:
                        newDpi = deviceMinWidth / 3;            // 480dp
                        break;
                    case R.id.radioSmallTablet:
                        newDpi = (int) (deviceMinWidth / 3.75); // 600dp
                        break;
                    case R.id.radioLargeTablet:
                        newDpi = (int) (deviceMinWidth / 4.5);  // 720dp
                        break;
                    default:
                        try {
                            newDpi = Integer.parseInt(mainFragment.CustomDpi.getText().toString());
                        }
                        catch (Exception exc) {
                            newDpi = currentDpi;
                        }
                        break;
                }
                if (newDpi == currentDpi)
                    Toast.makeText(getApplicationContext(), R.string.nothing_changed, Toast.LENGTH_SHORT).show();
                else
                {
                    prop.setProperty("ro.sf.lcd_density", Integer.toString(currentDpi = newDpi));
                    try {
                        FileOutputStream stream = new FileOutputStream(tempFile);
                        prop.store(stream, "Created by Mobile/Tablet Switcher");
                        stream.close();
                    } catch (IOException e) {
                        Toast.makeText(getApplicationContext(), getString(R.string.error) + e.getMessage(),
                                       Toast.LENGTH_LONG).show();
                        return true;
                    }
                    su("busybox cp -f " + tempFile + " /system/build.prop\nchmod 644 /system/bu ild.prop\n", true);
                    Toast.makeText(getApplicationContext(), R.string.nailed_it, Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.action_reboot:
                su("/system/bin/reboot\n", false);
                Toast.makeText(getApplicationContext(), R.string.nailed_it, Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_check_for_updates:
                final ProgressDialog dialog = ProgressDialog.show(this, getString(R.string.please_wait),
                                                                  getString(R.string.checking_for_updates), true);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        BufferedReader reader = null;
                        String url = null;
                        Exception exc = null;
                        try {
                            reader = new BufferedReader(new InputStreamReader(new URL("http://mygod.tk/product/update/"
                                    + getPackageManager().getPackageInfo(getPackageName(), 0).versionCode + '/')
                                    .openStream()));
                            url = reader.readLine();
                        } catch (Exception e) {
                            exc = e;
                        } finally {
                            try {
                                if (reader != null) reader.close();
                            } catch (Exception e) { }
                        }
                        dialog.dismiss();
                        final String urlCopy = url;
                        final Exception excCopy = exc;
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
                                        .setTitle(excCopy == null
                                                ? urlCopy == null || urlCopy.isEmpty()
                                                        ? R.string.no_update_available : R.string.update_available
                                                : R.string.check_for_updates_failed);
                                if (urlCopy != null && !urlCopy.isEmpty()) {
                                    builder.setNegativeButton(R.string.go_to_download_page,
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlCopy)));
                                                }
                                            });
                                }
                                builder.setNeutralButton(R.string.go_to_product_page,
                                                         new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        startActivity(new Intent(Intent.ACTION_VIEW,
                                                Uri.parse("http://mygod.tk/product/mobile-tablet-switcher/")));
                                    }
                                }).setPositiveButton(R.string.close, null).show();
                            }
                        });
                    }
                }).start();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction ft) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction ft) {
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            switch (position)
            {
                case 0:
                    return new CautionsFragment();
                case 1:
                    return mainFragment = new MainFragment();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getString(position == 0 ? R.string.title_section_cautions : R.string.title_section_main);
        }
    }

    public static class CautionsFragment extends Fragment {
        public CautionsFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_cautions, container, false);
            ((TextView) rootView.findViewById(R.id.cautions)).setText(Html.fromHtml(getString(R.string.cautions)));
            return rootView;
        }
    }

    public static class MainFragment extends Fragment {
        public MainFragment() {
        }

        public RadioGroup Radios;
        public EditText CustomDpi;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            Radios = (RadioGroup) rootView.findViewById(R.id.radioGroup);
            ((RadioButton) rootView.findViewById(R.id.radioDefault))
                    .setText(String.format(getString(R.string.radio_default), defaultDpi));
            ((RadioButton) rootView.findViewById(R.id.radioSmallPhone))
                    .setText(String.format(getString(R.string.radio_phone_small), deviceMinWidth / 2));
            ((RadioButton) rootView.findViewById(R.id.radioLargePhone))
                    .setText(String.format(getString(R.string.radio_phone_large), deviceMinWidth / 3));
            ((RadioButton) rootView.findViewById(R.id.radioSmallTablet))
                    .setText(String.format(getString(R.string.radio_tablet_small), (int) (deviceMinWidth / 3.75)));
            ((RadioButton) rootView.findViewById(R.id.radioLargeTablet))
                    .setText(String.format(getString(R.string.radio_tablet_large), (int) (deviceMinWidth / 4.5)));
            (CustomDpi = (EditText)rootView.findViewById(R.id.customDpi)).setText(Integer.toString(currentDpi));
            ((TextView) rootView.findViewById(R.id.instructions))
                    .setText(Html.fromHtml(getString(R.string.instructions)));
            return rootView;
        }
    }
}
