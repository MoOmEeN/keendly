package adaptors.feedly;

import adaptors.Adaptor;
import adaptors.auth.Entry;
import adaptors.auth.Subscription;
import adaptors.auth.Tokens;
import adaptors.auth.User;
import adaptors.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpStatus;
import play.libs.F.Promise;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static utils.ConfigUtils.parameter;

public class FeedlyAdaptor extends Adaptor {

    enum FeedlyParam {
        URL("feedly.url"),
        CLIENT_ID("feedly.client_id"),
        CLIENT_SECRET("feedly.client_secret"),
        REDIRECT_URL("feedly.redirect_uri");

        String value;
        FeedlyParam(String value){
            this.value = value;
        }
    }

    private static String feedlyUrl;
    private static String clientId;
    private static String clientSecret;
    private static String redirectUri;

    static {
        init();
    }

    private static void init(){
        feedlyUrl = parameter(FeedlyParam.URL.value);
        clientId = parameter(FeedlyParam.CLIENT_ID.value);
        clientSecret = parameter(FeedlyParam.CLIENT_SECRET.value);
        redirectUri = parameter(FeedlyParam.REDIRECT_URL.value);
    }

    @Override
    public Promise<Tokens> getTokens(String code){
        JsonNode json = Json.newObject()
                .put("grant_type", "authorization_code")
                .put("client_id", clientId)
                .put("client_secret", clientSecret)
                .put("code", code)
                .put("redirect_uri", redirectUri);

        return WS.url(feedlyUrl + "/auth/token")
                .post(json)
                .map(response -> {
                    if (isOk(response.getStatus())) {
                        JsonNode node = response.asJson();
                        String refreshToken = node.get("refresh_token").asText();
                        String accessToken = node.get("access_token").asText();
                        return new Tokens(refreshToken, accessToken);
                    } else {
                        throw new ApiException(response.getStatus(), response.getBody());
                    }
                });
    }

    @Override
    public Promise<User> getUser(Tokens tokens){
        return doGet(feedlyUrl + "/profile", tokens, response -> {
            User user = new User();
            JsonNode node = response.asJson();
            user.setId(node.get("id").asText());
            user.setUserName(node.get("email").asText());
            return user;
        });
    }

    @Override
    public Promise<List<Subscription>> getSubscriptions(Tokens tokens){
        return doGet(feedlyUrl + "/subscriptions", tokens, response -> {
            List<Subscription> subscriptions = new ArrayList<>();
            JsonNode json = response.asJson();
            if (json.isArray()){
                for (JsonNode item : json){
                    subscriptions.add(mapFromJson(item));
                }
            } else {
                subscriptions.add(mapFromJson(json));
            }
            return subscriptions;
        });
    }

    @Override
    public Promise<Map<String, List<Entry>>> getUnread(List<String> feedIds, Tokens tokens){
        return doGet(feedlyUrl + "/markers/counts", tokens, response -> {
            JsonNode json = response.asJson();
            Map<String, Promise<List<Entry>>> resultsPromises = new HashMap<>();
            for (JsonNode feedCount : json.get("unreadcounts")){
                String feedId = feedCount.get("id").asText();
                if (feedIds.contains(feedId)){
                    int count = feedCount.get("count").asInt();
                    Promise<List<Entry>> entries = getUnread(feedId, count, tokens);
                    resultsPromises.put(feedId, entries);
                }
            }
            return resultsPromises.entrySet().stream().collect(Collectors.toMap(
                    entry -> entry.getKey(), entry -> entry.getValue().get(timeoutInSeconds, TimeUnit.SECONDS)));
        });
    }

