/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.unifiedpush.service.impl;

import java.io.Serializable;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.jboss.aerogear.unifiedpush.service.PushSearchService;
import org.jboss.aerogear.unifiedpush.service.annotations.LoggedIn;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.KeycloakSecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for switch between different types of search Depending on the
 * role of the current logged in user
 */
@RequestScoped
public class SearchManager implements Serializable {
	private static final long serialVersionUID = -6665967856424444078L;
	private static final Logger logger = LoggerFactory.getLogger(SearchManager.class);

	private HttpServletRequest httpServletRequest;

	@Inject
	private PushSearchServiceImpl searchAll;
	@Inject
	private PushSearchByDeveloperServiceImpl searchByDeveloper;

	public void setHttpServletRequest(HttpServletRequest httpServletRequest) {
		this.httpServletRequest = httpServletRequest;
	}

	/**
	 * Validate the current logged in role
	 *
	 * @return an implementation of the search service
	 */
	public PushSearchService getSearchService() {

		boolean isAdmin = httpServletRequest.isUserInRole("admin");

		if (isAdmin) {
			return searchAll;
		}
		return searchByDeveloper;
	}

	/**
	 * Extract the username to be used in multiple queries
	 *
	 * @return current logged in user
	 */
	@Produces
	@LoggedIn
	public String extractUsername() {
		KeycloakPrincipal<?> p = (KeycloakPrincipal<?>) httpServletRequest.getUserPrincipal();

		// Null check for automation scenarios.
		if (p != null) {
			KeycloakSecurityContext kcSecurityContext = p.getKeycloakSecurityContext();
			return kcSecurityContext.getToken().getPreferredUsername();
		}

		logger.warn("Unable to extract KC username from http request, "
				+ "if this message exists in production we have a security breach");
		return "NULL";
	}
}
