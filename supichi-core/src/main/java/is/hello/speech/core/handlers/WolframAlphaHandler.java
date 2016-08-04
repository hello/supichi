package is.hello.speech.core.handlers;

import com.google.common.collect.Maps;
import com.wolfram.alpha.WAEngine;
import com.wolfram.alpha.WAException;
import com.wolfram.alpha.WAPlainText;
import com.wolfram.alpha.WAPod;
import com.wolfram.alpha.WAQuery;
import com.wolfram.alpha.WAQueryResult;
import is.hello.speech.core.db.SpeechCommandDAO;
import is.hello.speech.core.models.HandlerResult;
import is.hello.speech.core.models.HandlerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Created by ksg on 8/3/16
 */
public class WolframAlphaHandler extends BaseHandler {
    private final static Logger LOGGER = LoggerFactory.getLogger(WolframAlphaHandler.class);

    private final WAEngine engine;

    private final SpeechCommandDAO speechCommandDAO;

    private WolframAlphaHandler(final WAEngine engine, final SpeechCommandDAO speechCommandDAO) {
        super("wolfram", speechCommandDAO, Maps.newHashMap());
        this.engine = engine;
        this.speechCommandDAO = speechCommandDAO;
    }

    public static WolframAlphaHandler create(final SpeechCommandDAO speechCommandDAO, final String appId, final String format) {
        final WAEngine engine = new WAEngine();
        engine.setAppID(appId);
        engine.addFormat(format);
        return new WolframAlphaHandler(engine, speechCommandDAO);
    }

    @Override
    public HandlerResult executeCommand(String text, String senseId, Long accountId) {
        final Map<String, String> response = Maps.newHashMap();

        final WAQuery query = engine.createQuery();
        query.setInput(text);

        try {
            LOGGER.debug("action=query-wolfram");
            final WAQueryResult queryResult = engine.performQuery(query);

            if (queryResult.isSuccess()) {
                for (WAPod pod : queryResult.getPods()) {
                    if (!pod.getID().equalsIgnoreCase("result") || pod.isError()) {
                        continue;
                    }

                    final Object element = pod.getSubpods()[0].getContents()[0];

                    if (element instanceof WAPlainText) {
                        final String resultString = ((WAPlainText) element).getText();
                        LOGGER.debug("action=wolfram-api-success text={} result_text={}", text, resultString);

                        response.put("result", HandlerResult.Outcome.OK.getValue());
                        response.put("answer", "wolfram");
                        response.put("text", resultString);
                        return new HandlerResult(HandlerType.WOLFRAM_ALPHA, response);
                    }
                }
            }
        } catch (WAException e) {
            LOGGER.error("action=wolfram-api-fails error_msg={}", e.getMessage());
        }

        response.put("result", HandlerResult.Outcome.FAIL.getValue());
        response.put("error", "wolfram fail");
        response.put("text", String.format("Wolfram wasn't able to answer your question: %s", text));
        LOGGER.debug("action=wolfram-default-response msg=no-answer-found text={}", text);

        return new HandlerResult(HandlerType.WOLFRAM_ALPHA, response);
    }
}
