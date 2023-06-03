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

import android.util.Log;

import net.alliedmods.quotes.HttpCodeException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;

public class UrlBuilder {
    private String base_;
    private StringBuilder args_ = new StringBuilder();

    public UrlBuilder(String base) {
        base_ = base;
    }

    public void addParam(String key, String value) throws UnsupportedEncodingException {
        if (args_.length() > 0)
            args_.append("&");
        args_.append(URLEncoder.encode(key, "UTF-8"));
        args_.append("=");
        args_.append(URLEncoder.encode(value, "UTF-8"));
    }

    public URL getUrl() throws MalformedURLException {
        String url = base_ + "?" + args_.toString();
        return new URL(url);
    }

    public static String downloadUrl(URL url) throws IOException {
        return downloadUrl(url, null);
    }

    public static String downloadUrl(URL url, HashMap<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        final WeakReference<HttpURLConnection> con_ref =
                new WeakReference<HttpURLConnection>(connection);
        ICancelable cancel_task = new ICancelable() {
            @Override
            public void onCancel() {
                HttpURLConnection con = con_ref.get();
                if (con != null)
                    con.disconnect();
            }
        };
        IThread.addCancelTask(cancel_task);

        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("charset", "utf-8");
            for (HashMap.Entry<String, String> entry : headers.entrySet())
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();

            if (connection.getResponseCode() != 200)
                throw new HttpCodeException(connection.getResponseCode());

            try (InputStreamReader is = new InputStreamReader(connection.getInputStream());
                 BufferedReader br = new BufferedReader(is))
            {
                StringBuilder sb = new StringBuilder();
                char buffer[] = new char[4096];
                for (; ; ) {
                    int bytes_read = br.read(buffer, 0, buffer.length);
                    if (bytes_read == -1)
                        break;
                    sb.append(buffer, 0, bytes_read);
                }
                return sb.toString();
            }
        } finally {
            IThread.removeCancelTask(cancel_task);
            connection.disconnect();
        }
    }

    public static JSONObject downloadUrlAsJson(URL url) throws IOException, JSONException {
        String text = downloadUrl(url);
        return new JSONObject(text);
    }

    public static JSONArray downloadUrlAsJsonArray(URL url) throws IOException, JSONException
    {
        String text = downloadUrl(url);
        return new JSONArray(text);
    }
}