    private Promise<List<Entry>> getUnread(String feedId, int count, Tokens tokens) {
        return doGet(feedlyUrl + "/streams/" + urlEncode(feedId) + "/contents?count=100", tokens,
                response -> {
                    List<Entry> entries = new ArrayList<>();
                    for (JsonNode item : response.asJson().get("items")){
                        if (item.get("unread").asBoolean()){
                            String url = null;
                            String originId = item.get("originId").asText();
                            if (isURL(originId)){
                                url = originId;
                            } else {
                                for (JsonNode alternate : item.get("alternate")){
                                    if (alternate.get("type").asText().equals("text/html")){
                                        url = alternate.get("href").asText();
                                    }
                                }
                            }
                            if (url != null){
                                Entry entry = new Entry();
                                entry.setUrl(url);
                                entry.setTitle(asText(item, "title"));
                                entry.setAuthor(asText(item, "author"));
                                entry.setPublished(asDate(item, "published"));
                                entry.setContent(extractContent(item));
                                entries.add(entry);
                            }
                            if (entries.size() >= count){
                                break;
                            }
                        }
                    }
                    return entries;
        });
    }

    private String extractContent(JsonNode item){
        if (item.get("content") != null && item.get("content").get("content") != null){
            return item.get("content").get("content").asText();
        } else if (item.get("summary") != null && item.get("summary").get("content") != null){
            return item.get("summary").get("content").asText();
        }
        return null;
    }

    private static String urlEncode(String s){
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isURL(String s){
        try {
            new URL(s);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private static String asText(JsonNode node, String field){
        JsonNode j = node.get(field);
        if (j != null){
            return j.asText();
        }
        return null;
    }

    private static Date asDate(JsonNode node, String field){
        JsonNode j = node.get(field);
        if (j != null){
            Date d = new Date();
            d.setTime(j.asLong());
            return d;
        }
        return null;
    }

    private Subscription mapFromJson(JsonNode json){
        Subscription subscription = new Subscription();
        subscription.setFeedId(json.get("id").asText());
        subscription.setTitle(json.get("title").asText());
        return subscription;
    }

    private <T> Promise<T> doGet(String url, Tokens tokens, Function<WSResponse, T> callback){
        Promise<WSResponse> res = WS.url(url)
                .setHeader("Authorization", "OAuth " + tokens.getAccessToken())
                .get();
        return res
                .flatMap(response -> {
                    int status = response.getStatus();
                    if (isOk(status)) {
                        return Promise.pure(callback.apply(response));
                    } else if (isUnauthorized(status)) {
                        Promise token = refreshAccessToken(tokens.getRefreshToken());
                        return token.flatMap(newToken -> {
                            tokens.setAccessToken((String) newToken);
                            return doGetNoRefresh(url, tokens, callback);
                        });
                    } else {
                        throw new ApiException(response.getStatus(), response.getBody());
                    }
                });
    }

    private <T> Promise<T> doGetNoRefresh(String url, Tokens tokens, Function<WSResponse, T> callback){
        return WS.url(url)
                .setHeader("Authorization", "OAuth " + tokens.getAccessToken())
                .get()
                .map(response -> {
                    if (isOk(response.getStatus())) {
                        return callback.apply(response);
                    } else {
                        throw new ApiException(response.getStatus(), response.getBody());
                    }
                });
    }

    private Promise<String> refreshAccessToken(String refreshToken){
        JsonNode json = Json.newObject()
                .put("grant_type", "refresh_token")
                .put("client_id", clientId)
                .put("client_secret", clientSecret)
                .put("refresh_token", refreshToken);

        return WS.url(feedlyUrl + "/auth/token")
                .post(json)
                .map(response -> {
                    if (isOk(response.getStatus())) {
                        JsonNode node = response.asJson();
                        return node.get("access_token").asText();
                    } else {
                        throw new ApiException(response.getStatus(), response.getBody());
                    }
                });
    }

    private boolean isOk(int status){
        return status == HttpStatus.SC_OK;
    }

    private boolean isUnauthorized(int status){
        if (status == HttpStatus.SC_UNAUTHORIZED || status == HttpStatus.SC_FORBIDDEN){
            return true;
        }
        return false;
    }
}
