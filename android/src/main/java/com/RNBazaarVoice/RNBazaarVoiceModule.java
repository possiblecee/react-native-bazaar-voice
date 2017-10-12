package com.RNBazaarVoice;

import android.support.annotation.NonNull;
import android.util.Log;

import com.bazaarvoice.bvandroidsdk.Action;
import com.bazaarvoice.bvandroidsdk.AuthorIncludeType;
import com.bazaarvoice.bvandroidsdk.AuthorsRequest;
import com.bazaarvoice.bvandroidsdk.AuthorsResponse;
import com.bazaarvoice.bvandroidsdk.BVConversationsClient;
import com.bazaarvoice.bvandroidsdk.BVSDK;
import com.bazaarvoice.bvandroidsdk.BazaarException;
import com.bazaarvoice.bvandroidsdk.BulkRatingOptions;
import com.bazaarvoice.bvandroidsdk.BulkRatingsRequest;
import com.bazaarvoice.bvandroidsdk.BulkRatingsResponse;
import com.bazaarvoice.bvandroidsdk.EqualityOperator;
import com.bazaarvoice.bvandroidsdk.ProductStatistics;
import com.bazaarvoice.bvandroidsdk.Review;
import com.bazaarvoice.bvandroidsdk.ReviewOptions;
import com.bazaarvoice.bvandroidsdk.ReviewResponse;
import com.bazaarvoice.bvandroidsdk.ReviewStatistics;
import com.bazaarvoice.bvandroidsdk.ReviewSubmissionRequest;
import com.bazaarvoice.bvandroidsdk.ReviewSubmissionResponse;
import com.bazaarvoice.bvandroidsdk.ReviewsRequest;
import com.bazaarvoice.bvandroidsdk.Statistics;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class RNBazaarVoiceModule extends ReactContextBaseJavaModule {

    private static final Gson gson = new Gson();
    private static final String TAG = "RNBazaarVoiceModule";
    private static SimpleDateFormat simpleDateFormat =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssSSS", Locale.US);
    private final BVConversationsClient client;

    public RNBazaarVoiceModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.client = new BVConversationsClient.Builder(BVSDK.getInstance()).build();
    }

    private static WritableMap jsonToReact(JSONObject jsonObject) throws JSONException {
        WritableMap writableMap = Arguments.createMap();
        Iterator iterator = jsonObject.keys();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();
            Object value = jsonObject.get(key);
            if (value instanceof Float || value instanceof Double) {
                writableMap.putDouble(key, jsonObject.getDouble(key));
            } else if (value instanceof Number) {
                writableMap.putInt(key, jsonObject.getInt(key));
            } else if (value instanceof String) {
                writableMap.putString(key, jsonObject.getString(key));
            } else if (value instanceof JSONObject) {
                writableMap.putMap(key, jsonToReact(jsonObject.getJSONObject(key)));
            } else if (value instanceof JSONArray) {
                writableMap.putArray(key, jsonToReact(jsonObject.getJSONArray(key)));
            } else if (value == JSONObject.NULL) {
                writableMap.putNull(key);
            }
        }
        return writableMap;
    }

    private static WritableArray jsonToReact(@NonNull JSONArray jsonArray) throws JSONException {
        WritableArray writableArray = Arguments.createArray();
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof Float || value instanceof Double) {
                writableArray.pushDouble(jsonArray.getDouble(i));
            } else if (value instanceof Number) {
                writableArray.pushInt(jsonArray.getInt(i));
            } else if (value instanceof String) {
                writableArray.pushString(jsonArray.getString(i));
            } else if (value instanceof JSONObject) {
                writableArray.pushMap(jsonToReact(jsonArray.getJSONObject(i)));
            } else if (value instanceof JSONArray) {
                writableArray.pushArray(jsonToReact(jsonArray.getJSONArray(i)));
            } else if (value == JSONObject.NULL) {
                writableArray.pushNull();
            }
        }
        return writableArray;
    }

    private static String ucFirstLetter(String in) {
        return Character.toUpperCase(in.charAt(0)) + in.substring(1);
    }

    private static WritableMap toReact(Object o) throws JSONException {
        if (o instanceof List) {
            throw new JSONException("Expected non-list object! Use toReactArray instead!");
        }
        return jsonToReact(new JSONObject(gson.toJson(o)));
    }

    private static WritableArray toReactArray(List list) throws JSONException {
        return jsonToReact(changeReviewsRafaWay(new JSONArray(gson.toJson(list))));
    }

    @NonNull
    private static JSONArray changeReviewsRafaWay(JSONArray reviews) {
        for (int i = 0; i < reviews.length(); i++) {
            JSONObject reviewMap;
            try {
                reviewMap = reviews.getJSONObject(i);
            } catch (JSONException e) {
                return new JSONArray();
            }
            extraName(reviewMap, "AuthorId", "userUuid");
            extraName(reviewMap, "Id", "reviewId");
            extraName(reviewMap, "UserNickname", "nickname");
            extraName(reviewMap, "ContentLocale", "locale");
            extraName(reviewMap, "SubmissionId", "submissionId");
            extraName(reviewMap, "ProductId", "productId");
            extraName(reviewMap, "Title", "title");
            extraName(reviewMap, "ReviewText", "reviewText");
            extraName(reviewMap, "Rating", "rating");
            try {
                reviewMap.put("date",
                        simpleDateFormat.parse(reviewMap.getString("SubmissionTime")).toString());
            } catch (ParseException | JSONException e) {
                extraName(reviewMap, "SubmissionTime", "date");
            }


            try {
                reviewMap.put("avatar",
                        reviewMap.getJSONObject("AdditionalFields").getJSONObject("Avatar").getString("Value"));
                reviewMap.put("additionalFields", reviewMap.getJSONObject("AdditionalFields"));
                reviewMap.getJSONObject("additionalFields").put("avatar", reviewMap.getString("avatar"));
            } catch (JSONException e) {
                Log.w(TAG, "changeReviewsRafaWay: no Avatar for review no" + i);
            }
        }
        return reviews;
    }

    private static JSONObject extraName(JSONObject bundle, String from, String to) {
        try {
            bundle.put(to, bundle.getString(from));
        } catch (JSONException e) {
            Log.w(TAG, "extraName: cannot find name: " + from);
        }
        return bundle;
    }

    @Override
    public String getName() {
        return "RNBazaarVoice";
    }

    @ReactMethod
    public void getUserSubmittedReviews(
            String authorId, int limit, String locale, final Promise promise) {
        AuthorsRequest request =
                new AuthorsRequest.Builder(authorId).addIncludeStatistics(AuthorIncludeType.REVIEWS)
                        .addIncludeContent(AuthorIncludeType.REVIEWS, limit)
                        .build();
        try {
            AuthorsResponse response = client.prepareCall(request).loadSync();
            Log.w(TAG, "getUserSubmittedReviews: " + gson.toJson(response));
            List<Review> responseList = new ArrayList<>();
            if (!response.getResults().isEmpty()) {
                responseList.addAll(response.getIncludes().getReviews());
                filterReviews(responseList, locale);
            }
            promise.resolve(toReactArray(responseList));
        } catch (BazaarException | JSONException e) {
            promise.reject(e);
        }
    }

    private void filterReviews(List<Review> reviews, String locale) {
        Iterator<Review> reviewIterator = reviews.iterator();
        while (reviewIterator.hasNext()) {
            Review nextReview = reviewIterator.next();
            if (!locale.equals(nextReview.getContentLocale()) ||
                    nextReview.getModerationStatus() != null &&
                            !"APPROVED".equals(nextReview.getModerationStatus())) {
                reviewIterator.remove();
            }
        }
    }

    @ReactMethod
    public void getProductReviewsWithId(
            String productId, int limit, int offset, String locale, final Promise promise) {
        ReviewsRequest reviewsRequest = new ReviewsRequest.Builder(productId,
                limit,
                offset).addFilter(ReviewOptions.Filter.ContentLocale, EqualityOperator.EQ, locale).build();
        try {
            ReviewResponse response = client.prepareCall(reviewsRequest).loadSync();
            Log.w(TAG, "getProductReviewsWithId: " + gson.toJson(response.getResults()));
            filterReviews(response.getResults(), locale);
            promise.resolve(toReactArray(response.getResults()));
        } catch (BazaarException | JSONException e) {
            Log.e(TAG, "getProductReviewsWithId: ", e);
            promise.reject(e);
        }
    }

    @ReactMethod
    public void getProductsStats(
            ReadableArray productIds, String locale, final Promise promise) {
        List<String> products = new ArrayList<String>();
        for (int i = 0; i < productIds.size(); i++) {
            products.add(productIds.getString(i));
        }

        BulkRatingsRequest request =
                new BulkRatingsRequest.Builder(products, BulkRatingOptions.StatsType.All).addFilter(
                        BulkRatingOptions.Filter.ContentLocale,
                        EqualityOperator.EQ,
                        locale).build();
        try {
            BulkRatingsResponse response = client.prepareCall(request).loadSync();
            WritableArray responseList = Arguments.createArray();

            for (Statistics stats : response.getResults()) {
                ProductStatistics productStats = stats.getProductStatistics();
                WritableMap product = Arguments.createMap();

                product.putDouble("averageOverallRating", Float.NaN);
                if (productStats != null) {

                    product.putString("productId", productStats.getProductId());

                    ReviewStatistics reviewStats = productStats.getReviewStatistics();
                    if (reviewStats != null) {
                        product.putDouble("averageOverallRating", reviewStats.getAverageOverallRating());
                        product.putInt("totalReviewCount", reviewStats.getTotalReviewCount());
                    }
                }
                responseList.pushMap(product);
            }

            Log.w(TAG, "getProductsReviews: " + gson.toJson(responseList));
            promise.resolve(responseList);
        } catch (BazaarException e) {
            e.printStackTrace();
            promise.reject(e);
        }
    }

    @ReactMethod
    public void submitReview(
            ReadableMap review, String productId, ReadableMap user, final Promise promise) {
        ReviewSubmissionRequest.Builder previewSubmissionBuilder = new ReviewSubmissionRequest.Builder(
                Action.Submit, productId);
        if (user.hasKey("locale"))
            previewSubmissionBuilder.locale(user.getString("locale"));
        if (user.hasKey("nickname"))
            previewSubmissionBuilder.userNickname(user.getString("nickname"));
        if (user.hasKey("token"))
            previewSubmissionBuilder.user(user.getString("token"));
        if (user.hasKey("email"))
            previewSubmissionBuilder.userEmail(user.getString("email"));
        if (user.hasKey("sendEmailAlertWhenPublished"))
            previewSubmissionBuilder.sendEmailAlertWhenPublished(user.getBoolean("sendEmailAlertWhenPublished"));
        if (review.hasKey("title"))
            previewSubmissionBuilder.title(review.getString("title"));
        if (review.hasKey("text"))
            previewSubmissionBuilder.reviewText(review.getString("text"));
        if (review.hasKey("rating"))
            previewSubmissionBuilder.rating(review.getInt("rating"));
        if (review.hasKey("isRecommended"))
            previewSubmissionBuilder.isRecommended(review.getBoolean("isRecommended"));

        final String[] additionalReviewIntProperties = new String[]{
                "comfort", "size", "rating", "quality", "width"
        };

        for (String addRevKey : additionalReviewIntProperties) {
            if (review.hasKey(addRevKey))
                previewSubmissionBuilder.addRatingQuestion(ucFirstLetter(addRevKey),
                        review.getInt(addRevKey));
        }

        if (user.hasKey("profilePicture"))
            previewSubmissionBuilder.addAdditionalField("Avatar", user.getString("profilePicture"));

        try {
            ReviewSubmissionResponse response =
                    client.prepareCall(previewSubmissionBuilder.build()).loadSync();
            Log.w(TAG, "submitReview: " + gson.toJson(response));
            if (response.getErrors().isEmpty()) {
                promise.resolve(toReact(new ReviewSubmitResponse("ok")));
            } else {
                promise.resolve(toReact(new ReviewSubmitResponse(new Exception(response.getErrors()
                        .get(0)
                        .getCode() + " " + response.getErrors().get(0).getMessage()))));
            }
        } catch (BazaarException | JSONException e) {
            Log.e(TAG, "submitReview: ", e);
            try {
                promise.resolve(toReact(new ReviewSubmitResponse(e)));
            } catch (JSONException e1) {
                e1.printStackTrace();
                promise.reject(e1);
            }
        }
    }

    private class ReviewSubmitResponse {
        public String success;
        public String submissionId;
        public String error;

        public ReviewSubmitResponse(String submissionId) {
            this.success = "1";
            this.submissionId = submissionId;
        }

        public ReviewSubmitResponse(Throwable t) {
            this.success = "0";
            this.error = t.getMessage();
        }
    }
}
