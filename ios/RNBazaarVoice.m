#import "RNBazaarVoice.h"
#import "RCTConvert.h"
#import "RCTLog.h"
#import "RCTUIManager.h"
#import "RCTUtils.h"
#import "RCTBridge.h"
#import "RCTBridgeModule.h"
@import BVSDK;

@implementation RNBazaarVoice

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE()

- (dispatch_queue_t)methodQueue {
    return dispatch_get_main_queue();
}

RCT_EXPORT_METHOD(getUserSubmittedReviews:(NSString *)authorId withLimit:(int)limit andLocale:(NSString*)locale withResolver:(RCTPromiseResolveBlock)resolve andRejecter:(RCTResponseSenderBlock)reject) {
    BVAuthorRequest *request = [[BVAuthorRequest alloc] initWithAuthorId:authorId];
    [request includeStatistics:BVAuthorContentTypeReviews];
    [request includeContent:BVAuthorContentTypeReviews limit:limit];
    [request load:^(BVAuthorResponse * _Nonnull response) {
        NSArray *reviews = [NSArray new];
        if (response.results.count == 0 ) {
            reviews = @[];
        } else {
            BVAuthor *author = response.results[0];
            if (author.includedReviews == 0) {
                reviews = @[];
            } else {
                reviews = [self filterReviewsByLocale:[self parseReviews:author.includedReviews] andLocale:locale];
            }
        }
        
        resolve(reviews);
    } failure:^(NSArray * _Nonnull errors) {
        reject(errors);
    }];
}

RCT_EXPORT_METHOD(getProductReviewsWithId:(NSString *)productId andLimit:(int)limit offset:(int)offset andLocale:(NSString*)locale withResolver:(RCTPromiseResolveBlock)resolve andRejecter:(RCTResponseSenderBlock)reject) {
    BVReviewsTableView *reviewsTableView = [BVReviewsTableView new];
    BVReviewsRequest* request = [[BVReviewsRequest alloc] initWithProductId:productId limit:limit offset:offset];
    [request addFilter:BVReviewFilterTypeContentLocale filterOperator:BVFilterOperatorEqualTo value:locale];
    [reviewsTableView load:request success:^(BVReviewsResponse * _Nonnull response) {
        resolve([self filterReviewsByLocale:[self parseReviews:response.results] andLocale:locale]);
    } failure:^(NSArray<NSError *> * _Nonnull errors) {
        reject(@[@"Error"]);
    }];
}

RCT_EXPORT_METHOD(getProductsStats:(NSArray *)productIds andLocale:(NSString*)locale withResolver:(RCTPromiseResolveBlock)resolve andRejecter:(RCTResponseSenderBlock)reject) {
    BVBulkRatingsRequest* request = [[BVBulkRatingsRequest alloc] initWithProductIds:productIds statistics:BulkRatingsStatsTypeAll];
    [request addFilter:BVBulkRatingsFilterTypeContentLocale filterOperator:BVFilterOperatorEqualTo values:@[locale]];
    [request load:^(BVBulkRatingsResponse * _Nonnull response) {
        NSArray* ratings = response.results;
        NSMutableArray* results = [[NSMutableArray alloc] init];
        
        for (BVProductStatistics* rating in ratings) {
            NSMutableDictionary* product = [[NSMutableDictionary alloc]init];
            NSString* productId = rating.productId;
            NSNumber* averageOverallRating = rating.reviewStatistics.averageOverallRating;
            
            [product setObject:productId forKey:@"productId"];
            [product setObject:averageOverallRating forKey:@"averageOverallRating"];
            
            [results addObject:product];
        }
        
        resolve(results);
        
    } failure:^(NSArray * _Nonnull errors) {
        reject(errors);
    }];
}

