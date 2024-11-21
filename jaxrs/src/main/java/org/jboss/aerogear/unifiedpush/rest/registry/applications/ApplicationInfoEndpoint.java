package org.jboss.aerogear.unifiedpush.rest.registry.applications;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.rest.util.PushAppAuthHelper;
import org.jboss.aerogear.unifiedpush.service.PushApplicationService;
import org.springframework.stereotype.Component;

import com.qmino.miredot.annotations.ReturnType;

@Component
@Path("/application")
public class ApplicationInfoEndpoint {

    @Inject
    private PushApplicationService pushApplicationService;

    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    @ReturnType("org.jboss.aerogear.unifiedpush.api.PushApplication")
    public Response findById(@Context HttpServletRequest request) {
        PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request, pushApplicationService);
        if (pushApplication == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .header("WWW-Authenticate", "Basic realm=\"AeroGear UnifiedPush Server\"")
                .entity("Unauthorized Request")
                .build();
        }

        return Response.ok(pushApplication).build();
    }
}
