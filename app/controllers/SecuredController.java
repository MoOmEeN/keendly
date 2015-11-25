package controllers;

import adaptors.Adaptor;
import adaptors.model.ExternalSubscription;
import adaptors.model.Tokens;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.model.FeedSubscription;
import controllers.request.DeliveryRequest;
import controllers.request.Feed;
import controllers.request.ScheduleRequest;
import dao.SubscriptionDao;
import entities.Subscription;
import entities.SubscriptionFrequency;
import entities.SubscriptionItem;
import play.Logger;
import play.db.jpa.JPA;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import sun.util.calendar.ZoneInfo;
import utils.SessionUtils;
import views.html.home;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.zone.ZoneRulesProvider;
import java.util.*;
import java.util.stream.Collectors;


@With(SecuredAction.class)
public class SecuredController extends Controller {

    private static Map<String, Integer> zones = new TreeMap<>();
    static {
        for (String zone : ZoneRulesProvider.getAvailableZoneIds()){
            zones.put(zone, ZoneInfo.getTimeZone(zone).getOffset(System.currentTimeMillis())/1000/60/60);
        }
    }

    private SubscriptionDao subscriptionDao = new SubscriptionDao();

    public Promise<Result> home(){
        Tokens tokens = SessionUtils.findTokens(session());
        Adaptor adaptor = SessionUtils.findAdaptor(session());
        return adaptor.getSubscriptions(tokens).map(subscriptions ->
            JPA.withTransaction(() -> {
                List<SubscriptionItem> items = subscriptionDao.getSubscriptionItems(SessionUtils.getUser(session()));
                List<FeedSubscription> feedSubscriptions = new ArrayList<>();

                for(ExternalSubscription externalSubscription : subscriptions){
                    List<SubscriptionItem> feedItems = items.stream()
                            .filter(s -> s.feedId.equals(externalSubscription.getFeedId()))
                            .collect(Collectors.toList());
                    FeedSubscription feedSubscription = map(externalSubscription, feedItems);
                    feedSubscriptions.add(feedSubscription);
                }
                return ok(home.render(feedSubscriptions, zones, dateTimeFormatter(), session()));
            })
        );
    }

    private FeedSubscription map(ExternalSubscription externalSubscription, List<SubscriptionItem> feedItems){
        FeedSubscription feedSubscription = new FeedSubscription();
        feedSubscription.providerId = externalSubscription.getFeedId();
        feedSubscription.title = externalSubscription.getTitle();
        Map<LocalTime, String> scheduled = new HashMap<>();
        for (SubscriptionItem item : feedItems){
            scheduled.put(LocalTime.parse(item.subscription.time), item.subscription.timeZone);
            Logger.info(LocalTime.parse(item.subscription.time).format(dateTimeFormatter()));
        }
        feedSubscription.scheduled = scheduled;

        return feedSubscription;
    }

    private DateTimeFormatter dateTimeFormatter(){
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(lang().toLocale());
    }

    public Promise<Result> deliver() {
        JsonNode json = request().body().asJson();
        System.out.println(json);
        DeliveryRequest request = Json.fromJson(json, DeliveryRequest.class);

        // get articles from adaptor, call lautus and send to S3
        Tokens tokens = SessionUtils.findTokens(session());
        Adaptor adaptor = SessionUtils.findAdaptor(session());

        return Promise.pure(ok());
    }