RCT_EXPORT_METHOD(submitReview:(NSDictionary *)review fromProduct:(NSString *)productId andUser:(NSDictionary *)user withResolver:(RCTPromiseResolveBlock)resolve andRejecter:(RCTResponseErrorBlock)reject) {
    NSString *nickname = [user objectForKey:@"nickname"];
    NSString *locale = [user objectForKey:@"locale"];
    NSString *token = [user objectForKey:@"token"];
    NSString *email = [user objectForKey:@"email"];
    NSString *profilePicture = [user objectForKey:@"profilePicture"];
    bool sendEmailAlertWhenPublished = [user objectForKey:@"sendEmailAlertWhenPublished"];
    
    NSString *title = [review objectForKey:@"title"];
    NSString *text = [review objectForKey:@"text"];
    int rating = [[review valueForKey:@"rating"] intValue];
    BVReviewSubmission* bvReview = [[BVReviewSubmission alloc] initWithReviewTitle:title
                                                                        reviewText:text
                                                                            rating:rating
                                                                         productId:productId];
    bvReview.action = BVSubmissionActionSubmit;
    bvReview.locale = locale;
    bvReview.userNickname = nickname;
    bvReview.user = token;
    bvReview.userEmail = email;
    bvReview.sendEmailAlertWhenPublished = [NSNumber numberWithBool:sendEmailAlertWhenPublished];

    if ([review valueForKey:@"comfort"]) {
        int comfort = [[review valueForKey:@"comfort"] intValue];
        [bvReview addRatingQuestion:@"Comfort" value:comfort];
    }
    
    if ([review valueForKey:@"size"]) {
        int size = [[review valueForKey:@"size"] intValue];
        [bvReview addRatingQuestion:@"Size" value:size];
    }

    if ([review valueForKey:@"quality"]) {
        int quality = [[review valueForKey:@"quality"] intValue];
        [bvReview addRatingQuestion:@"Quality" value:quality];
    }
    
    if ([review valueForKey:@"width"]) {
        int width = [[review valueForKey:@"width"] intValue];
        [bvReview addRatingQuestion:@"Width" value:width];
    }
    
    [bvReview addAdditionalField:@"Avatar" value:profilePicture];
    [bvReview submit:^(BVReviewSubmissionResponse * _Nonnull response) {
        NSMutableDictionary *result = [NSMutableDictionary new];
        if (response.submissionId) {
            [result setObject:response.submissionId forKey:@"submissionId"];
            [result setObject:[NSNumber numberWithBool:YES] forKey:@"success"];
            resolve(result);
        } else {
            [result setObject:[NSNumber numberWithBool:NO] forKey:@"success"];
            [result setObject:@"No submissionId found on response" forKey:@"error"];
            resolve(result);
        }
    } failure:^(NSArray * _Nonnull errors) {
        // You can check the error codes at: https://developer.bazaarvoice.com/conversations-api/reference/v5.4-prr/reviews/review-submission
        NSError *error = errors[0];
        NSMutableDictionary *result = [NSMutableDictionary new];
        [result setObject:[NSNumber numberWithBool:NO] forKey:@"success"];
        [result setObject:error.localizedDescription forKey:@"error"];
        resolve(result);
    }];
}

-(NSArray *)parseReviews:(NSArray*)results {
    NSMutableArray *reviews = [NSMutableArray new];
    for (BVReview *review in results) {
        [reviews addObject:[self jsonFromReview:review]];
    }
    return reviews;
}

-(NSMutableArray *)filterReviewsByLocale:(NSArray*)reviews andLocale:(NSString*)locale {
    NSMutableArray *filteredReviews = [NSMutableArray new];
    for (NSDictionary *review in reviews) {
        if ([[review objectForKey:@"locale"] isEqualToString:locale]) {
            [filteredReviews addObject:review];
        }
    }
    return filteredReviews;
}


- (NSMutableDictionary *)jsonFromReview:(BVReview *)review {
    NSMutableDictionary *dictionary = [NSMutableDictionary new];
    [dictionary setValue:review.authorId forKey:@"userUuid"];
    [dictionary setValue:review.identifier forKey:@"reviewId"];
    [dictionary setValue:review.submissionId forKey:@"submissionId"];
    [dictionary setValue:review.productId forKey:@"productId"];
    [dictionary setObject:review.userNickname forKey:@"nickname"];
    [dictionary setObject:review.contentLocale forKey:@"locale"];
    if (review.title) {
        [dictionary setObject:review.title forKey:@"title"];
    }
    if (review.reviewText) {
        [dictionary setObject:review.reviewText forKey:@"reviewText"];
    }
    
    NSDateFormatter* dateFormatter = [NSDateFormatter new];
    dateFormatter.dateFormat = @"yyyy-MM-dd'T'HH:mm:ssxxx";
    [dateFormatter setCalendar:[NSCalendar calendarWithIdentifier:NSCalendarIdentifierGregorian]];
    dateFormatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    NSString *date = [dateFormatter stringFromDate:review.submissionTime];
    
    [dictionary setObject:date forKey:@"date"];
    [dictionary setObject:[NSNumber numberWithInteger:review.rating] forKey:@"rating"];
    [dictionary setObject:@[review.additionalFields] forKey:@"additionalFields"];
    
    NSDictionary *additionalField = review.additionalFields;
    if ([additionalField objectForKey:@"Avatar"]) {
        NSDictionary *avatar = [additionalField objectForKey:@"Avatar"];
        NSString *profilePicture = [avatar objectForKey:@"Value"];
        [dictionary setObject:profilePicture forKey:@"avatar"];
    }
    
    return dictionary;
}

@end
