package controllers.pagemanagement;

import akka.dispatch.Futures;
import akka.pattern.Patterns;
import com.sun.tools.internal.ws.resources.WscompileMessages;
import controllers.BaseController;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpHeaders;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.ExecutionContext;
import org.sunbird.common.request.Request;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.util.Util;
import play.api.http.Writeable;
import play.api.libs.ws.WSClient;
import play.api.libs.ws.WSResponse;
import play.api.mvc.Codec;
import play.libs.F;
import play.libs.Json;
import play.mvc.Result;
import play.mvc.Results;
import scala.Tuple2;
import scala.collection.JavaConverters;
import scala.concurrent.Future;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class PageSearchActor extends BaseActor {

    @Override
    public void onReceive(Request request) throws Throwable {
        Util.initializeContext(request, TelemetryEnvKey.PAGE);

        ExecutionContext.setRequestId(request.getRequestId());
        if (request.getOperation().equalsIgnoreCase("getSearchData")) {
            getSearchData(request);
        } else {
            onReceiveUnsupportedOperation(request.getOperation());
        }
    }

    private void getSearchData(Request request) {
        System.out.println("Entered PageSearchActor");
        List<Map<String, Object>> sections = (List<Map<String, Object>>) request.get("sections");
        WSClient wsClient = (WSClient) request.get("wsclient");
        if (CollectionUtils.isNotEmpty(sections)) {
            List<F.Promise<Map<String, Object>>> futures = sections.stream().map(f ->
                    {
                        String query = (String) f.get("searchQuery");
                        List<Tuple2<String, String>> headers = Arrays.asList(
                                new Tuple2<String, String>(HttpHeaders.AUTHORIZATION, JsonKey.BEARER + System.getenv(JsonKey.SUNBIRD_AUTHORIZATION)),
                                new Tuple2<String, String>(HttpHeaders.CONTENT_TYPE, "application/json"),
                                new Tuple2<String, String>(HttpHeaders.CONNECTION, "Keep-Alive"));

                        long startTime = System.currentTimeMillis();
                        return F.Promise.wrap(wsClient.url("https://dev.sunbirded.org/action/composite/v3/search")//"http://28.0.3.10:9000/v3/search")
                                .withHeaders(JavaConverters.asScalaIteratorConverter(headers.iterator()).asScala().toSeq())
                                .post(query, Writeable.wString(Codec.utf_8()))).map(new F.Function<WSResponse, Map<String, Object>>() {
                            @Override
                            public Map<String, Object> apply(WSResponse wsResponse) throws Throwable {
                                System.out.println("Time taken for content-search: " + (System.currentTimeMillis() - startTime));
                                f.put("contents", Json.parse(wsResponse.body()));
                                return f;
                            }
                        });
                    }
            ).parallel().collect(Collectors.toList());


            F.Promise<Result> result = F.Promise.sequence(futures).map(new F.Function<List<Map<String, Object>>, Result>() {
                @Override
                public Result apply(List<Map<String, Object>> maps) {
                    return Results.ok(Json.toJson(maps));
                }
            });
            sender().tell(result, self());
        }

    }

}