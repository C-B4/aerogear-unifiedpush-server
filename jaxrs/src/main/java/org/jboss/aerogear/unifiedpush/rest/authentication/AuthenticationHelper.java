package org.jboss.aerogear.unifiedpush.rest.authentication;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.jboss.aerogear.unifiedpush.api.Installation;
import org.jboss.aerogear.unifiedpush.api.PushApplication;
import org.jboss.aerogear.unifiedpush.api.Variant;
import org.jboss.aerogear.unifiedpush.rest.util.BearerHelper;
import org.jboss.aerogear.unifiedpush.rest.util.ClientAuthHelper;
import org.jboss.aerogear.unifiedpush.rest.util.HttpBasicHelper;
import org.jboss.aerogear.unifiedpush.rest.util.PushAppAuthHelper;
import org.jboss.aerogear.unifiedpush.service.AliasService;
import org.jboss.aerogear.unifiedpush.service.ClientInstallationService;
import org.jboss.aerogear.unifiedpush.service.GenericVariantService;
import org.jboss.aerogear.unifiedpush.service.PushApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AuthenticationHelper {
	private static final Logger logger = LoggerFactory.getLogger(AuthenticationHelper.class);

	@Inject
	private ClientInstallationService clientInstallationService;
	@Inject
	private GenericVariantService genericVariantService;
	@Inject
	private PushApplicationService pushApplicationService;
	@Inject
	private AliasService aliasService;

	public PushApplication loadApplicationWhenAuthorized(HttpServletRequest request) {
		return loadApplicationWhenAuthorized(request, null, false);
	}

	public PushApplication loadApplicationWhenAuthorized(HttpServletRequest request, String aliasValue){
		return loadApplicationWhenAuthorized(request, aliasValue, true);
	}

	private PushApplication loadApplicationWhenAuthorized(HttpServletRequest request, String aliasValue, boolean forceAliasScope) {

		// Extract device token
		String deviceToken = ClientAuthHelper.getDeviceToken(request);

		// Device based authentication
		if (StringUtils.isNotEmpty(deviceToken)) {
			final Variant variant = loadVariantWhenAuthorized(deviceToken, request);

			if (variant == null) {
				return null;
			}

			// Find application by variant
			return pushApplicationService.findByVariantID(variant.getVariantID());
		}

		// Application based authentication
		PushApplication pushApplication = PushAppAuthHelper.loadPushApplicationWhenAuthorized(request,
				pushApplicationService);

		if (pushApplication == null) {
			logger.warn("UnAuthorized application authentication attempt, credentials ({}) are not authorized",
					HttpBasicHelper.getAuthorizationHeader(request));
			return null;
		}

		if (forceAliasScope && aliasService.find(pushApplication.getPushApplicationID(), aliasValue) == null) {
			logger.warn(
					"UnAuthorized application authentication, application ({}) is not authorized to modify alias ({}) data",
					pushApplication.getName(), aliasValue);
			return null;
		}

		return pushApplication;
	}

	/**
	 * Returns the variant if either device token is present and device enabled
	 * or applicationId exists and
	 *
	 */
	private Variant loadVariantWhenAuthorized(String deviceToken, HttpServletRequest request) {

		if (deviceToken == null) {
			logger.warn("API request missing device-token header ({}), URI - > {}", deviceToken,
					request.getRequestURI());
			return null;
		}

		// Get variant from basic authentication headers
		Variant variant = ClientAuthHelper.loadVariantWhenAuthorized(genericVariantService, request, false);

		if (variant == null) {
			// Variant is missing, try to extract variant using Bearer
			if (isBearerAllowed(request)) {
				variant = loadVariantFromBearerWhenAuthorized(genericVariantService, request);

				if (variant == null) {
					logger.info("UnAuthorized bearer authentication missing variant. device token ({}), URI {}",
							deviceToken, request.getRequestURI());
					return null;
				}
				logger.debug("Authorized bearer authentication to exising variant id: {} API: {}",
						variant.getVariantID(), request.getRequestURI());
			} else {
				// Variant is missing to anonymous/otp mode
				logger.warn("UnAuthorized basic authentication using token-id {}", request.getRequestURI());
				return null;
			}
		}

		// Variant can't be null at this point.
		Installation installation = clientInstallationService.findInstallationForVariantByDeviceToken(
				variant.getVariantID(), ClientAuthHelper.getDeviceToken(request));

		// Installation should always be present and enabled.
		if (installation == null || installation.isEnabled() == false) {
			logger.info("API request to non-existing / disabled installation variant id: {} API: {}",
					variant.getVariantID(), request.getRequestURI());
			return null;
		}

		return variant;
	}

	/**
	 * returns variant from the bearer token if it is valid for the request
	 */
	private static Variant loadVariantFromBearerWhenAuthorized(GenericVariantService genericVariantService,
			HttpServletRequest request) {
		// extract the pushApplicationID from the Authorization header:
		final Variant variant = BearerHelper.extractVariantFromBearerHeader(genericVariantService, request);

		if (variant != null) {
			return variant;
		}

		// unauthorized...
		return null;
	}

	// Barear authentication allowed only using /upsi context
	private boolean isBearerAllowed(HttpServletRequest request) {
		return ClientAuthHelper.isWebAppContext(request);
	}
}