package org.jboss.aerogear.unifiedpush.rest.documents;

import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang3.StringUtils;
import org.jboss.aerogear.unifiedpush.api.Alias;
import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.api.Variant;
import org.jboss.aerogear.unifiedpush.api.document.DocumentMetadata;
import org.jboss.aerogear.unifiedpush.cassandra.dao.NullAlias;
import org.jboss.aerogear.unifiedpush.cassandra.dao.model.DocumentContent;
import org.jboss.aerogear.unifiedpush.rest.AbstractEndpoint;
import org.jboss.aerogear.unifiedpush.rest.util.ClientAuthHelper;
import org.jboss.aerogear.unifiedpush.service.AliasService;
import org.jboss.aerogear.unifiedpush.service.ClientInstallationService;
import org.jboss.aerogear.unifiedpush.service.DocumentService;
import org.jboss.aerogear.unifiedpush.service.GenericVariantService;
import org.jboss.aerogear.unifiedpush.service.PushApplicationService;
import org.jboss.resteasy.plugins.providers.multipart.MultipartOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qmino.miredot.annotations.ReturnType;

@Path("/database")
public class DatabaseEndpoint extends AbstractEndpoint {
	private final Logger logger = LoggerFactory.getLogger(DatabaseEndpoint.class);

	public static final String X_HEADER_SNAPSHOT_ID = "X-AB-Snapshot-Id";
	public static final String X_HEADER_COUNT = "X-AB-Count";
	private static final String X_HEADER_DATE = "Date";

	@Inject
	private ClientInstallationService clientInstallationService;
	@Inject
	private GenericVariantService genericVariantService;
	@Inject
	private DocumentService documentService;
	@Inject
	private PushApplicationService pushApplicationService;
	@Inject
	private AliasService aliasService;

	/**
	 * Cross Origin for global scope database.
	 *
	 * @param headers
	 *            "Origin" header
	 * @return "Access-Control-Allow-Origin" header for your response
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Methods POST, PUT, DELETE, HEAD
	 * @responseheader Access-Control-Allow-Headers accept, origin,
	 *                 content-type, authorization
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader Access-Control-Max-Age 604800
	 *
	 * @statuscode 200 Successful response for your request
	 */
	@OPTIONS
	@Path("/{database}")
	@ReturnType("java.lang.Void")
	public Response crossOriginForGlobal(@Context HttpHeaders headers) {
		return appendPreflightResponseHeaders(headers, Response.ok()).build();
	}

	/**
	 * Cross Origin for global scope database.
	 *
	 * @param headers
	 *            "Origin" header
	 * @return "Access-Control-Allow-Origin" header for your response
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Methods POST, DELETE
	 * @responseheader Access-Control-Allow-Headers accept, origin,
	 *                 content-type, authorization
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader Access-Control-Max-Age 604800
	 *
	 * @statuscode 200 Successful response for your request
	 */
	@OPTIONS
	@Path("/{database}/{snapshot}")
	@ReturnType("java.lang.Void")
	public Response crossOriginForGlobalPut(@Context HttpHeaders headers) {
		return appendPreflightResponseHeaders(headers, Response.ok()).build();
	}

	/**
	 * Cross Origin for global scope database.
	 *
	 * @param headers
	 *            "Origin" header
	 * @return "Access-Control-Allow-Origin" header for your response
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Methods POST, DELETE
	 * @responseheader Access-Control-Allow-Headers accept, origin,
	 *                 content-type, authorization
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader Access-Control-Max-Age 604800
	 *
	 * @statuscode 200 Successful response for your request
	 */
	@OPTIONS
	@Path("/{database}/alias/{alias}")
	@ReturnType("java.lang.Void")
	public Response crossOriginForAlias(@Context HttpHeaders headers) {
		return appendPreflightResponseHeaders(headers, Response.ok()).build();
	}

