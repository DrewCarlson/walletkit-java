/*
 * Created by Michael Carrara <michael.carrara@breadwallet.com> on 5/31/18.
 * Copyright (c) 2018 Breadwinner AG.  All right reserved.
 *
 * See the LICENSE file at the project root for license information.
 * See the CONTRIBUTORS file at the project root for a list of contributors.
 */
package com.breadwallet.crypto.blockchaindb.apis.bdb;

import android.support.annotation.Nullable;
import android.util.Log;

import com.breadwallet.crypto.blockchaindb.DataTask;
import com.breadwallet.crypto.blockchaindb.apis.ArrayResponseParser;
import com.breadwallet.crypto.blockchaindb.apis.ObjectResponseParser;
import com.breadwallet.crypto.blockchaindb.apis.PagedCompletionHandler;
import com.breadwallet.crypto.blockchaindb.errors.QueryError;
import com.breadwallet.crypto.blockchaindb.errors.QueryJsonParseError;
import com.breadwallet.crypto.blockchaindb.errors.QueryModelError;
import com.breadwallet.crypto.blockchaindb.errors.QueryNoDataError;
import com.breadwallet.crypto.blockchaindb.errors.QueryResponseError;
import com.breadwallet.crypto.blockchaindb.errors.QuerySubmissionError;
import com.breadwallet.crypto.blockchaindb.errors.QueryUrlError;
import com.breadwallet.crypto.utility.CompletionHandler;
import com.google.common.base.Optional;
import com.google.common.collect.Multimap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class BdbApiClient {

    private static final String TAG = BdbApiClient.class.getName();

    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String baseUrl;
    private final DataTask dataTask;

    public BdbApiClient(OkHttpClient client, String baseUrl, DataTask dataTask) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.dataTask = dataTask;
    }

    // Create (Crud)

    <T> void sendPost(String resource, Multimap<String, String> params, JSONObject json, ObjectResponseParser<T> parser,
                      CompletionHandler<T, QueryError> handler) {
        makeAndSendRequest(
                Collections.singletonList(resource),
                params,
                json,
                "POST",
                new ObjectHandler<>(parser, handler));
    }

    // Read (cRud)

    /* package */
    <T> void sendGet(String resource, Multimap<String, String> params, ObjectResponseParser<T> parser,
                     CompletionHandler<T, QueryError> handler) {
        makeAndSendRequest(
                Collections.singletonList(resource),
                params,
                null,
                "GET",
                new ObjectHandler<>(parser, handler));
    }

    /* package */
    <T> void sendGetForArray(String resource, Multimap<String, String> params, ArrayResponseParser<T> parser,
                             CompletionHandler<T, QueryError> handler) {
        makeAndSendRequest(
                Collections.singletonList(resource),
                params,
                null,
                "GET",
                new ArrayHandler<>(resource, parser, handler));
    }

    /* package */
    <T> void sendGetForArrayWithPaging(String resource, Multimap<String, String> params, ArrayResponseParser<T> parser,
                                       PagedCompletionHandler<T, QueryError> handler) {
        makeAndSendRequest(
                Collections.singletonList(resource),
                params,
                null,
                "GET",
                new PagedArrayHandler<>(resource, parser, handler));
    }

    /* package */
    <T> void sendGetForArrayWithPaging(String resource, String url, ArrayResponseParser<T> parser,
                                       PagedCompletionHandler<T, QueryError> handler) {
        makeAndSendRequest(
                url,
                "GET",
                new PagedArrayHandler<>(resource, parser, handler));
    }

    /* package */
    <T> void sendGetWithId(String resource, String id, Multimap<String, String> params, ObjectResponseParser<T> parser,
                           CompletionHandler<T, QueryError> handler) {
        makeAndSendRequest(
                Arrays.asList(resource, id),
                params,
                null,
                "GET",
                new ObjectHandler<>(parser, handler));
    }

    // Update (crUd)

    <T> void sendPut(String resource, Multimap<String, String> params, JSONObject json,
                     ObjectResponseParser<T> parser, CompletionHandler<T, QueryError> handler) {
        makeAndSendRequest(
                Collections.singletonList(resource),
                params,
                json,
                "PUT",
                new ObjectHandler<>(parser, handler));
    }

    <T> void sendPutWithId(String resource, String id, Multimap<String, String> params, JSONObject json,
                           ObjectResponseParser<T> parser, CompletionHandler<T, QueryError> handler) {
        makeAndSendRequest(
                Arrays.asList(resource, id),
                params,
                json,
                "PUT",
                new ObjectHandler<>(parser, handler));
    }

    // Delete (crdD)

    /* package */
    <T> void sendDeleteWithId(String resource, String id, Multimap<String, String> params,
                              ObjectResponseParser<T> parser,
                              CompletionHandler<T, QueryError> handler) {
        makeAndSendRequest(
                Arrays.asList(resource, id),
                params,
                null,
                "DELETE",
                new ObjectHandler<>(parser, handler));
    }

    private void makeAndSendRequest(String url,
                                    String httpMethod,
                                    ResponseHandler handler) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        HttpUrl httpUrl = urlBuilder.build();
        Log.d(TAG, String.format("Request: %s: Method: %s", httpUrl, httpMethod));

        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(httpUrl);
        requestBuilder.header("Accept", "application/json");
        requestBuilder.method(httpMethod, null);

        sendRequest(requestBuilder.build(), dataTask, handler);
    }

    private void makeAndSendRequest(List<String> pathSegments,
                                    Multimap<String, String> params,
                                    @Nullable JSONObject json,
                                    String httpMethod,
                                    ResponseHandler handler) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();

        for (String segment : pathSegments) {
            urlBuilder.addPathSegment(segment);
        }

        for (Map.Entry<String, String> entry : params.entries()) {
            String key = entry.getKey();
            String value = entry.getValue();
            urlBuilder.addQueryParameter(key, value);
        }

        HttpUrl httpUrl = urlBuilder.build();
        Log.d(TAG, String.format("Request: %s: Method: %s: Data: %s", httpUrl, httpMethod, json));

        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(httpUrl);
        requestBuilder.header("Accept", "application/json");
        requestBuilder.method(httpMethod, json == null ? null : RequestBody.create(MEDIA_TYPE_JSON, json.toString()));

        sendRequest(requestBuilder.build(), dataTask, handler);
    }

    private <T> void sendRequest(Request request, DataTask dataTask, ResponseHandler handler) {
        dataTask.execute(client, request, new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int responseCode = response.code();
                if (responseCode == 200) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody == null) {
                            Log.e(TAG, "response failed with null body");
                            handler.handleError(new QueryNoDataError());
                        } else {
                            try {
                                handler.handleData(new JSONObject(responseBody.string()));
                            } catch (JSONException e) {
                                Log.e(TAG, "response failed parsing json", e);
                                handler.handleError(new QueryJsonParseError(e.getMessage()));
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "response failed with status " + responseCode);
                    handler.handleError(new QueryResponseError(responseCode));
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "send request failed", e);
                handler.handleError(new QuerySubmissionError(e.getMessage()));
            }
        });
    }

    private interface ResponseHandler {
        void handleData(JSONObject data);
        void handleError(QueryError error);
    }

    private static class ObjectHandler<T> implements ResponseHandler {

        private final ObjectResponseParser<T> parser;
        private final CompletionHandler<T, QueryError> handler;

        ObjectHandler(ObjectResponseParser<T> parser, CompletionHandler<T, QueryError> handler) {
            this.parser = parser;
            this.handler = handler;
        }

        @Override
        public void handleData(JSONObject json) {
            PagedCompletionHandler.PageInfo pageInfo = getPageInfo(json);
            checkState(pageInfo.nextUrl == null);
            checkState(pageInfo.prevUrl== null);

            Optional<T> data = parser.parse(json);
            if (data.isPresent()) {
                handler.handleData(data.get());

            } else {
                QueryError e = new QueryModelError("Transform error");
                Log.e(TAG, "parsing error", e);
                handler.handleError(e);
            }
        }

        @Override
        public void handleError(QueryError error) {
            handler.handleError(error);
        }
    }

    private static class ArrayHandler<T> implements ResponseHandler {

        private final String path;
        private final ArrayResponseParser<T> parser;
        private final CompletionHandler<T, QueryError> handler;


        ArrayHandler(String path, ArrayResponseParser<T> parser, CompletionHandler<T, QueryError> handler) {
            this.path = path;
            this.parser = parser;
            this.handler = handler;
        }

        @Override
        public void handleData(JSONObject json) {
            PagedCompletionHandler.PageInfo pageInfo = getPageInfo(json);
            checkState(pageInfo.nextUrl == null);
            checkState(pageInfo.prevUrl== null);

            JSONObject jsonEmbedded = json.optJSONObject("_embedded");
            JSONArray jsonEmbeddedData = jsonEmbedded == null ? new JSONArray() : jsonEmbedded.optJSONArray(path);

            Optional<T> data = parser.parse(jsonEmbeddedData);
            if (data.isPresent()) {
                handler.handleData(data.get());

            } else {
                QueryError e = new QueryModelError("Transform error");
                Log.e(TAG, "parsing error", e);
                handler.handleError(e);
            }
        }

        @Override
        public void handleError(QueryError error) {
            handler.handleError(error);
        }
    }

    private static class PagedArrayHandler<T> implements ResponseHandler {

        private final String path;
        private final ArrayResponseParser<T> parser;
        private final PagedCompletionHandler<T, QueryError> handler;


        PagedArrayHandler(String path, ArrayResponseParser<T> parser, PagedCompletionHandler<T, QueryError> handler) {
            this.path = path;
            this.parser = parser;
            this.handler = handler;
        }

        @Override
        public void handleData(JSONObject json) {
            PagedCompletionHandler.PageInfo pageInfo = getPageInfo(json);

            JSONObject jsonEmbedded = json.optJSONObject("_embedded");
            JSONArray jsonEmbeddedData = jsonEmbedded == null ? new JSONArray() : jsonEmbedded.optJSONArray(path);

            Optional<T> data = parser.parse(jsonEmbeddedData);
            if (data.isPresent()) {
                handler.handleData(data.get(), pageInfo);

            } else {
                QueryError e = new QueryModelError("Transform error");
                Log.e(TAG, "parsing error", e);
                handler.handleError(e);
            }
        }

        @Override
        public void handleError(QueryError error) {
            handler.handleError(error);
        }
    }

    private static PagedCompletionHandler.PageInfo getPageInfo(JSONObject json) {
        String nextUrl = null;
        String prevUrl = null;
        String selfUrl = null;

        JSONObject links = json.optJSONObject("_links");
        if (null != links) {

            JSONObject next = links.optJSONObject("next");
            if (next!= null) {
                nextUrl = next.optString("href");
            }

            JSONObject prev = links.optJSONObject("prev");
            if (prev!= null) {
                prevUrl = prev.optString("href");
            }

            JSONObject self = links.optJSONObject("self");
            if (self!= null) {
                selfUrl = self.optString("href");
            }

        }

        return new PagedCompletionHandler.PageInfo(nextUrl, prevUrl, selfUrl);
    }
}
