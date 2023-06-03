// vim: set sts=4 sw=4 tw=99 et:
//
// Copyright (C) 2019 AlliedModders LLC
// Copyright (C) 2019 David Anderson
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
package net.alliedmods.stocks;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import net.alliedmods.quotes.IQuoteService;
import net.alliedmods.quotes.QuoteFetcher;
import net.alliedmods.quotes.QuoteRequest;
import net.alliedmods.quotes.QuoteResult;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private IThread test_thread_;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference pref = findPreference("api_kind");
            pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    changeApiChoice(newValue);
                    return true;
                }
            });

            pref = findPreference("test_api_button");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startApiTest();
                    return true;
                }
            });

            String api_kind = StockApplication.getSettings().getApiServiceName();
            addApiPrefs(api_kind);
        }

        private void startApiTest() {
            if (test_thread_ != null) {
                test_thread_.shutdown();
                test_thread_ = null;
            }

            final Preference pref = findPreference("test_api_button");
            final IQuoteService service = StockApplication.getSettings().createQuoteService();
            if (service == null) {
                pref.setSummary("FAILED: API not configured");
                return;
            }

            final Handler handler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    endTestApi(msg);
                    return true;
                }
            });

            test_thread_ = new IThread(new Runnable() {
                @Override
                public void run() {
                    QuoteRequest request = new QuoteRequest("AAPL", null, true);
                    List<QuoteRequest> requests = new ArrayList<QuoteRequest>();
                    requests.add(request);

                    QuoteResult result = null;
                    try {
                        List<QuoteResult> results = service.query(requests);
                        if (results == null)
                            throw new Exception("Results object is null");
                        if (results.size() == 0)
                            throw new Exception("No results were returned");
                        result = results.get(0);
                    } catch (Exception e) {
                        result = new QuoteResult();
                        result.success = false;
                        result.error = e.getMessage();
                    }

                    Message msg = new Message();
                    msg.setAsynchronous(true);
                    msg.setData(result.serialize());
                    handler.sendMessage(msg);
                }
            });
        }

        private void endTestApi(Message msg) {
            if (test_thread_ != null) {
                test_thread_.shutdown();
                test_thread_ = null;
            }

            Preference pref = findPreference("test_api_button");

            Bundle b = msg.getData();
            QuoteResult result = QuoteResult.deserialize(b);
            if (!result.success) {
                pref.setSummary("FAILED: " + result.error);
                return;
            }
            if (!result.symbol.equals("AAPL")) {
                pref.setSummary("FAILED: Wrong symbol name");
                return;
            }
            if (result.companyName == null) {
                pref.setSummary("FAILED: Company name not returned");
                return;
            }

            pref.setSummary("Passed!");
        }

        private void changeApiChoice(Object newValue) {
            String new_api = (String) newValue;

            PreferenceScreen screen = (PreferenceScreen) findPreference("preferenceScreen");
            PreferenceCategory api_cat = (PreferenceCategory) findPreference("api_settings");
            if (api_cat != null)
                screen.removePreference(api_cat);
            addApiPrefs(new_api);

            Preference pref = findPreference("test_api_button");
            pref.setSummary(R.string.test_api_help);
        }

        private void addApiPrefs(String api_kind) {
            if (api_kind.equals("alphavantage"))
                addPreferencesFromResource(R.xml.alphavantage_preferences);
            else if (api_kind.equals("iexcloud"))
                addPreferencesFromResource(R.xml.iexcloud_preferences);
            else if (api_kind.equals("intrinio"))
                addPreferencesFromResource(R.xml.intrinio_preferences);
            else if (api_kind.equals("worldtradingdata"))
                addPreferencesFromResource(R.xml.wtd_preferences);
            else if (api_kind.equals("yhfinance"))
                addPreferencesFromResource(R.xml.yhfinance_preferences);
        }
    }
}