	/**
	 * Cross Origin for global scope database.
	 *
	 * @param headers
	 *            "Origin" header
	 * @return "Access-Control-Allow-Origin" header for your response
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Methods POST, DELETE
	 * @responseheader Access-Control-Allow-Headers accept, origin,
	 *                 content-type, authorization
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader Access-Control-Max-Age 604800
	 *
	 * @statuscode 200 Successful response for your request
	 */
	@OPTIONS
	@Path("/{database}/alias/{alias}/{snapshot}")
	@ReturnType("java.lang.Void")
	public Response crossOriginForAliasPut(@Context HttpHeaders headers) {
		return appendPreflightResponseHeaders(headers, Response.ok()).build();
	}

	/**
	 * RESTful API for storing global scope document. The Endpoint is protected
	 * using <code>HTTP Basic</code> (credentials
	 * <code>VariantID:secret</code>).
	 *
	 * <pre>
	 * curl -u "variantID:secret"
	 *   -v -H "Accept: application/json" -H "Content-type: application/json"
	 *   -X POST
	 *   -d '{
	 *     "any-attribute-1" : "example1",
	 *     "any-attribute-2" : "example1",
	 *     "any-attribute-3" : "example1"
	 *   }'
	 *   https://SERVER:PORT/context/rest/database/users
	 * </pre>
	 *
	 * @param document
	 *            JSON content
	 * @param database
	 *            Logical database name, e.g users | metadata | any other.
	 * @param id
	 *            Document collection id.
	 *
	 * @return Document body as json.
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="AeroBase Server" (only for
	 *                 401 response)
	 *
	 * @statuscode 200 Successful store of the document.
	 * @statuscode 400 The format of the document request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@ReturnType("java.lang.Void")
	@Path("/{database}")
	public Response save(String document, //
			@PathParam("database") String database, //
			@QueryParam("id") String id, //
			@Context HttpServletRequest request) { //

		return save(document, database, null, id, request);
	}

	/**
	 * RESTful API for updating global scope document. The Endpoint is protected
	 * using <code>HTTP Basic</code> (credentials
	 * <code>VariantID:secret</code>).
	 *
	 * <pre>
	 * curl -u "variantID:secret"
	 *   -v -H "Accept: application/json" -H "Content-type: application/json"
	 *   -X PUT
	 *   -d '{
	 *     "any-attribute-1" : "example1",
	 *     "any-attribute-2" : "example1",
	 *     "any-attribute-3" : "example1"
	 *   }'
	 *   https://SERVER:PORT/context/rest/database/users/snapshot
	 * </pre>
	 *
	 * @param document
	 *            JSON content
	 * @param database
	 *            Logical database name, e.g users | metadata | any other.
	 * @param snapshot
	 *            Unique snapshot identifier (TimeBased UUID).
	 * @param id
	 *            Document collection identifier.
	 *
	 * @return Document body as json.
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="AeroBase Server" (only for
	 *                 401 response)
	 *
	 * @statuscode 200 Successful update of the document.
	 * @statuscode 400 The format of the document request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{database}/{snapshot}")
	public Response save(String document, //
			@PathParam("database") String database, //
			@PathParam("snapshot") String snapshot, //
			@QueryParam("id") String id, //
			@Context HttpServletRequest request) { //
		// Authentication
		final Variant variant = ClientAuthHelper.loadVariantWhenInstalled(genericVariantService,
				clientInstallationService, request);

		if (variant == null) {
			return create401Response(request);
		}

		// Find push application b variant
		PushApplication pushApplication = pushApplicationService.findByVariantID(variant.getVariantID());
		UUID pushApplicationId = UUID.fromString(pushApplication.getPushApplicationID());

		// Create metadata object
		DocumentMetadata metadata = new DocumentMetadata(pushApplicationId, database,
				NullAlias.getAlias(pushApplicationId), id,
				StringUtils.isEmpty(snapshot) ? null : UUID.fromString(snapshot));

		DocumentContent doc = documentService.save(metadata, document);

		try {
			return appendAllowOriginHeader(appendSnapshotHeader(Response.ok(), doc.getKey().getSnapshot()), request);
		} catch (Exception e) {
			logger.error(String.format("Cannot store document for database %s", database), e);
			return appendAllowOriginHeader(Response.status(Status.INTERNAL_SERVER_ERROR), request);
		}
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	@ReturnType("java.lang.Void")
	@Path("/{database}/alias/{alias}")
	public Response saveForAlias(String document, //
			@PathParam("database") String database, //
			@PathParam("alias") String alias, //
			@QueryParam("id") String id, //
			@Context HttpServletRequest request) { //
		return saveForAlias(document, database, alias, null, id, request);
	}

	/**
	 * RESTful API for update alias scope document. The Endpoint is protected
	 * using <code>HTTP Basic</code> (credentials
	 * <code>VariantID:secret</code>).
	 *
	 * <pre>
	 * curl -u "variantID:secret"
	 *   -v -H "Accept: application/json" -H "Content-type: application/json"
	 *   -X POST
	 *   -d '{
	 *     "any-attribute-1" : "example1",
	 *     "any-attribute-2" : "example1",
	 *     "any-attribute-3" : "example1"
	 *   }'
	 *   https://SERVER:PORT/context/rest/database/users/alias/support@aerobase.org/snapshot
	 * </pre>
	 *
	 * @param document
	 *            JSON content.
	 * @param database
	 *            Logical database name, e.g users | metadata | any other.
	 * @param alias
	 *            Unique alias name (email/phone/tokenid/other).
	 * @param snapshot
	 *            Unique snapshot identifier (TimeBased UUID).
	 * @param id
	 *            Document collection id.
	 *
	 * @return Document body as json.
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="AeroBase Server" (only for
	 *                 401 response)
	 *
	 * @statuscode 200 Successful update of the document.
	 * @statuscode 400 The format of the document request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	@ReturnType("java.lang.Void")
	@Path("/{database}/alias/{alias}/{snapshot}")
	public Response saveForAlias(String document, //
			@PathParam("database") String database, //
			@PathParam("alias") String alias, //
			@PathParam("snapshot") String snapshot, //
			@QueryParam("id") String id, //
			@Context HttpServletRequest request) { //

		// Authentication
		String deviceToken = ClientAuthHelper.getDeviceToken(request);
		final Variant variant = ClientAuthHelper.loadVariantWhenInstalled(genericVariantService,
				clientInstallationService, deviceToken, request);

		if (variant == null) {
			return create401Response(request);
		}

		// Find application by variant
		PushApplication pushApplication = pushApplicationService.findByVariantID(variant.getVariantID());
		UUID pushApplicationId = UUID.fromString(pushApplication.getPushApplicationID());

		// Find related alias
		Alias aliasObj = aliasService.find(pushApplicationId.toString(), alias);

		if (aliasObj == null) {
			logger.debug("Alias {} is missing, storing by token-id", alias);
			aliasObj = getAliasByToken(pushApplicationId, deviceToken);
		}

		DocumentMetadata metadata = new DocumentMetadata(pushApplicationId, database, aliasObj, id,
				StringUtils.isEmpty(snapshot) ? null : UUID.fromString(snapshot));
		DocumentContent doc = documentService.save(metadata, document);

		try {
			return appendAllowOriginHeader(appendSnapshotHeader(Response.ok(), doc.getKey().getSnapshot()), request);
		} catch (Exception e) {
			logger.error(String.format("Cannot store document for database %s", database), e);
			return appendAllowOriginHeader(Response.status(Status.INTERNAL_SERVER_ERROR), request);
		}
	}

	/**
	 * RESTful API for query alias scope document. The Endpoint is protected
	 * using <code>HTTP Basic</code> (credentials
	 * <code>VariantID:secret</code>).
	 *
	 * <pre>
	 * curl -u "variantID:secret"
	 *   -v -H "Accept: application/json" -H "Content-type: application/json"
	 *   -X GET
	 *   https://SERVER:PORT/context/rest/database/users/alias/support@aerobase.org/
	 * </pre>
	 *
	 * @param document
	 *            JSON content.
	 * @param database
	 *            Logical database name, e.g users | metadata | any other.
	 * @param alias
	 *            Unique alias name (email/phone/tokenid/other).
	 * @param id
	 *            Document collection id.
	 *
	 * @return Document content as multipart/mixed.
	 *
	 * @responseheader Access-Control-Allow-Origin With host in your "Origin"
	 *                 header
	 * @responseheader Access-Control-Allow-Credentials true
	 * @responseheader WWW-Authenticate Basic realm="AeroBase Server" (only for
	 *                 401 response)
	 *
	 * @statuscode 200 Successful store of the document.
	 * @statuscode 400 The format of the document request was incorrect (e.g.
	 *             missing required values).
	 * @statuscode 401 The request requires authentication.
	 */
	@GET
	@Produces("multipart/mixed")
	@Path("/{database}/alias/{alias}")
	public Response getForAlias(String document, //
			@PathParam("database") String database, //
			@PathParam("alias") String alias, //
			@QueryParam("id") String id, //
			@Context HttpServletRequest request) { //

		// Authentication
		String deviceToken = ClientAuthHelper.getDeviceToken(request);
		final Variant variant = ClientAuthHelper.loadVariantWhenInstalled(genericVariantService,
				clientInstallationService, deviceToken, request);

		if (variant == null) {
			return create401Response(request);
		}

		// Find application by variant
		PushApplication pushApplication = pushApplicationService.findByVariantID(variant.getVariantID());
		UUID pushApplicationId = UUID.fromString(pushApplication.getPushApplicationID());

		// Find related alias
		Alias aliasObj = aliasService.find(pushApplicationId.toString(), alias);

		if (aliasObj == null) {
			logger.debug("Unable to get documents for unknown alias {}", alias);
			if (aliasObj == null) {
				logger.debug("Alias {} is missing, quering by token-id", alias);
				aliasObj = getAliasByToken(pushApplicationId, deviceToken);
			}
		}

		final MultipartOutput output = new MultipartOutput();

		DocumentMetadata metadata = new DocumentMetadata(pushApplicationId, database, aliasObj, id);
		documentService.find(metadata, null).forEach(doc -> {
			output.addPart(doc.getContent(), MediaType.valueOf(doc.getContentType()),
					doc.getKey().getSnapshot().toString());
		});

		try {
			return appendAllowOriginHeader(appendCountHeader(Response.ok(output), output.getParts().size()), request);
		} catch (Exception e) {
			logger.error(String.format("Cannot store document for database %s", database), e);
			return appendAllowOriginHeader(Response.status(Status.INTERNAL_SERVER_ERROR), request);
		}
	}

