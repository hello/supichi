package is.hello.speech.resources.demo;

import com.google.common.collect.ImmutableList;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import is.hello.speech.core.api.Response;
import is.hello.speech.core.handlers.executors.HandlerExecutor;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import is.hello.speech.core.models.TextQuery;
import is.hello.speech.core.models.UploadResponseParam;
import is.hello.speech.utils.S3ResponseBuilder;
import is.hello.speech.utils.WatsonResponseBuilder;



@Path("/upload")
@Produces(MediaType.APPLICATION_JSON)
public class DemoResource {

    private final static Logger LOGGER = LoggerFactory.getLogger(DemoResource.class);

    private final HandlerExecutor handlerExecutor;

    private final DeviceDAO deviceDAO;

    private final S3ResponseBuilder s3ResponseBuilder;
    private final WatsonResponseBuilder watsonResponseBuilder;

    @Context
    HttpServletRequest request;

    public DemoResource(final HandlerExecutor handlerExecutor,
                          final DeviceDAO deviceDAO,
                          final S3ResponseBuilder s3ResponseBuilder,
                          final WatsonResponseBuilder watsonResponseBuilder) {
        this.handlerExecutor = handlerExecutor;
        this.deviceDAO = deviceDAO;
        this.s3ResponseBuilder = s3ResponseBuilder;
        this.watsonResponseBuilder = watsonResponseBuilder;
    }

    @Path("/text")
    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public byte[] text(@Valid final TextQuery query,
                       @DefaultValue("adpcm") @QueryParam("response") final UploadResponseParam responseParam
    ) throws InterruptedException, IOException {

        final ImmutableList<DeviceAccountPair> accounts = deviceDAO.getAccountIdsForDeviceId(query.senseId);

        LOGGER.debug("info=sense-id id={}", query.senseId);
        if (accounts.isEmpty()) {
            LOGGER.error("error=no-paired-sense-found sense_id={}", query.senseId);
            return s3ResponseBuilder.response(Response.SpeechResponse.Result.REJECTED, false, HandlerResult.emptyResult(), responseParam);
        }

        // TODO: for now, pick the smallest account-id as the primary id
        Long accountId = accounts.get(0).accountId;
        for (final DeviceAccountPair accountPair : accounts) {
            if (accountPair.accountId < accountId) {
                accountId = accountPair.accountId;
            }
        }

        LOGGER.debug("action=execute-handler sense_id={} account_id={}", query.senseId, accountId);
        try {

            final HandlerResult executeResult = handlerExecutor.handle(query.senseId, accountId, query.transcript);

            if (executeResult.handlerType.equals(HandlerType.WEATHER)) {
                return watsonResponseBuilder.response(Response.SpeechResponse.Result.OK, false, executeResult, responseParam);
            }

            // TODO: response-builder
            if (!executeResult.handlerType.equals(HandlerType.NONE)) {
                return s3ResponseBuilder.response(Response.SpeechResponse.Result.OK, false, executeResult, responseParam);
            }
            return s3ResponseBuilder.response(Response.SpeechResponse.Result.TRY_AGAIN, false, executeResult, responseParam);
        } catch (Exception e) {
            LOGGER.error("action=streaming error={}", e.getMessage());
        }

        return s3ResponseBuilder.response(Response.SpeechResponse.Result.REJECTED, false, HandlerResult.emptyResult(), responseParam);
    }

}