    public Promise<Result> schedule() {
        JsonNode json = request().body().asJson();
        System.out.println(json);
        ScheduleRequest request = Json.fromJson(json, ScheduleRequest.class);
        TimeZone timezone = ZoneInfo.getTimeZone(request.timezone);
        LocalTime time = LocalTime.parse(request.time, DateTimeFormatter.ofPattern("h:m a"));
        Subscription subscription = new Subscription();
        subscription.active = Boolean.TRUE;
        subscription.frequency = SubscriptionFrequency.DAILY;
        subscription.time = time.toString();
        subscription.timeZone = timezone.toZoneId().getId();
        subscription.items = new ArrayList<>();
        subscription.user = SessionUtils.getUser(session());

        for (Feed feed : request.feeds){
            SubscriptionItem item = new SubscriptionItem();
            item.feedId = feed.id;
            item.fullArticle = feed.fullArticle;
            item.markAsRead = feed.markAsRead;
            item.withImages = feed.includeImages;
            item.subscription = subscription;
            subscription.items.add(item);
        }

        JPA.withTransaction(() -> subscriptionDao.createSubscription(subscription));
        return Promise.pure(ok());
    }

//    public Promise<Result> deliver(){
//        JsonNode json = request().body().asJson();
//        DeliveryRequest request = Json.fromJson(json, DeliveryRequest.class);
//        Tokens tokens = SessionUtils.findTokens(session());
//        Adaptor adaptor = SessionUtils.findAdaptor(session());
//        return adaptor.getUnread(extractIds(request.feeds), tokens)
//                .flatMap(unread ->
//                                fetchArticles(unread, request).map(ret -> {
//                                    Book.BookBuilder bookBuilder = initBookBuilder(request);
//                                    List<Section> sections = new ArrayList<>();
//                                    for (Feed feed : request.feeds) {
//                                        Section.SectionBuilder s = Section.builder();
//                                        s.title(feed.title);
//                                        List<Article> articles = new ArrayList<>();
//                                        if (unread.containsKey(feed.id)){
//                                            for (Entry entry : unread.get(feed.id)) {
//                                                articles.add(convertToArticle(entry));
//                                            }
//                                            s.articles(articles);
//                                            sections.add(s.build());
//                                        }
//                                    }
//                                    bookBuilder.sections(sections);
//                                    String filePath = new Generator(ConfigUtils.parameter("temp.directory"),
//                                            ConfigUtils.parameter("kindlegen.path")).generate(bookBuilder.build());
//
//                                    try {
//                                        new EmailSender().sendFile(filePath, "moomeen@gmail.com");
//                                    } catch (Exception e){
//                                        return internalServerError(Json.toJson("error"));
//                                    }
//
//                                    adaptor.markAsRead(extractIdsToMarkAsRead(request.feeds), tokens).map(res -> {
//                                      return "a";
//                                    });
//                                    return ok(Json.toJson("ok"));
//                                })
//                );
//    }
//
//    private Promise fetchArticles(Map<String, List<Entry>> entries, DeliveryRequest request){
//        List<Promise<Entry>> promises = new ArrayList<>();
//        for (Map.Entry<String, List<Entry>> entry : entries.entrySet()){
//            if (getFeedRequest(request, entry.getKey()).fullArticle) {
//                for (Entry entry1 : entry.getValue()) {
//                    Promise<Entry> promise = fetchEntryContent(entry1);
//                    promises.add(promise);
//                }
//            }
//        }
//        return Promise.sequence(promises);
//    }
//
//    private Promise<Entry> fetchEntryContent(Entry entry){
//        return WS.url(entry.getUrl())
//                .get().map(res -> {
//                    if (res.getStatus() == HttpStatus.SC_OK){
//                        ArticleTextExtractor extractor = new ArticleTextExtractor();
//                        JResult result = extractor.extractContent(res.getBody());
//                        entry.setContent(result.getText());
//                    }
//                    return entry;
//                });
//    }
//
//    private Feed getFeedRequest(DeliveryRequest deliveryRequest, String feedId){
//        for (Feed feedDeliveryRequest : deliveryRequest.feeds){
//            if (feedDeliveryRequest.id.equals(feedId)){
//                return feedDeliveryRequest;
//            }
//        }
//        return null;
//    }
//
//    private List<String> extractIds(List<Feed> feeds){
//        return feeds.stream().map(feed -> feed.id).collect(Collectors.toList());
//    }
//
//    private List<String> extractIdsToMarkAsRead(List<Feed> feeds){
//        return feeds.stream().filter(feed -> feed.markAsRead).map(feed -> feed.id).collect(Collectors.toList());
//    }
//
//    private Article convertToArticle(Entry entry){
//        Article.ArticleBuilder b = Article.builder();
//        b.author(entry.getAuthor());
//        b.title(entry.getTitle());
//        b.date(entry.getPublished());
//        b.content(entry.getContent());
//        return b.build();
//    }
//
//    private Book.BookBuilder initBookBuilder(DeliveryRequest request){
//        Book.BookBuilder b = Book.builder();
//        if (request.feeds.size() == 1){
//            b.title(request.feeds.get(0).title);
//        } else {
//            b.title("Articles"); // TODO translated
//        }
//        b.language("en-gb").creator("keendly").subject("news").date(today());
//        return b;
//    }
//
//    private String today(){
//        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
//    }
}