	private Alias getAliasByToken(UUID pushApplicationId, String deviceToken) {
		// Find alias by device token
		Alias aliasObj = aliasService.find(pushApplicationId.toString(), deviceToken);

		if (aliasObj == null) {
			// Create alias by token id
			logger.debug("Alias {} is missing, creating new alias by token-id", deviceToken);
			aliasObj = new Alias(pushApplicationId, null, null, deviceToken);

			// Since device is anonymous we don't want to create KC user.
			aliasService.create(aliasObj, false);
		}

		return aliasObj;
	}

	private ResponseBuilder appendCountHeader(ResponseBuilder rb, int count) {
		rb.header(X_HEADER_COUNT, count);

		return appendAllowExposeHeader(rb);
	}

	private ResponseBuilder appendSnapshotHeader(ResponseBuilder rb, UUID snapshot) {
		rb.header(X_HEADER_SNAPSHOT_ID, snapshot.toString());

		return appendAllowExposeHeader(rb);
	}

	private ResponseBuilder appendAllowExposeHeader(ResponseBuilder rb) {
		rb.header("Access-Control-Expose-Headers",
				StringUtils.join(new String[] { X_HEADER_SNAPSHOT_ID, X_HEADER_COUNT, X_HEADER_DATE }, ","));
		return rb;
	}
}