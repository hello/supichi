package is.hello.speech.resources.v1;

import com.google.api.client.util.Lists;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 * Created by ksg on 7/11/16
 */
@Path("/parse")
@Produces(MediaType.APPLICATION_JSON)
public class ParseResource {
    private final static Logger LOGGER = LoggerFactory.getLogger(ParseResource.class);

    final Parser parser = new Parser();

    @Path("/datetime")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public List<String> parseDateTime(final String text) {
        final List<DateGroup> groups = parser.parse(text);
        final List<String> results = Lists.newArrayList();
        for (final DateGroup group : groups) {
            final String match = group.getText();
            results.add(match);
        }
        return results;
    }

}
